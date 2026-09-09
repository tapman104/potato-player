package com.potato.player.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val recentlyPlayedEnabled by viewModel.recentlyPlayedEnabled.collectAsState()
    val verboseLoggingEnabled by viewModel.verboseLoggingEnabled.collectAsState()

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showVerboseWarningDialog by remember { mutableStateOf(false) }

    val historyClearedMessage = stringResource(R.string.history_cleared)

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

    // ── Confirmation dialog: enable verbose logging ───────────────────────────
    if (showVerboseWarningDialog) {
        AlertDialog(
            onDismissRequest = { showVerboseWarningDialog = false },
            title = { Text(stringResource(R.string.verbose_logging)) },
            text = { Text(stringResource(R.string.verbose_logging_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    showVerboseWarningDialog = false
                    viewModel.setVerboseLoggingEnabled(true)
                }) {
                    Text(stringResource(R.string.enable))
                }
            },
            dismissButton = {
                TextButton(onClick = { showVerboseWarningDialog = false }) {
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── SECTION: HISTORY ─────────────────────────────────────────────
            item {
                Text(
                    text = stringResource(R.string.section_history),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column {
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
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.clear_playback_history),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            supportingContent = { Text(stringResource(R.string.clear_playback_history_desc)) },
                            modifier = Modifier.clickable {
                                showClearHistoryDialog = true
                            }
                        )
                    }
                }
            }

            // ── SECTION: CACHE ───────────────────────────────────────────────
            item {
                Text(
                    text = stringResource(R.string.section_cache),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.clear_config_cache)) },
                            supportingContent = { Text(stringResource(R.string.clear_config_cache_desc)) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.clear_thumbnail_cache)) },
                            supportingContent = { Text(stringResource(R.string.clear_thumbnail_cache_desc)) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.clear_cached_fonts)) },
                            supportingContent = { Text(stringResource(R.string.clear_cached_fonts_desc)) }
                        )
                    }
                }
            }

            // ── SECTION: LOGGING ─────────────────────────────────────────────
            item {
                Text(
                    text = stringResource(R.string.section_logging),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                )
            }
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.verbose_logging)) },
                        supportingContent = { Text(stringResource(R.string.verbose_logging_desc)) },
                        trailingContent = {
                            Switch(
                                checked = verboseLoggingEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        // Show warning before enabling; the dialog commits the save
                                        showVerboseWarningDialog = true
                                    } else {
                                        viewModel.setVerboseLoggingEnabled(false)
                                    }
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

