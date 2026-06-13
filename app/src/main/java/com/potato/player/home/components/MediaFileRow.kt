package com.potato.player.home.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import coil.transition.CrossfadeTransition
import com.potato.player.data.MediaFile
import com.potato.player.data.toFormattedDuration
import com.potato.player.data.toFormattedSize

private val THUMB_WIDTH  = 140.dp
private val THUMB_HEIGHT = 80.dp

// Derive a short codec/container label from the MIME type
private fun MediaFile.codecLabel(): String = when {
    mimeType.contains("mkv")  || mimeType.contains("matroska") -> "MKV"
    mimeType.contains("mp4")  || mimeType.contains("mpeg4")    -> "MP4"
    mimeType.contains("avi")                                    -> "AVI"
    mimeType.contains("webm")                                   -> "WEBM"
    mimeType.contains("mov")  || mimeType.contains("quicktime") -> "MOV"
    mimeType.contains("3gp")                                    -> "3GP"
    mimeType.contains("flv")                                    -> "FLV"
    mimeType.contains("ts")                                     -> "TS"
    mimeType.contains("aac")                                    -> "AAC"
    mimeType.contains("mp3")  || mimeType.contains("mpeg")      -> "MP3"
    mimeType.contains("flac")                                   -> "FLAC"
    mimeType.contains("ogg")                                    -> "OGG"
    mimeType.contains("wav")                                    -> "WAV"
    else -> mimeType.substringAfterLast('/').uppercase().take(5)
}

@Composable
private fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by transition.animateFloat(
        initialValue = -1f,
        targetValue  = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF1E1E28),
            Color(0xFF2E2E3A),
            Color(0xFF1E1E28),
        ),
        start = Offset(shimmerOffset * 400f, 0f),
        end   = Offset(shimmerOffset * 400f + 300f, 200f)
    )
    Box(modifier = modifier.background(shimmerBrush))
}

@Composable
fun MediaFileRow(
    file: MediaFile,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "rowScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color(0xFF6C63FF).copy(alpha = 0.18f)),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Thumbnail ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(width = THUMB_WIDTH, height = THUMB_HEIGHT)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1A22)),
            contentAlignment = Alignment.Center
        ) {
            if (file.isVideo) {
                val painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(context)
                        .data(file.uri)
                        .size(Size(420, 240))
                        .memoryCacheKey("vthumb_${file.uri}_420x240")
                        .diskCacheKey("vthumb_${file.uri}_420x240")
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .transitionFactory(CrossfadeTransition.Factory(durationMillis = 300))
                        .build()
                )

                when (painter.state) {
                    is AsyncImagePainter.State.Loading,
                    is AsyncImagePainter.State.Empty -> {
                        ShimmerBox(modifier = Modifier.size(THUMB_WIDTH, THUMB_HEIGHT))
                    }
                    else -> {
                        Image(
                            painter = painter,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(THUMB_WIDTH, THUMB_HEIGHT)
                        )
                    }
                }

                // Dark gradient overlay + play icon
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
                                radius = 200f
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Duration badge — bottom-end
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = file.durationMs.toFormattedDuration(),
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                // Audio placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1A1035), Color(0xFF2A1A50))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.MusicNote,
                        contentDescription = "Audio",
                        tint = Color(0xFF6C63FF).copy(alpha = 0.85f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // ── Metadata ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .padding(start = 14.dp)
                .weight(1f)
        ) {
            Text(
                text = file.displayName,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 19.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Codec badge
                Box(
                    modifier = Modifier
                        .background(Color(0xFF6C63FF).copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = file.codecLabel(),
                        fontSize = 9.sp,
                        color = Color(0xFF9E97FF),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp
                    )
                }
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = file.sizeBytes.toFormattedSize(),
                    fontSize = 12.sp,
                    color = Color(0xFF7A7A88)
                )
            }
        }

        // ── Overflow menu ─────────────────────────────────────────────
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "More options",
                    tint = Color(0xFF666677)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(Color(0xFF242430))
            ) {
                DropdownMenuItem(
                    text = { Text("Play", color = Color.White, fontSize = 14.sp) },
                    onClick = {
                        menuExpanded = false
                        onClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Details", color = Color(0xFFAAAAAA), fontSize = 14.sp) },
                    onClick = { menuExpanded = false }
                )
            }
        }
    }
}
