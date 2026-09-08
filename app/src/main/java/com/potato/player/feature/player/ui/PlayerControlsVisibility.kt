package com.potato.player.feature.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

@Composable
fun rememberControlsVisibility(
    isPlaying: Boolean,
    hideDelayMs: Long = 3000L,
    isSeeking: Boolean,
    isFastForwarding: Boolean,
    isLocked: Boolean,
    isSwipingVolumeOrBrightness: Boolean,
    isPipMode: Boolean
): Pair<Boolean, () -> Unit> {
    var controlsVisible by rememberSaveable { mutableStateOf(false) }
    var interactionTick by remember { mutableStateOf(0L) }

    LaunchedEffect(isLocked) {
        if (isLocked) {
            controlsVisible = false
        } else {
            controlsVisible = true
            interactionTick = System.currentTimeMillis()
        }
    }

    LaunchedEffect(interactionTick, isPlaying, isSeeking, isFastForwarding, isLocked, isSwipingVolumeOrBrightness) {
        if (isLocked) return@LaunchedEffect
        if (controlsVisible && isPlaying && !isSeeking && !isFastForwarding && !isSwipingVolumeOrBrightness) {
            delay(hideDelayMs)
            controlsVisible = false
        }
    }

    LaunchedEffect(isPipMode) {
        if (isPipMode) controlsVisible = false
    }

    val onUserInteraction: () -> Unit = {
        if (controlsVisible) {
            controlsVisible = false
        } else {
            controlsVisible = true
            interactionTick = System.currentTimeMillis()
        }
    }

    return Pair(controlsVisible, onUserInteraction)
}
