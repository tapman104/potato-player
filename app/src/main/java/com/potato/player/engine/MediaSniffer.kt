package com.potato.player.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Two-stage detector for files with missing or incorrect MIME types.
 *
 * Stage 1 â€“ [sniffMagic]: reads 12 bytes, matches known container magic numbers.
 *            Fast enough to run inline before ExoPlayer.prepare().
 * Stage 2 â€“ [probePlayability]: MediaMetadataRetriever confirms a playable track exists.
 *            Suspend; must run on IO dispatcher.
 */
object MediaSniffer {

    data class SniffResult(
        val isMedia: Boolean,
        /** MIME hint for MediaItem.Builder.setMimeType(); null = let ExoPlayer decide */
        val mimeHint: String? = null
    )

    // â”€â”€ Stage 1: Magic bytes â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun sniffMagic(context: Context, uri: Uri): SniffResult {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buf = ByteArray(12)
                val n = stream.read(buf)
                if (n < 4) SniffResult(false) else classify(buf, n)
            } ?: SniffResult(false)
        } catch (e: Exception) {
            android.util.Log.e("MediaSniffer", "MediaSniffer.sniffMagic: ${e.message}")
            SniffResult(false)
        }
    }

    // â”€â”€ Stage 2: MediaMetadataRetriever probe â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    suspend fun probePlayability(context: Context, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(context, uri)
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) != null
            } catch (e: Exception) {
                android.util.Log.e("MediaSniffer", "MediaSniffer.probePlayability: ${e.message}")
                false
            } finally {
                @Suppress("SwallowedException")
                try { mmr.release() } catch (_: Exception) {}
            }
        }

    // â”€â”€ Magic-byte classification table â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun classify(b: ByteArray, n: Int): SniffResult {
        // ISO Base Media file (MP4 / M4V / M4A / MOV / 3GP): ftyp box at offset 4
        if (n >= 8 && b[4] == 0x66.b && b[5] == 0x74.b && b[6] == 0x79.b && b[7] == 0x70.b)
            return SniffResult(true, "video/mp4")

        // Matroska / WebM: EBML header
        if (n >= 4 && b[0] == 0x1A.b && b[1] == 0x45.b && b[2] == 0xDF.b && b[3] == 0xA3.b)
            return SniffResult(true, "video/x-matroska")

        // RIFF container: distinguish AVI ("AVI " at offset 8) from WAV
        if (n >= 4 && b[0] == 0x52.b && b[1] == 0x49.b && b[2] == 0x46.b && b[3] == 0x46.b) {
            return if (n >= 12 && b[8] == 0x41.b && b[9] == 0x56.b && b[10] == 0x49.b && b[11] == 0x20.b)
                SniffResult(true, "video/x-msvideo")
            else
                SniffResult(true, "audio/wav")
        }

        // FLV
        if (n >= 3 && b[0] == 0x46.b && b[1] == 0x4C.b && b[2] == 0x56.b)
            return SniffResult(true, "video/x-flv")

        // ID3 tag â†’ MP3
        if (n >= 3 && b[0] == 0x49.b && b[1] == 0x44.b && b[2] == 0x33.b)
            return SniffResult(true, "audio/mpeg")

        // MP3 sync frame (FF FB / FF F3 / FF F2)
        if (n >= 2 && b[0] == 0xFF.b && (b[1] == 0xFB.b || b[1] == 0xF3.b || b[1] == 0xF2.b))
            return SniffResult(true, "audio/mpeg")

        // AAC ADTS (FF F1 / FF F9)
        if (n >= 2 && b[0] == 0xFF.b && (b[1] == 0xF1.b || b[1] == 0xF9.b))
            return SniffResult(true, "audio/aac")

        // FLAC
        if (n >= 4 && b[0] == 0x66.b && b[1] == 0x4C.b && b[2] == 0x61.b && b[3] == 0x43.b)
            return SniffResult(true, "audio/flac")

        // OGG (Vorbis / Opus / Theora)
        if (n >= 4 && b[0] == 0x4F.b && b[1] == 0x67.b && b[2] == 0x67.b && b[3] == 0x53.b)
            return SniffResult(true, "audio/ogg")

        // MPEG-2 Transport Stream
        if (n >= 1 && b[0] == 0x47.b)
            return SniffResult(true, "video/mp2ts")

        // ASF / WMV / WMA: header GUID
        if (n >= 4 && b[0] == 0x30.b && b[1] == 0x26.b && b[2] == 0xB2.b && b[3] == 0x75.b)
            return SniffResult(true, "video/x-ms-asf")

        // MPEG-1/2 Program Stream pack header
        if (n >= 4 && b[0] == 0x00.b && b[1] == 0x00.b && b[2] == 0x01.b && b[3] == 0xBA.b)
            return SniffResult(true, "video/mpeg")

        // MPEG-1/2 video Elementary Stream
        if (n >= 4 && b[0] == 0x00.b && b[1] == 0x00.b && b[2] == 0x01.b && b[3] == 0xB3.b)
            return SniffResult(true, "video/mpeg")

        // RealMedia (RM / RMVB)
        if (n >= 4 && b[0] == 0x2E.b && b[1] == 0x52.b && b[2] == 0x4D.b && b[3] == 0x46.b)
            return SniffResult(true, "application/vnd.rn-realmedia")

        return SniffResult(false)
    }

    private val Int.b: Byte get() = toByte()
}
