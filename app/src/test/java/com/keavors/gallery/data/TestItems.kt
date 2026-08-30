package com.keavors.gallery.data

/**
 * Builds a media item for tests.
 *
 * One factory rather than a copy in every test class: the model gains a field
 * every other stage, and each copy would have to be found and updated.
 */
fun testItem(
    id: Long,
    name: String = "IMG_$id.jpg",
    isVideo: Boolean = false,
    size: Long = 1000,
    taken: Long = 1_700_000_000_000,
    bucket: Long = 1,
    favorite: Boolean = false,
    expiresAt: Long = 0,
) = MediaItem(
    id = id,
    uri = "content://media/external/images/media/$id",
    name = name,
    mimeType = if (isVideo) "video/mp4" else "image/jpeg",
    isVideo = isVideo,
    sizeBytes = size,
    width = 4000,
    height = 3000,
    durationMs = if (isVideo) 5000 else 0,
    takenAt = taken,
    addedAt = taken,
    modifiedAt = taken,
    bucketId = bucket,
    bucketName = "Folder$bucket",
    relativePath = "DCIM/Folder$bucket/",
    isFavorite = favorite,
    orientation = 0,
    expiresAt = expiresAt,
)
