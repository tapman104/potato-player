package com.potato.player.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potato.player.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSpeedDialog     by remember { mutableStateOf(false) }
    var showHideDelayDialog by remember { mutableStateOf(false) }
    var showOrientationDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.category_player)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Playback ───────────────────────────────────────────────────────
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
                    headlineContent = { Text(stringResource(R.string.default_speed)) },
                    supportingContent = { Text("${uiState.defaultSpeed}×") },
                    modifier = Modifier.clickable { showSpeedDialog = true }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // ── Controls ───────────────────────────────────────────────────────
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
                        val label = when (uiState.controlsHideDelay) {
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
                        Switch(
                            checked = uiState.gesturesEnabled,
                            onCheckedChange = null
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.setGesturesEnabled(!uiState.gesturesEnabled)
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.lock_button)) },
                    trailingContent = {
                        Switch(
                            checked = uiState.lockButtonEnabled,
                            onCheckedChange = null
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.setLockButtonEnabled(!uiState.lockButtonEnabled)
                    }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // ── Orientation ────────────────────────────────────────────────────
            item {
                Text(
                    text = stringResource(R.string.section_orientation),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.video_orientation)) },
                    supportingContent = {
                        val label = when (uiState.videoOrientation) {
                            "auto"             -> stringResource(R.string.orientation_auto)
                            "landscape"        -> stringResource(R.string.orientation_landscape)
                            "portrait"         -> stringResource(R.string.orientation_portrait)
                            "sensor"           -> stringResource(R.string.orientation_sensor)
                            "sensor_landscape" -> stringResource(R.string.orientation_sensor_land)
                            "sensor_portrait"  -> stringResource(R.string.orientation_sensor_port)
                            "locked"           -> stringResource(R.string.orientation_locked)
                            else               -> uiState.videoOrientation
                        }
                        Text(label)
                    },
                    modifier = Modifier.clickable { showOrientationDialog = true }
                )
            }
        }

        if (showSpeedDialog) {
            AlertDialog(
                onDismissRequest = { showSpeedDialog = false },
                title = { Text(stringResource(R.string.dialog_default_speed)) },
                text = {
                    Column {
                        val options = listOf(
                            0.25 to "0.25×",
                            0.5  to "0.5×",
                            0.75 to "0.75×",
                            1.0  to "1.0× (Normal)",
                            1.25 to "1.25×",
                            1.5  to "1.5×",
                            1.75 to "1.75×",
                            2.0  to "2.0×"
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
                    TextButton(onClick = { showSpeedDialog = false }) {
                        Text(stringResource(R.string.close))
                    }
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
                    TextButton(onClick = { showHideDelayDialog = false }) {
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
                            "auto"             to stringResource(R.string.orientation_auto),
                            "landscape"        to stringResource(R.string.orientation_landscape),
                            "portrait"         to stringResource(R.string.orientation_portrait),
                            "sensor"           to stringResource(R.string.orientation_sensor),
                            "sensor_landscape" to stringResource(R.string.orientation_sensor_land),
                            "sensor_portrait"  to stringResource(R.string.orientation_sensor_port),
                            "locked"           to stringResource(R.string.orientation_locked)
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
