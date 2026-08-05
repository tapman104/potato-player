package com.potato.player

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.potato.player.engine.MpvWrapper
import kotlinx.coroutines.flow.first
import dagger.hilt.android.AndroidEntryPoint

private val AmoledDarkColorScheme = darkColorScheme(
    background        = Color(0xFF000000),
    surface           = Color(0xFF000000),
    surfaceVariant    = Color(0xFF0D0D0D),
    surfaceContainer  = Color(0xFF0A0A0A),
    surfaceContainerHigh = Color(0xFF111111),
    surfaceContainerHighest = Color(0xFF1A1A1A),
    onBackground      = Color(0xFFFFFFFF),
    onSurface         = Color(0xFFFFFFFF),
    primary           = Color(0xFF90CAF9),
    onPrimary         = Color(0xFF000000),
    secondary         = Color(0xFF80CBC4),
    onSecondary       = Color(0xFF000000)
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var pendingIntent by mutableStateOf<Intent?>(null)
    private val mpvWrapper by lazy { MpvWrapper(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // MpvWrapper initializes in its init block, so just by accessing it, it initializes.
        val wrapper = mpvWrapper 
        pendingIntent = intent
        
        val initialRoute = parseViewIntent(intent)

        // Set orientation before setContent to prevent portrait flash
        if (initialRoute != null) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        // Immersive full-screen
        WindowCompat.setDecorFitsSystemWindows(window, false)

        addOnPictureInPictureModeChangedListener { info ->
            handlePipModeChange(info.isInPictureInPictureMode)
        }

        setContent {
            MaterialTheme(colorScheme = AmoledDarkColorScheme) {
                val navController = rememberNavController()

                AppNavigation(
                    navController = navController,
                    wrapper       = mpvWrapper
                )

                LaunchedEffect(navController, pendingIntent) {
                    pendingIntent?.let { newIntent ->
                        parseViewIntent(newIntent)?.let { route ->
                            navController.navigate(route) {
                                popUpTo(HomeRoute) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                        pendingIntent = null
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntent = intent
    }

    private fun parseViewIntent(intent: Intent?): PlayerRoute? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null

        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val flags = intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (flags != 0) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // transient grant only — still have access for this session
                }
            }
            try {
                contentResolver.openFileDescriptor(uri, "r")?.close()
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "Cannot read file: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                return null // URI unreadable, bail
            }
        }

        val title = uri.lastPathSegment?.substringAfterLast('/') ?: ""
        val route = PlayerRoute(
            videoUri = android.net.Uri.encode(uri.toString()),
            title = android.net.Uri.encode(title),
            isExternal = true
        )
        intent.action = Intent.ACTION_MAIN
        return route
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // MpvWrapper takes care of this internally via surface Changed callbacks.
    }

    override fun onPause() {
        super.onPause()
        // Don't pause playback when transitioning into PiP mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) return
        mpvWrapper.pause()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        handlePipModeChange(isInPictureInPictureMode)
    }

    private fun handlePipModeChange(isInPip: Boolean) {
        // PlayerViewModel now watches isInPipMode from activity/system or we don't need to manually set it.
        // Actually, PlayerViewModel watches PipMode internally or through UI state.
        if (isInPip) {
            mpvWrapper.resume() // or just let it play
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            mpvWrapper.destroy()
        }
    }
}
