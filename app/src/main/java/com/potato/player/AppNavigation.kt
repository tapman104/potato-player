package com.potato.player


import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.potato.player.engine.MpvWrapper
import com.potato.player.feature.home.FolderScreen
import com.potato.player.feature.home.HomeScreen
import com.potato.player.feature.player.ui.PlayerScreen
import com.potato.player.feature.player.PlayerViewModel
import com.potato.player.feature.player.PlayerViewModelFactory
import com.potato.player.feature.settings.SettingsScreen
import android.content.pm.ActivityInfo
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
    val isExternal: Boolean = false,
    val playlist: List<String> = emptyList(),       // encoded URIs of all videos in folder, in order
    val playlistTitles: List<String> = emptyList()  // titles matching playlist order
)

@Serializable
data object SettingsRoute

@Serializable
data object AboutRoute

@Composable
fun AppNavigation(
    navController: NavHostController,
    wrapper: MpvWrapper,
    startDestination: PlayerStartDestination = PlayerStartDestination.Home
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    // Map the semantic start destination to a concrete nav route.
    // This mapping runs before the first NavHost composition — HomeScreen is
    // never rendered when startDestination is PlayerStartDestination.Player.
    val navStartRoute: Any = when (startDestination) {
        is PlayerStartDestination.Home -> HomeRoute
        is PlayerStartDestination.Player -> PlayerRoute(
            videoUri = android.net.Uri.encode(startDestination.uri.toString()),
            title    = android.net.Uri.encode(startDestination.title ?: ""),
            isExternal = true
        )
    }

    NavHost(
        navController = navController,
        startDestination = navStartRoute
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onNavigateToPlayer = { uri, title ->
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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
                onNavigateToPlayer = { uri, title, playlist ->
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    navController.navigate(
                        PlayerRoute(
                            videoUri = android.net.Uri.encode(uri),
                            title = android.net.Uri.encode(title),
                            playlist = playlist.map { android.net.Uri.encode(it.uri.toString()) },
                            playlistTitles = playlist.map { android.net.Uri.encode(it.title) }
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
                },
                onNavigateToAbout = { navController.navigate(AboutRoute) }
            )
        }

        composable<AboutRoute> {
            com.potato.player.feature.settings.AboutScreen(
                onBack = { navController.popBackStack() },
                onChangelog = { },
                onLicenses = { },
                onPrivacyPolicy = { }
            )
        }

        composable<PlayerRoute> { backStackEntry ->
            val route: PlayerRoute = backStackEntry.toRoute()
            val videoUri = android.net.Uri.decode(route.videoUri)
            val title = android.net.Uri.decode(route.title)
            val isExternal = route.isExternal
            val playlist = route.playlist.map { android.net.Uri.decode(it) }
            val playlistTitles = route.playlistTitles.map { android.net.Uri.decode(it) }
            val context = LocalContext.current
            val activity = context.findActivity()

            val historyRepository = androidx.compose.runtime.remember(context) {
                val db = com.potato.player.data.AppDatabase.getInstance(context)
                com.potato.player.data.VideoHistoryRepository(db.videoHistoryDao())
            }

            val playerViewModel: PlayerViewModel = viewModel(
                factory = PlayerViewModelFactory(context.applicationContext, wrapper, historyRepository)
            )

            PlayerScreen(
                videoUri       = videoUri,
                title          = title,
                viewModel      = playerViewModel,
                isExternalIntent = isExternal,
                playlist       = playlist,
                playlistTitles = playlistTitles,
                onBack    = {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
