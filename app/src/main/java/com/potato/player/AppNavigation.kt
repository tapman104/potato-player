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
import com.potato.player.feature.settings.AboutScreen
import com.potato.player.feature.settings.AdvancedScreen
import com.potato.player.feature.settings.AppearanceScreen
import com.potato.player.feature.settings.AudioScreen
import com.potato.player.feature.settings.DecoderScreen
import com.potato.player.feature.settings.PlayerScreen as PlayerSettingsScreen
import com.potato.player.feature.settings.SettingsScreen
import com.potato.player.feature.settings.SubtitlesScreen

import com.potato.player.util.findActivity
import kotlinx.serialization.Serializable
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.potato.player.data.UserPreferencesRepository

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppNavigationEntryPoint {
    fun userPreferencesRepository(): UserPreferencesRepository
}

// ── Top-level routes ──────────────────────────────────────────────────────────

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

// ── Settings sub-routes ───────────────────────────────────────────────────────

@Serializable
data object AppearanceRoute

@Serializable
data object PlayerSettingsRoute

@Serializable
data object DecoderRoute

@Serializable
data object SubtitlesSettingsRoute

@Serializable
data object AudioRoute

@Serializable
data object AdvancedRoute

@Serializable
data object AboutRoute

// ── Placeholder routes (callbacks wired but screens not yet implemented) ──────

@Serializable
data object ChangelogRoute

@Serializable
data object LicensesRoute

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

        // ── Settings hub (stateless category list) ────────────────────────────
        composable<SettingsRoute> {
            SettingsScreen(
                onBack             = { navController.popBackStack() },
                onNavigateToHome   = { navController.popBackStack(HomeRoute, inclusive = false) },
                onNavigateToAppearance = { navController.navigate(AppearanceRoute) },
                onNavigateToPlayer     = { navController.navigate(PlayerSettingsRoute) },
                onNavigateToDecoder    = { navController.navigate(DecoderRoute) },
                onNavigateToSubtitles  = { navController.navigate(SubtitlesSettingsRoute) },
                onNavigateToAudio      = { navController.navigate(AudioRoute) },
                onNavigateToAdvanced   = { navController.navigate(AdvancedRoute) },
                onNavigateToAbout      = { navController.navigate(AboutRoute) }
            )
        }

        // ── Settings sub-screens ──────────────────────────────────────────────
        composable<AppearanceRoute> {
            AppearanceScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<PlayerSettingsRoute> {
            PlayerSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<DecoderRoute> {
            DecoderScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<SubtitlesSettingsRoute> {
            SubtitlesScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<AudioRoute> {
            AudioScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<AdvancedRoute> {
            AdvancedScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // ── About + placeholder sub-routes ────────────────────────────────────
        composable<AboutRoute> {
            AboutScreen(
                onBack         = { navController.popBackStack() },
                onChangelog    = { navController.navigate(ChangelogRoute) },
                onLicenses     = { navController.navigate(LicensesRoute) },
                onPrivacyPolicy = { }
            )
        }

        // Placeholder — no screen implemented yet, just pop back immediately
        composable<ChangelogRoute> {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                navController.popBackStack()
            }
        }

        composable<LicensesRoute> {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                navController.popBackStack()
            }
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

            val prefsRepository = androidx.compose.runtime.remember(context) {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    AppNavigationEntryPoint::class.java
                ).userPreferencesRepository()
            }

            val playerViewModel: PlayerViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return PlayerViewModel(
                            context.applicationContext, 
                            wrapper, 
                            historyRepository,
                            prefsRepository
                        ) as T
                    }
                }
            )

            PlayerScreen(
                videoUri       = videoUri,
                title          = title,
                viewModel      = playerViewModel,
                isExternalIntent = isExternal,
                playlist       = playlist,
                playlistTitles = playlistTitles,
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
