package com.potato.player.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potato.player.R
import com.potato.player.data.UserPreferencesRepository
import com.potato.player.feature.home.PillBarTab
import com.potato.player.feature.home.PotatoPillBar
import kotlinx.coroutines.launch

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDecoderDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showHideDelayDialog by remember { mutableStateOf(false) }
    var showSubLangDialog by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
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
        bottomBar = {
            PotatoPillBar(
                selectedTab = PillBarTab.SETTINGS,
                onFoldersClick = onNavigateToHome,
                onSettingsClick = { /* Already on Settings */ }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                Text(
                    text = "Playback",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Default decoder") },
                    supportingContent = { 
                        val label = when(uiState.defaultDecoder) {
                            "mediacodec-copy" -> "HW+ (MediaCodec Copy)"
                            "mediacodec" -> "HW (MediaCodec)"
                            "no" -> "SW (Software)"
                            else -> uiState.defaultDecoder
                        }
                        Text(label)
                    },
                    modifier = Modifier.clickable { showDecoderDialog = true }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Default speed") },
                    supportingContent = { Text("${uiState.defaultSpeed}×") },
                    modifier = Modifier.clickable { showSpeedDialog = true }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item {
                Text(
                    text = "Subtitles",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Default language") },
                    supportingContent = { 
                        val label = when(uiState.preferredSubLang) {
                            "eng" -> "English (eng)"
                            "jpn" -> "Japanese (jpn)"
                            "kor" -> "Korean (kor)"
                            "off" -> "None (off)"
                            else -> uiState.preferredSubLang
                        }
                        Text(label) 
                    },
                    modifier = Modifier.clickable { showSubLangDialog = true }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Subtitle appearance") },
                    supportingContent = { Text("Configure in player") }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item {
                Text(
                    text = "Interface",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Auto-hide delay") },
                    supportingContent = { 
                        val label = when(uiState.controlsHideDelay) {
                            2000 -> "2 seconds"
                            3000 -> "3 seconds"
                            5000 -> "5 seconds"
                            else -> "${uiState.controlsHideDelay / 1000} seconds"
                        }
                        Text(label)
                    },
                    modifier = Modifier.clickable { showHideDelayDialog = true }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Gestures") },
                    trailingContent = { 
                        androidx.compose.material3.Switch(
                            checked = uiState.gesturesEnabled,
                            onCheckedChange = { viewModel.setGesturesEnabled(it) }
                        ) 
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Lock button") },
                    trailingContent = { 
                        androidx.compose.material3.Switch(
                            checked = uiState.lockButtonEnabled,
                            onCheckedChange = { viewModel.setLockButtonEnabled(it) }
                        ) 
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Auto-rotation") },
                    trailingContent = { 
                        androidx.compose.material3.Switch(
                            checked = uiState.autoRotation,
                            onCheckedChange = { viewModel.setAutoRotation(it) }
                        ) 
                    }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about)) },
                    supportingContent = { Text("Version ${uiState.appVersion}") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToAbout)
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        }

        if (showDecoderDialog) {
            AlertDialog(
                onDismissRequest = { showDecoderDialog = false },
                title = { Text("Default decoder") },
                text = {
                    Column {
                        val options = listOf(
                            "mediacodec-copy" to "HW+ (MediaCodec Copy)",
                            "mediacodec" to "HW (MediaCodec)",
                            "no" to "SW (Software)"
                        )
                        options.forEach { (code, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (code == uiState.defaultDecoder),
                                        onClick = {
                                            viewModel.setDefaultDecoder(code)
                                            showDecoderDialog = false
                                        }
                                    )
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (code == uiState.defaultDecoder),
                                    onClick = null
                                )
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDecoderDialog = false }) { Text("Close") }
                }
            )
        }

        if (showSpeedDialog) {
            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                title = { Text("Default speed") },
                text = {
                    Column {
                        val options = listOf(
                            0.25 to "0.25×",
                            0.5 to "0.5×",
                            0.75 to "0.75×",
                            1.0 to "1.0× (Normal)",
                            1.25 to "1.25×",
                            1.5 to "1.5×",
                            1.75 to "1.75×",
                            2.0 to "2.0×"
                        )
                        options.forEach { (value, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (value == uiState.defaultSpeed),
                                        onClick = {
                                            viewModel.setDefaultSpeed(value)
                                            showSpeedDialog = false
                                        }
                                    )
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (value == uiState.defaultSpeed),
                                    onClick = null
                                )
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSpeedDialog = false }) { Text("Close") }
                }
            )
        }

        if (showHideDelayDialog) {
            AlertDialog(
                onDismissRequest = { showHideDelayDialog = false },
                title = { Text("Auto-hide delay") },
                text = {
                    Column {
                        val options = listOf(
                            2000 to "2 seconds",
                            3000 to "3 seconds",
                            5000 to "5 seconds"
                        )
                        options.forEach { (value, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (value == uiState.controlsHideDelay),
                                        onClick = {
                                            viewModel.setControlsHideDelay(value)
                                            showHideDelayDialog = false
                                        }
                                    )
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (value == uiState.controlsHideDelay),
                                    onClick = null
                                )
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHideDelayDialog = false }) { Text("Close") }
                }
            )
        }

        if (showSubLangDialog) {
            AlertDialog(
                onDismissRequest = { showSubLangDialog = false },
                title = { Text("Default subtitle language") },
                text = {
                    Column {
                        val options = listOf("eng" to "English (eng)", "jpn" to "Japanese (jpn)", "kor" to "Korean (kor)", "off" to "None (off)")
                        options.forEach { (code, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (code == uiState.preferredSubLang),
                                        onClick = {
                                            viewModel.setPreferredSubLang(code)
                                            showSubLangDialog = false
                                        }
                                    )
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (code == uiState.preferredSubLang),
                                    onClick = null // handled by row
                                )
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubLangDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
