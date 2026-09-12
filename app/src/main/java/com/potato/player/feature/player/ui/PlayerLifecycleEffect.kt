package com.potato.player.feature.player.ui

import com.potato.player.feature.player.state.*
import com.potato.player.feature.player.PlayerViewModel
import androidx.compose.runtime.*
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

// ponytail: extracted from PlayerScreen — zero new logic
@Composable
fun PlayerLifecycleEffect(
    activity: android.app.Activity?,
    uiState: PlayerUiState,
    viewModel: PlayerViewModel
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(uiState.isLocked, uiState.videoOrientation,
                   uiState.videoWidth, uiState.videoHeight, uiState.videoRotate,
                   activity) {

        val orientation = if (uiState.isLocked) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else {
            when (uiState.videoOrientation) {
                "landscape"        -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                "portrait"         -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                "sensor"           -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                "sensor_landscape" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                "sensor_portrait"  -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                "locked"           -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
                "auto"             -> {
                    val w = uiState.videoWidth
                    val h = uiState.videoHeight
                    val rotate = uiState.videoRotate
                    if (w <= 0 || h <= 0) {
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    } else {
                        val effectiveW = if (rotate == 90 || rotate == 270) h else w
                        val effectiveH = if (rotate == 90 || rotate == 270) w else h
                        when {
                            effectiveW > effectiveH ->
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            effectiveH > effectiveW ->
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            else ->
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                        }
                    }
                }
                else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            }
        }
        activity?.requestedOrientation = orientation
    }
    DisposableEffect(lifecycleOwner, activity) {
        val window = activity?.window
        if (window != null) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && activity?.isInPictureInPictureMode == true) return@LifecycleEventObserver
                if (activity?.isChangingConfigurations == false) {
                    viewModel.onPlayerPause()
                }
            } else if (event == Lifecycle.Event.ON_RESUME) {
                if (activity?.isChangingConfigurations == false) {
                    viewModel.onPlayerResume()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Do NOT reset orientation while the activity is finishing — the OS renders one
            // more frame in the new orientation before the window closes, causing a visible
            // flash. Only unlock if we are NOT finishing (e.g. unexpected recomposition).
            if (activity?.isChangingConfigurations == false && activity?.isFinishing == false) {
                if (activity.isInPictureInPictureMode == false) {
                    if (window != null) {
                        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
        }
    }
}
