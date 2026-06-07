package com.potato.player.home.components

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.potato.player.data.MediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun RecentFilesRow(
    files: List<MediaFile>,
    onFilePicked: (Uri) -> Unit
) {
    if (files.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Recently Played",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
        )
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(files) { file ->
                RecentFileItem(file = file, onClick = { onFilePicked(file.uri) })
            }
        }
    }
}

@Composable
private fun RecentFileItem(file: MediaFile, onClick: () -> Unit) {
    val thumbnail by produceState<Bitmap?>(initialValue = null, key1 = file.uri) {
        if (file.isVideo) {
            value = withContext(Dispatchers.IO) {
                try {
                    val filePath = File(file.folderPath, file.displayName).absolutePath
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        ThumbnailUtils.createVideoThumbnail(
                            File(filePath),
                            Size(256, 144),
                            null
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        ThumbnailUtils.createVideoThumbnail(
                            filePath,
                            MediaStore.Images.Thumbnails.MINI_KIND
                        )
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .width(100.dp)
            .height(130.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A1A))
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center
            ) {
                if (file.isVideo) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail!!.asImageBitmap(),
                            contentDescription = "Thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(80.dp)
                        )
                    } else {
                        Icon(Icons.Outlined.Videocam, contentDescription = "Video", tint = Color(0xFF555555))
                    }
                } else {
                    Icon(Icons.Outlined.MusicNote, contentDescription = "Audio", tint = Color(0xFF7C4DFF))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
