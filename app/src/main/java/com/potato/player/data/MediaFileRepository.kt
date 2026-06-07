package com.potato.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaFileRepository(private val context: Context) {

    suspend fun queryMediaFiles(): List<MediaFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<MediaFile>()
        try {
            files.addAll(queryStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true))
            files.addAll(queryStore(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, isVideo = false))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        files
    }

    private fun queryStore(contentUri: Uri, isVideo: Boolean): List<MediaFile> {
        val files = mutableListOf<MediaFile>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.MIME_TYPE
        )

        context.contentResolver.query(
            contentUri,
            projection,
            null,
            null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val displayName = cursor.getString(nameColumn) ?: "Unknown"
                val durationMs = cursor.getLong(durationColumn)
                val sizeBytes = cursor.getLong(sizeColumn)
                val folderName = cursor.getString(bucketColumn) ?: "Unknown Folder"
                val dataPath = cursor.getString(dataColumn) ?: ""
                val mimeType = cursor.getString(mimeTypeColumn) ?: if (isVideo) "video/mp4" else "audio/mpeg"

                val uri = ContentUris.withAppendedId(contentUri, id)
                val folderPath = dataPath.substringBeforeLast("/")

                files.add(
                    MediaFile(
                        uri = uri,
                        displayName = displayName,
                        durationMs = durationMs,
                        sizeBytes = sizeBytes,
                        folderName = folderName,
                        folderPath = folderPath,
                        mimeType = mimeType
                    )
                )
            }
        }
        return files
    }
}
