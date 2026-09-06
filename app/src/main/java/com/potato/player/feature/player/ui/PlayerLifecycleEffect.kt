package com.potato.player.feature.player.ui

import android.content.pm.ActivityInfo
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
    DisposableEffect(lifecycleOwner, activity) {
        val window = activity?.window
        if (window != null) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        viewModel.orientationManager.activity = activity
        // Apply the persisted orientation mode once the activity reference is live.
        // OrientationManager is the sole owner of requestedOrientation.
        viewModel.orientationManager.apply(
            orientationMode = uiState.orientationMode,
            videoOrientation = uiState.videoOrientation,
            videoRotate = uiState.videoRotate
        )

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
                if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                    viewModel.orientationManager.reset()
                }
                if (activity.isInPictureInPictureMode == false) {
                    if (window != null) {
                        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
            viewModel.orientationManager.activity = null
        }
    }
}
