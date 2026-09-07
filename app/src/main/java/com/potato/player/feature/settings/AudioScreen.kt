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
fun AudioScreen(
    onBack: () -> Unit,
    viewModel: AudioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showLangDialog     by remember { mutableStateOf(false) }
    var showChannelsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.category_audio)) },
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
            // ── Language ───────────────────────────────────────────────────────
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
                    headlineContent = { Text(stringResource(R.string.preferred_audio_language)) },
                    supportingContent = {
                        val label = when (uiState.preferredAudioLang) {
                            "eng" -> stringResource(R.string.lang_english)
                            "jpn" -> stringResource(R.string.lang_japanese)
                            "kor" -> stringResource(R.string.lang_korean)
                            "off" -> stringResource(R.string.lang_none)
                            else  -> uiState.preferredAudioLang
                        }
                        Text(label)
                    },
                    modifier = Modifier.clickable { showLangDialog = true }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // ── Output ─────────────────────────────────────────────────────────
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
                    headlineContent = { Text(stringResource(R.string.audio_channels)) },
                    supportingContent = {
                        val label = when (uiState.audioChannels) {
                            "auto"     -> stringResource(R.string.channels_auto)
                            "stereo"   -> stringResource(R.string.channels_stereo)
                            "mono"     -> stringResource(R.string.channels_mono)
                            "surround" -> stringResource(R.string.channels_surround)
                            else       -> uiState.audioChannels
                        }
                        Text(label)
                    },
                    modifier = Modifier.clickable { showChannelsDialog = true }
                )
            }
        }

        if (showLangDialog) {
            AlertDialog(
                onDismissRequest = { showLangDialog = false },
                title = { Text(stringResource(R.string.preferred_audio_language)) },
                text = {
                    Column {
                        val options = listOf(
                            "eng" to stringResource(R.string.lang_english),
                            "jpn" to stringResource(R.string.lang_japanese),
                            "kor" to stringResource(R.string.lang_korean),
                            "off" to stringResource(R.string.lang_none)
                        )
                        options.forEach { (code, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (code == uiState.preferredAudioLang),
                                        onClick = {
                                            viewModel.setPreferredAudioLang(code)
                                            showLangDialog = false
                                        }
                                    )
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (code == uiState.preferredAudioLang),
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
                    TextButton(onClick = { showLangDialog = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }

        if (showChannelsDialog) {
            AlertDialog(
                onDismissRequest = { showChannelsDialog = false },
                title = { Text(stringResource(R.string.audio_channels)) },
                text = {
                    Column {
                        val options = listOf(
                            "auto"     to stringResource(R.string.channels_auto),
                            "stereo"   to stringResource(R.string.channels_stereo),
                            "mono"     to stringResource(R.string.channels_mono),
                            "surround" to stringResource(R.string.channels_surround)
                        )
                        options.forEach { (code, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = (code == uiState.audioChannels),
                                        onClick = {
                                            viewModel.setAudioChannels(code)
                                            showChannelsDialog = false
                                        }
                                    )
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (code == uiState.audioChannels),
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
                    TextButton(onClick = { showChannelsDialog = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }
    }
}
