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
import com.potato.player.feature.home.FolderScreen
import com.potato.player.feature.home.HomeScreen
import com.potato.player.feature.player.PlayerScreen
import com.potato.player.feature.player.PlayerViewModel
import com.potato.player.feature.player.PlayerViewModelFactory
import com.potato.player.feature.settings.SettingsScreen
import com.potato.player.util.findActivity
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class FolderRoute(
    val bucketId: Long,
    val folderName: String
)

@Serializable
data class PlayerRoute(
    val videoUri: String,
    val title: String = "",
    val isExternal: Boolean = false
)

@Serializable
data object SettingsRoute

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
                    navController.navigate(
                        PlayerRoute(
                            videoUri = android.net.Uri.encode(uri),
                            title = android.net.Uri.encode(title)
                        )
                    )
                },
                onNavigateToFolder = { bucketId, folderName ->
                    navController.navigate(
                        FolderRoute(
                            bucketId = bucketId,
                            folderName = folderName
                        )
                    )
                },
                onNavigateToSettings = {
                    navController.navigate(SettingsRoute)
                }
            )
        }

        composable<FolderRoute> { backStackEntry ->
            val route: FolderRoute = backStackEntry.toRoute()
            FolderScreen(
                bucketId = route.bucketId,
                folderName = route.folderName,
                onNavigateToPlayer = { uri, title ->
                    navController.navigate(
                        PlayerRoute(
                            videoUri = android.net.Uri.encode(uri),
                            title = android.net.Uri.encode(title)
                        )
                    )
                },
                onBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(SettingsRoute) }
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.popBackStack(HomeRoute, inclusive = false)
                }
            )
        }

        composable<PlayerRoute> { backStackEntry ->
            val route: PlayerRoute = backStackEntry.toRoute()
            val videoUri = android.net.Uri.decode(route.videoUri)
            val title = android.net.Uri.decode(route.title)
            val isExternal = route.isExternal
            val context = LocalContext.current
            val activity = context.findActivity()

            DisposableEffect(Unit) {
                onDispose {
                    val currentActivity = context.findActivity()
                    if (currentActivity?.isFinishing == false) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || currentActivity.isInPictureInPictureMode != true) {
                            currentActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    }
                }
            }
            val historyRepository = androidx.compose.runtime.remember(context) {
                val db = com.potato.player.data.AppDatabase.getInstance(context)
                com.potato.player.data.VideoHistoryRepository(db.videoHistoryDao())
            }

            val playerViewModel: PlayerViewModel = viewModel(
                factory = PlayerViewModelFactory(context.applicationContext, wrapper, historyRepository)
            )

            PlayerScreen(
                videoUri  = videoUri,
                title     = title,
                viewModel = playerViewModel,
                isExternalIntent = isExternal,
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
