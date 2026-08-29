package com.potato.player.feature.player.controls

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.potato.player.feature.player.state.TrackUiModel
import com.potato.player.feature.player.state.PlayerUiState
import kotlin.math.roundToInt


@Composable
fun SubtitleTrackDialog(
    visible: Boolean,
    tracks: List<TrackUiModel>,
    currentTrackId: Int,
    onSelectTrack: (Int) -> Unit,
    onLaunchFilePicker: () -> Unit,
    onDismiss: () -> Unit,
    uiState: PlayerUiState,
    onSetSubtitleAppearance: (Double, Int) -> Unit,
    onPreviewSubtitleAppearance: (Double, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAppearanceDialog by remember { mutableStateOf(false) }

    // FIX: Removed conditionally-composed rememberLauncherForActivityResult.
    // Launcher is now hoisted to PlayerScreen to survive process death during file selection.

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
                                text = "Subtitle Track",
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

                        LazyColumn(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Row 0: "Off" option (id = -1)
                            item(key = "sub_off") {
                                val isSelected = currentTrackId == -1
                                TrackSelectionRow(
                                    label = "Off",
                                    isSelected = isSelected,
                                    onClick = { onSelectTrack(-1) }
                                )
                            }

                            // Rows 1..N: embedded subtitle tracks
                            items(tracks, key = { it.id }) { track ->
                                val isSelected = track.id == currentTrackId
                                TrackSelectionRow(
                                    label = track.displayLabel,
                                    isSelected = isSelected,
                                    onClick = { onSelectTrack(track.id) }
                                )
                            }

                            // Last row: "Load external subtitle..."
                            item(key = "sub_external") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onLaunchFilePicker() }
                                        .padding(vertical = 12.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Load external subtitle...",
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Subtitle appearance option
                            item(key = "sub_appearance") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { showAppearanceDialog = true }
                                        .padding(vertical = 12.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Subtitle appearance...",
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAppearanceDialog) {
        val subScale = uiState.subScale.toFloat()
        val subPos = uiState.subPos
        val initialPosition = 1f - (subPos / 100f)
        SubtitleAppearanceDialog(
            initialSize = subScale,
            initialPosition = initialPosition,
            onApply = { size, position ->
                val mpvPos = ((1f - position) * 100).roundToInt().coerceIn(0, 100)
                onSetSubtitleAppearance(size.toDouble(), mpvPos)
                showAppearanceDialog = false
            },
            onPreview = { size, position ->
                val mpvPos = ((1f - position) * 100).roundToInt().coerceIn(0, 100)
                onPreviewSubtitleAppearance(size.toDouble(), mpvPos)
            },
            onDismiss = {
                // Revert MPV to original values since user cancelled
                val revertMpvPos = subPos.coerceIn(0, 100)
                onPreviewSubtitleAppearance(subScale.toDouble(), revertMpvPos)
                showAppearanceDialog = false
            }
        )
    }
}
