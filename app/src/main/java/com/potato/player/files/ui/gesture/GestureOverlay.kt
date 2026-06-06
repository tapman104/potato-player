package com.potato.player.player.ui.gesture

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Stateless overlay that renders visual feedback for active gestures.
 *
 * Mount this above the gesture-input layer but below the top-bar so it
 * doesn't obscure navigation chrome. The composable renders nothing when
 * [gestureState.active] is [ActiveGesture.None].
 *
 * @param gestureState Current gesture state from [PlayerGestureHandler.gestureState].
 * @param modifier     Applied to the root [Box].
 */
@Composable
fun GestureOverlay(
    gestureState: GestureState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (val gesture = gestureState.active) {
            ActiveGesture.None -> Unit // render nothing

            ActiveGesture.LongPressSpeed -> {
                // Centered pill showing "2× Speed"
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(150)),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.60f),
                                shape = RoundedCornerShape(50),
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "2× Speed",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            is ActiveGesture.DoubleTapSeek -> {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(150)),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.60f),
                                shape = RoundedCornerShape(50),
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (gesture.isForward) "+10s ⏩" else "⏪ -10s",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            is ActiveGesture.VolumeSwipe -> {
                // Centered indicator for volume
                Box(
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = "Volume",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = "${(gesture.fraction * 100).roundToInt()}%",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
