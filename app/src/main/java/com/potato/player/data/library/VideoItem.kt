package com.potato.player.data.library

import android.net.Uri

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val bucketId: Long,
    val bucketName: String
)

data class FolderItem(
    val bucketId: Long,
    val name: String,
    val videoCount: Int,
    val firstVideoUri: Uri? = null,
    val totalSizeBytes: Long = 0L
)
