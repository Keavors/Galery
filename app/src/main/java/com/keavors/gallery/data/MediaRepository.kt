package com.keavors.gallery.data

import android.content.Context
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

    private companion object {
        const val RELOAD_DEBOUNCE_MS = 400L
    }
}
