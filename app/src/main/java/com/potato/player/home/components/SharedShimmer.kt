package com.potato.player.home.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * A single shared shimmer offset for the entire screen.
 *
 * Instead of every visible row running its own InfiniteTransition
 * (= N gradient re-computations per frame), one animation drives all
 * shimmer boxes simultaneously through CompositionLocal.
 */
val LocalShimmerOffset = compositionLocalOf { 0f }

/**
 * Wrap the content that contains shimmer boxes with this composable.
 * One InfiniteTransition is created here and shared down the tree.
 */
@Composable
fun ShimmerHost(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "shimmerHost")
    val offset by transition.animateFloat(
        initialValue = -600f,
        targetValue  =  600f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerHostOffset"
    )
    CompositionLocalProvider(LocalShimmerOffset provides offset) {
        content()
    }
}

/**
 * A shimmer placeholder box driven by the nearest [ShimmerHost].
 * Falls back gracefully if no host is provided (offset = 0).
 */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val offset = LocalShimmerOffset.current
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF1A1A24),
                    Color(0xFF2A2A38),
                    Color(0xFF1A1A24),
                ),
                start = Offset(offset, 0f),
                end   = Offset(offset + 400f, 180f)
            )
        )
    )
}
