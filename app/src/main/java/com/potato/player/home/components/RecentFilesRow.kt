package com.potato.player.home.components

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

@Composable
fun RecentFilesRow(
    files: List<MediaFile>,
    onFilePicked: (Uri) -> Unit,
    // Optional: pass saved-position fractions per URI for the progress arc
    positionFractions: Map<String, Float> = emptyMap()
) {
    if (files.isEmpty()) return

    val lazyListState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Recently Played",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFAAAAAA),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 28.dp, bottom = 14.dp)
        )

        LazyRow(
            state = lazyListState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        )  {
            items(files, key = { it.uri.toString() }) { file ->
                RecentFileItem(
                    file = file,
                    progressFraction = positionFractions[file.uri.toString()] ?: 0f,
                    onClick = { onFilePicked(file.uri) }
                )
            }
        }
    }
}

@Composable
private fun RecentFileItem(
    file: MediaFile,
    progressFraction: Float,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .width(162.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1A22))
            .clickable(onClick = onClick)
    ) {
        Column {
            // ── Thumbnail area ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .background(Color(0xFF161620)),
                contentAlignment = Alignment.Center
            ) {
                if (file.isVideo) {
                    val painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(context)
                            .data(file.uri)
                            .size(Size(480, 270))
                            .memoryCacheKey("vthumb_${file.uri}_480x270")
                            .diskCacheKey("vthumb_${file.uri}_480x270")
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .transitionFactory(CrossfadeTransition.Factory(durationMillis = 300))
                            .build()
                    )

                    when (painter.state) {
                        is AsyncImagePainter.State.Loading,
                        is AsyncImagePainter.State.Empty -> {
                            // Shimmer only runs while loading — stops automatically
                            // when bitmap arrives, saving CPU/GPU.
                            val shimmerTransition = rememberInfiniteTransition(label = "recentShimmer")
                            val shimmerOffset by shimmerTransition.animateFloat(
                                initialValue = -1f,
                                targetValue  = 2f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(900, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "recentShimmerOffset"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF1E1E28), Color(0xFF2A2A38), Color(0xFF1E1E28)),
                                            start = Offset(shimmerOffset * 400f, 0f),
                                            end   = Offset(shimmerOffset * 400f + 300f, 200f)
                                        )
                                    )
                            )
                        }
                        else -> {
                            Image(
                                painter = painter,
                                contentDescription = "Thumbnail",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Gradient scrim + play icon (always on top of thumbnail/shimmer)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.38f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Duration badge
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
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Progress bar (only rendered when position is known)
                    if (progressFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                                    .height(3.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF6C63FF), Color(0xFFAA77FF))
                                        )
                                    )
                            )
                        }
                    }
                } else {
                    // Audio placeholder — static, no animation cost
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

            // ── Title ─────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = file.displayName,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
