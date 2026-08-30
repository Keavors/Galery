package com.keavors.gallery.data

import android.content.Context
import android.net.Uri
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * A photo from another app, ready to show.
 *
 * [bucketId] is null when the file is not in the library at all, which is what
 * tells the rest of the app there are no neighbours to be found later either.
 */
data class ExternalResolution(
    val items: List<MediaItem>,
    val index: Int,
    val bucketId: Long?,
    val folderName: String,
)

/** What the library screens render. */
sealed interface LibraryState {
    /** No read permission yet, so there is nothing to load. */
    data object Locked : LibraryState

    /** A load is in flight and nothing has arrived yet. */
    data object Loading : LibraryState

    data class Ready(val items: List<MediaItem>, val summary: LibrarySummary) : LibraryState
}

/**
 * The single source of truth for the library.
 *
 * The index lives in memory: every field in it comes straight from MediaStore,
 * which is already a database with its own indices, so a second copy on disk
 * would buy nothing today. Storage arrives with the first data that is ours
 * alone — user-made albums.
 */
class MediaRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val source = MediaStoreSource(context)

    private val _state = MutableStateFlow<LibraryState>(LibraryState.Locked)
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    /**
     * What is in the system trash. A separate query because every ordinary one
     * hides trashed rows, and a separate flow because it is usually a handful of
     * files against a library of thousands.
     */
    private val _trash = MutableStateFlow<List<MediaItem>>(emptyList())
    val trash: StateFlow<List<MediaItem>> = _trash.asStateFlow()

    // Taking a photo fires several notifications in a row as the file is written
    // and then its metadata filled in. Collapsing them avoids three full reloads
    // for one new picture.
    private val invalidations = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            invalidations.tryEmit(Unit)
        }
    }

    private var observing = false

    init {
        scope.launch {
            invalidations.debounce(RELOAD_DEBOUNCE_MS).collect { load() }
        }
    }

    /**
     * Starts watching MediaStore and loads the library. Safe to call repeatedly:
     * the app calls it every time it resumes, since permissions can change while
     * it is in the background.
     */
    fun refresh() {
        if (context.mediaAccess() == MediaAccess.NONE) {
            stopObserving()
            _state.value = LibraryState.Locked
            _trash.value = emptyList()
            return
        }
        startObserving()
        scope.launch { load() }
    }

    private suspend fun load() {
        if (context.mediaAccess() == MediaAccess.NONE) {
            _state.value = LibraryState.Locked
            return
        }
        if (_state.value !is LibraryState.Ready) _state.value = LibraryState.Loading
        val items = source.query()
        _state.value = LibraryState.Ready(items, items.summarize())
        _trash.value = source.query(MediaFilter.Trashed)
    }

    private fun startObserving() {
        if (observing) return
        context.contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            /* notifyForDescendants = */ true,
            observer,
        )
        observing = true
    }

    private fun stopObserving() {
        if (!observing) return
        context.contentResolver.unregisterContentObserver(observer)
        observing = false
    }

    /**
     * Finds a photo another app sent over, and its folder, without waiting for
     * the library.
     *
     * A cold start triggered by another app has nothing indexed yet, and reading
     * five thousand rows before showing one photo is the difference between a
     * picture that appears at once and one that appears in half a second. So the
     * database is asked narrowly: the file, then its folder.
     *
     * Returns a resolution that always has something to show — worst case the
     * file on its own, which is the honest answer for a picture living in some
     * other app's private storage.
     */
    suspend fun resolveExternal(uri: Uri, declaredType: String?): ExternalResolution {
        val ref = context.probeExternal(uri, declaredType)

        // Asking the database narrowly, then letting the same matching rule the
        // tests cover decide — an ambiguous name with no size stays unanswered
        // rather than paging through the wrong folder.
        val match = ref.mediaStoreId
            ?.let { source.query(MediaFilter.Id(it)).firstOrNull() }
            ?: ref.name?.takeIf { it.isNotBlank() }
                ?.let { name -> source.query(MediaFilter.Name(name)).matchExternal(ref) }

        if (match == null) {
            return ExternalResolution(
                items = listOf(ref.asStandaloneItem()),
                index = 0,
                bucketId = null,
                folderName = "",
            )
        }

        val folder = source.query(MediaFilter.Bucket(match.bucketId))
        return ExternalResolution(
            items = folder.ifEmpty { listOf(match) },
            index = folder.indexOfId(match.id),
            bucketId = match.bucketId,
            folderName = match.bucketName,
        )
    }

    private companion object {
        const val RELOAD_DEBOUNCE_MS = 400L
    }
}
