package com.potato.player.feature.settings

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.foundation.clickable
import com.potato.player.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    viewModel: AppearanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showOrientationDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.category_appearance)) },
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
