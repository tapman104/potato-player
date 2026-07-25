package com.potato.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.potato.player.engine.MpvWrapper
import com.potato.player.feature.home.HomeScreen
import com.potato.player.feature.player.PlayerScreen
import com.potato.player.feature.player.PlayerViewModel
import com.potato.player.feature.player.PlayerViewModelFactory
import com.potato.player.util.findActivity
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class PlayerRoute(
    val videoUri: String,
    val title: String = ""
)

@Composable
fun AppNavigation(
    navController: NavHostController,
    wrapper: MpvWrapper
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onNavigateToPlayer = { uri, title ->
                    navController.navigate(PlayerRoute(videoUri = uri, title = title))
                }
            )
        }

        composable<PlayerRoute> { backStackEntry ->
            val route: PlayerRoute = backStackEntry.toRoute()
            val activity = LocalContext.current.findActivity()

            DisposableEffect(route.videoUri) {
                onDispose {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity?.isInPictureInPictureMode != true) {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }
            }

            val db = com.potato.player.data.AppDatabase.getInstance(LocalContext.current)
            val historyRepository = com.potato.player.data.VideoHistoryRepository(db.videoHistoryDao())

            val playerViewModel: PlayerViewModel = viewModel(
                factory = PlayerViewModelFactory(LocalContext.current.applicationContext, wrapper, historyRepository)
            )

            PlayerScreen(
                videoUri  = route.videoUri,
                title     = route.title,
                viewModel = playerViewModel,
                onBack    = {
                    navController.popBackStack()
                },
                onBrightnessChange = { brightness ->
                    val window = activity?.window
                    if (window != null) {
                        val lp = window.attributes
                        lp.screenBrightness = brightness
                        window.attributes = lp
                    }
                }
            )
        }
    }
}
