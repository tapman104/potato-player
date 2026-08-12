package com.potato.player.feature.player

import android.content.pm.ActivityInfo
import androidx.compose.runtime.*
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.potato.player.util.lockOrientation

// ponytail: extracted from PlayerScreen — zero new logic
@Composable
fun PlayerLifecycleEffect(
    activity: android.app.Activity?,
    uiState: PlayerUiState,
    viewModel: PlayerViewModel
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasSetAspectOrientation by remember { mutableStateOf(false) }

    fun updateOrientation() {
        if (uiState.isAutoRotation) {
            hasSetAspectOrientation = true
            lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR)
            return
        }
        when (uiState.orientationMode) {
            OrientationMode.LOCK_LANDSCAPE -> {
                hasSetAspectOrientation = true
                lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
            }
            OrientationMode.LOCK_PORTRAIT -> {
                hasSetAspectOrientation = true
                lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT)
            }
            OrientationMode.AUTO -> {
                if (uiState.videoWidth > 0 && uiState.videoHeight > 0) {
                    val target = if (uiState.videoHeight > uiState.videoWidth) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                    lockOrientation(activity, target)
                    hasSetAspectOrientation = true
                } else if (!hasSetAspectOrientation) {
                    lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                }
            }
        }
    }

    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(lifecycleOwner, activity, view) {
        val window = activity?.window
        if (window != null) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        updateOrientation()
        
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
            if (activity?.isChangingConfigurations == false) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                if (activity.isFinishing == false) {
                    if (activity.isInPictureInPictureMode == false && window != null) {
                        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
        }
    }

    LaunchedEffect(uiState.fileLoaded, uiState.videoWidth, uiState.videoHeight, uiState.orientationMode, uiState.isAutoRotation) {
        updateOrientation()
    }
}
