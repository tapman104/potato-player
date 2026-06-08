package com.potato.player.home.components

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.potato.player.data.MediaFile
import com.potato.player.data.toFormattedDuration
import com.potato.player.data.toFormattedSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MediaFileRow(
    file: MediaFile,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val thumbnail by produceState<Bitmap?>(initialValue = null, key1 = file.uri) {
        if (file.isVideo) {
            value = withContext(Dispatchers.IO) {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        context.contentResolver.loadThumbnail(file.uri, Size(128, 128), null)
                    } else {
                        val filePath = File(file.folderPath, file.displayName).absolutePath
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color(0xFF6C63FF).copy(alpha = 0.1f)),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 68.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1C1C1C)),
            contentAlignment = Alignment.Center
        ) {
            if (file.isVideo) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail!!.asImageBitmap(),
                        contentDescription = "Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Outlined.Movie, contentDescription = "Video", tint = Color(0xFF6C63FF), modifier = Modifier.size(24.dp))
                }
            } else {
                Icon(Icons.Outlined.MusicNote, contentDescription = "Audio", tint = Color(0xFF6C63FF), modifier = Modifier.size(24.dp))
            }
        }

        Column(modifier = Modifier
            .padding(start = 16.dp)
            .weight(1f)
        ) {
            Text(
                text = file.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${file.durationMs.toFormattedDuration()} • ${if (file.sizeBytes > 0) "1080p" else "SD"}", // TODO: Actual resolution if available, using placeholder for now to match screenshot style
                fontSize = 13.sp,
                color = Color(0xFFAAAAAA),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "More options",
                    tint = Color.White
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(Color(0xFF2C2C2C))
            ) {
                DropdownMenuItem(
                    text = { Text("Play", color = Color.White) },
                    onClick = {
                        menuExpanded = false
                        onClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Details", color = Color.White) },
                    onClick = {
                        menuExpanded = false
                        // TODO: Show details dialog
                    }
                )
            }
        }
    }
}
