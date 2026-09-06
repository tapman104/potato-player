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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potato.player.R
import com.potato.player.data.UserPreferencesRepository
import com.potato.player.feature.home.PillBarTab
import com.potato.player.feature.home.PotatoPillBar

import androidx.hilt.navigation.compose.hiltViewModel

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
    var showOrientationDialog by remember { mutableStateOf(false) }
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
                    text = stringResource(R.string.section_playback),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.default_decoder)) },
                    supportingContent = { 
                        val label = when(uiState.defaultDecoder) {
                            "mediacodec-copy" -> stringResource(R.string.decoder_hw_plus)
                            "mediacodec" -> stringResource(R.string.decoder_hw)
                            "no" -> stringResource(R.string.decoder_sw)
                            else -> uiState.defaultDecoder
                        }
                        Text(label)
                    },
                    modifier = Modifier.clickable { showDecoderDialog = true }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.default_speed)) },
                    supportingContent = { Text("${uiState.defaultSpeed}×") },
                    modifier = Modifier.clickable { showSpeedDialog = true }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item {
                Text(
                    text = stringResource(R.string.section_subtitles),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.default_language)) },
                    supportingContent = { 
                        val label = when(uiState.preferredSubLang) {
                            "eng" -> stringResource(R.string.lang_english)
                            "jpn" -> stringResource(R.string.lang_japanese)
                            "kor" -> stringResource(R.string.lang_korean)
                            "off" -> stringResource(R.string.lang_none)
                            else -> uiState.preferredSubLang
                        }
                        Text(label) 
                    },
                    modifier = Modifier.clickable { showSubLangDialog = true }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.subtitle_appearance)) },
                    supportingContent = { Text(stringResource(R.string.configure_in_player)) }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item {
                Text(
                    text = stringResource(R.string.section_interface),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.auto_hide_delay)) },
                    supportingContent = { 
                        val label = when(uiState.controlsHideDelay) {
                            2000 -> stringResource(R.string.delay_2s)
                            3000 -> stringResource(R.string.delay_3s)
                            5000 -> stringResource(R.string.delay_5s)
                            else -> "${uiState.controlsHideDelay / 1000} seconds"
                        }
                        Text(label)
                    },
                    modifier = Modifier.clickable { showHideDelayDialog = true }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.gestures)) },
                    trailingContent = { 
                        androidx.compose.material3.Switch(
                            checked = uiState.gesturesEnabled,
                            onCheckedChange = null
                        ) 
                    },
                    modifier = Modifier.clickable { viewModel.setGesturesEnabled(!uiState.gesturesEnabled) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.lock_button)) },
                    trailingContent = { 
                        androidx.compose.material3.Switch(
                            checked = uiState.lockButtonEnabled,
                            onCheckedChange = null
                        ) 
                    },
                    modifier = Modifier.clickable { viewModel.setLockButtonEnabled(!uiState.lockButtonEnabled) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.video_orientation)) },
                    supportingContent = { 
                        val label = when(uiState.videoOrientation) {
                            "auto" -> stringResource(R.string.orientation_auto)
                            "landscape" -> stringResource(R.string.orientation_landscape)
                            "portrait" -> stringResource(R.string.orientation_portrait)
                            "sensor" -> stringResource(R.string.orientation_sensor)
                            "sensor_landscape" -> stringResource(R.string.orientation_sensor_land)
                            "sensor_portrait" -> stringResource(R.string.orientation_sensor_port)
                            "locked" -> stringResource(R.string.orientation_locked)
                            else -> uiState.videoOrientation
                        }
                        Text(label)
                    },
                    modifier = Modifier.clickable { showOrientationDialog = true }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item {
                Text(
                    text = stringResource(R.string.section_about),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about)) },
                    supportingContent = { Text(stringResource(R.string.app_version_format, uiState.appVersion)) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onNavigateToAbout)
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        }

        if (showDecoderDialog) {
            AlertDialog(
                onDismissRequest = { showDecoderDialog = false },
                title = { Text(stringResource(R.string.dialog_default_decoder)) },
                text = {
                    Column {
                        val options = listOf(
                            "mediacodec-copy" to stringResource(R.string.decoder_hw_plus),
                            "mediacodec" to stringResource(R.string.decoder_hw),
                            "no" to stringResource(R.string.decoder_sw)
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
                    TextButton(onClick = { showDecoderDialog = false }) { Text(stringResource(R.string.close)) }
                }
            )
        }

        if (showSpeedDialog) {
            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                title = { Text(stringResource(R.string.dialog_default_speed)) },
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
                    TextButton(onClick = { showSpeedDialog = false }) { Text(stringResource(R.string.close)) }
                }
            )
        }

        if (showHideDelayDialog) {
            AlertDialog(
                onDismissRequest = { showHideDelayDialog = false },
                title = { Text(stringResource(R.string.dialog_auto_hide_delay)) },
                text = {
                    Column {
                        val options = listOf(
                            2000 to stringResource(R.string.delay_2s),
                            3000 to stringResource(R.string.delay_3s),
                            5000 to stringResource(R.string.delay_5s)
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
                    TextButton(onClick = { showHideDelayDialog = false }) { Text(stringResource(R.string.close)) }
                }
            )
        }

        if (showSubLangDialog) {
            AlertDialog(
                onDismissRequest = { showSubLangDialog = false },
                title = { Text(stringResource(R.string.dialog_sub_lang)) },
                text = {
                    Column {
                        val options = listOf("eng" to stringResource(R.string.lang_english), "jpn" to stringResource(R.string.lang_japanese), "kor" to stringResource(R.string.lang_korean), "off" to stringResource(R.string.lang_none))
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
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }

        if (showOrientationDialog) {
            AlertDialog(
                onDismissRequest = { showOrientationDialog = false },
                title = { Text(stringResource(R.string.dialog_video_orientation)) },
                text = {
                    Column {
                        val options = listOf(
                            "auto" to stringResource(R.string.orientation_auto),
                            "landscape" to stringResource(R.string.orientation_landscape),
                            "portrait" to stringResource(R.string.orientation_portrait),
                            "sensor" to stringResource(R.string.orientation_sensor),
                            "sensor_landscape" to stringResource(R.string.orientation_sensor_land),
                            "sensor_portrait" to stringResource(R.string.orientation_sensor_port),
                            "locked" to stringResource(R.string.orientation_locked)
                        )
                        options.forEach { (code, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (code == uiState.videoOrientation),
                                        onClick = {
                                            viewModel.setVideoOrientation(code)
                                            showOrientationDialog = false
                                        }
                                    )
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (code == uiState.videoOrientation),
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
                    TextButton(onClick = { showOrientationDialog = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }
    }
}
