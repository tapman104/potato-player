package com.potato.player.feature.player.ui

import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potato.player.feature.player.PlayerViewModel
import com.potato.player.feature.player.controls.DoubleTapSeekOverlay
import com.potato.player.util.findActivity

@Composable
fun PlayerGestureBox(
    viewModel: PlayerViewModel,
    positionProvider: () -> Double,
    durationProvider: () -> Double,
    onToggleControls: () -> Unit,
    onGestureActive: (Boolean) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val fileLoaded = uiState.fileLoaded
    val gesturesEnabled = uiState.gesturesEnabled
    val isPipMode = activity?.isInPictureInPictureMode == true

    val initialBrightness = remember {
        val wb = activity?.window?.attributes?.screenBrightness ?: -1f
        if (wb >= 0f) wb
        else try {
            Settings.System.getInt(
                activity?.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            ) / 255f
        } catch (e: Exception) { 0.5f }
    }

    val controller = remember(viewModel, context) {
        GestureController(
            positionProvider = positionProvider,
            durationProvider = durationProvider,
            gesturesEnabled = { viewModel.uiState.value.gesturesEnabled },
            scope = scope,
            setVideoZoom = viewModel::setVideoZoom,
            setVolume = viewModel::setVolume,
            onSeek = viewModel::seekTo,
            onSeekFinished = viewModel::seekTo,
            stopFastForward = viewModel::stopFastForward,
            startFastForward = viewModel::startFastForward,
            seekExactRelative = viewModel::seekExactRelative,
            togglePlay = viewModel::togglePlay,
            applyBrightness = { brightness ->
                val window = activity?.window
                if (window != null) {
                    val lp = window.attributes
                    lp.screenBrightness = brightness
                    window.attributes = lp
                }
            },
            onToggleControls = onToggleControls,
            performHapticFeedback = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
            onGestureActive = onGestureActive,
            initialBrightness = initialBrightness
        )
    }

    val gestureUiState by controller.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(fileLoaded) {
        if (fileLoaded) {
            controller.resetZoom()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(gesturesEnabled, isPipMode) {
                if (!gesturesEnabled || isPipMode) return@pointerInput
                controller.handlePointerInput(this)
            }
    ) {
        if (!isPipMode) {
            DoubleTapSeekOverlay(seekState = gestureUiState.doubleTapSeekState)
        }

        SwipeSeekOverlay(
            targetSec = gestureUiState.swipeSeekTargetSec,
            dragStartSec = gestureUiState.swipeDragStartSec,
            isPipMode = isPipMode
        )

        if (!isPipMode) {
            VolumeIndicator(
                volume = gestureUiState.volumeLevel,
                visible = gestureUiState.showVolumeIndicator,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
            )
            BrightnessIndicator(
                brightness = gestureUiState.brightnessLevel,
                visible = gestureUiState.showBrightnessIndicator,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
            )
            ZoomIndicator(
                zoom = gestureUiState.zoomLevel,
                visible = gestureUiState.showZoomIndicator,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 32.dp)
            )
        }
    }
}

