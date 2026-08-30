package com.potato.player.feature.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class DoubleTapSeekState(
    val isForward: Boolean,
    val totalSeconds: Int,
    val triggerId: Long = System.currentTimeMillis()
)

@Composable
fun DoubleTapSeekOverlay(
    seekState: DoubleTapSeekState?,
    modifier: Modifier = Modifier
) {
    val lastState = remember { mutableStateOf(seekState) }
    if (seekState != null) {
        lastState.value = seekState
    }

    AnimatedVisibility(
        visible = seekState != null,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier.fillMaxSize()
    ) {
        lastState.value?.let { state ->
            // Pulse scale on each new tap
            var scale by remember { mutableFloatStateOf(1f) }
            LaunchedEffect(state.triggerId) {
                scale = 1.15f
                delay(80)
                scale = 1f
            }
            val animatedScale by animateFloatAsState(
                targetValue = scale,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                ),
                label = "seekPulse"
            )

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                val alignment = if (state.isForward) Alignment.CenterEnd else Alignment.CenterStart

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.42f)
                        .align(alignment),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.graphicsLayer {
                            scaleX = animatedScale
                            scaleY = animatedScale
                        }
                    ) {
                        Icon(
                            imageVector = if (state.isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                            contentDescription = if (state.isForward) "Fast forward" else "Fast rewind",
                            tint = Color.White,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (state.isForward) "+${state.totalSeconds}s" else "-${state.totalSeconds}s",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
