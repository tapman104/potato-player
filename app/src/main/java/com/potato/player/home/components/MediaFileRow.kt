package com.potato.player.home.components

import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.decode.VideoFrameDecoder
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.potato.player.data.MediaFile
import com.potato.player.data.toFormattedDuration
import com.potato.player.data.toFormattedSize

@Composable
fun MediaFileRow(
    file: MediaFile,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }



    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color(0xFF6C63FF).copy(alpha = 0.2f)),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 140.dp, height = 80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1E24)),
            contentAlignment = Alignment.Center
        ) {
            if (file.isVideo) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(file.uri)
                        .decoderFactory(VideoFrameDecoder.Factory())
                        .crossfade(true)
                        .build(),
                    contentDescription = "Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(28.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = file.durationMs.toFormattedDuration(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Icon(Icons.Outlined.MusicNote, contentDescription = "Audio", tint = Color(0xFF6C63FF), modifier = Modifier.size(28.dp))
            }
        }

        Column(modifier = Modifier
            .padding(start = 16.dp)
            .weight(1f)
        ) {
            Text(
                text = file.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF2A2A35), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (file.sizeBytes > 0) "1080p" else "SD",
                        fontSize = 10.sp,
                        color = Color(0xFFB0B0B8),
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = file.sizeBytes.toFormattedSize(),
                    fontSize = 12.sp,
                    color = Color(0xFF8A8A93)
                )
            }
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
