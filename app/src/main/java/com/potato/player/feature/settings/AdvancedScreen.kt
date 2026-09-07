package com.potato.player.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.potato.player.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedScreen(
    onBack: () -> Unit,
    viewModel: AdvancedViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val logState by viewModel.logState.collectAsState()
    val recentlyPlayedEnabled by viewModel.recentlyPlayedEnabled.collectAsState()
    val verboseLoggingEnabled by viewModel.verboseLoggingEnabled.collectAsState()

    var showClearHistoryDialog by remember { mutableStateOf(false) }

    val historyClearedMessage = stringResource(R.string.history_cleared)
    val logDumpErrorPrefix = stringResource(R.string.log_dump_error, "")

    // ── Dump logs: fire email chooser once log file is ready ─────────────────
    LaunchedEffect(logState.logFile) {
        val file = logState.logFile ?: return@LaunchedEffect
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val bodyText = context.getString(
            R.string.bug_report_email_body,
            android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            android.os.Build.MODEL,
            android.os.Build.VERSION.RELEASE,
            android.os.Build.VERSION.SDK_INT,
            com.potato.player.BuildConfig.VERSION_NAME,
            com.potato.player.BuildConfig.BUILD_TYPE.replaceFirstChar { it.uppercase() }
        )
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("tapman104@proton.me"))
            putExtra(
                Intent.EXTRA_SUBJECT,
                "Potato Player | Bug Report | v${com.potato.player.BuildConfig.VERSION_NAME}"
            )
            putExtra(Intent.EXTRA_TEXT, bodyText)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(emailIntent, "Send bug report"))
        } catch (e: ActivityNotFoundException) {
            snackbarHostState.showSnackbar(
                context.getString(R.string.no_app_found_to_open_link)
            )
        }
        viewModel.clearLogFile()
    }

    // ── Show snackbar on log capture error ───────────────────────────────────
    LaunchedEffect(logState.logError) {
        val err = logState.logError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            context.getString(R.string.log_dump_error, err)
        )
        viewModel.clearLogFile()
    }

    // ── Confirmation dialog: clear playback history ───────────────────────────
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.clear_playback_history)) },
            text = { Text(stringResource(R.string.clear_playback_history_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearHistoryDialog = false
                    viewModel.clearPlaybackHistory {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(historyClearedMessage)
                        }
                    }
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.category_advanced)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_desc_advanced),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // ── MPV config items (existing, kept as coming soon) ─────────────
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.configuration_location)) },
                    supportingContent = { Text(stringResource(R.string.coming_soon)) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.mpv_conf)) },
                    supportingContent = { Text(stringResource(R.string.coming_soon)) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.input_conf)) },
                    supportingContent = { Text(stringResource(R.string.coming_soon)) }
                )
            }

            // ── SECTION: HISTORY ─────────────────────────────────────────────
            item {
                Text(
                    text = stringResource(R.string.section_history),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.recently_played)) },
                    supportingContent = { Text(stringResource(R.string.recently_played_desc)) },
                    trailingContent = {
                        Switch(
                            checked = recentlyPlayedEnabled,
                            onCheckedChange = { viewModel.setRecentlyPlayedEnabled(it) }
                        )
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.clear_playback_history)) },
                    supportingContent = { Text(stringResource(R.string.clear_playback_history_desc)) },
                    modifier = Modifier.clickable {
                        showClearHistoryDialog = true
                    }
                )
            }

            // ── SECTION: CACHE ───────────────────────────────────────────────
            item {
                Text(
                    text = stringResource(R.string.section_cache),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.clear_config_cache)) },
                    supportingContent = { Text(stringResource(R.string.clear_config_cache_desc)) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.clear_thumbnail_cache)) },
                    supportingContent = { Text(stringResource(R.string.clear_thumbnail_cache_desc)) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.clear_cached_fonts)) },
                    supportingContent = { Text(stringResource(R.string.clear_cached_fonts_desc)) }
                )
            }

            // ── SECTION: LOGGING ─────────────────────────────────────────────
            item {
                Text(
                    text = stringResource(R.string.section_logging),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.verbose_logging)) },
                    supportingContent = { Text(stringResource(R.string.verbose_logging_desc)) },
                    trailingContent = {
                        Switch(
                            checked = verboseLoggingEnabled,
                            onCheckedChange = { viewModel.setVerboseLoggingEnabled(it) }
                        )
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.dump_logs)) },
                    supportingContent = { Text(stringResource(R.string.dump_logs_desc)) },
                    modifier = Modifier.clickable {
                        viewModel.dumpLogs()
                    }
                )
            }
        }
    }
}
