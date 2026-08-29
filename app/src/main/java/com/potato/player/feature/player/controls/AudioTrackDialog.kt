package com.potato.player.feature.player.controls

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.potato.player.feature.player.state.TrackUiModel

@Composable
fun AudioTrackDialog(
    visible: Boolean,
    tracks: List<TrackUiModel>,
    currentTrackId: Int,
    onSelectTrack: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 0.45f else 0f,
        label = "scrimAlpha"
    )
    val transitionState = remember { androidx.compose.animation.core.MutableTransitionState(visible) }
    transitionState.targetState = visible

    if (transitionState.currentState || transitionState.targetState || scrimAlpha > 0f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .pointerInput(visible || scrimAlpha > 0f) {
                    detectTapGestures(onTap = { _ -> onDismiss() })
                }
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                    color = Color(0xFF1E1E1E).copy(alpha = 0.90f),
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(380.dp)
                        .pointerInput(Unit) {
                            detectTapGestures {}
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Audio Track",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = onDismiss, modifier = PlayerControlsStyles.dialogIconButtonModifier) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        if (tracks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No audio tracks available",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(tracks, key = { it.id }) { track ->
                                    val isSelected = track.id == currentTrackId
                                    TrackSelectionRow(
                                        label = track.displayLabel,
                                        isSelected = isSelected,
                                        onClick = { onSelectTrack(track.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
