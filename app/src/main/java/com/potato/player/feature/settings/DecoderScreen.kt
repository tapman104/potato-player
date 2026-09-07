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
fun DecoderScreen(
    onBack: () -> Unit,
    viewModel: DecoderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDecoderDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.category_decoder)) },
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
            // ── Hardware decoding ──────────────────────────────────────────────
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
                        val label = when (uiState.defaultDecoder) {
                            "mediacodec-copy" -> stringResource(R.string.decoder_hw_plus)
                            "mediacodec"      -> stringResource(R.string.decoder_hw)
                            "no"              -> stringResource(R.string.decoder_sw)
                            else              -> uiState.defaultDecoder
                        }
                        Text(label)
                    },
                    modifier = Modifier.clickable { showDecoderDialog = true }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // ── Video processing ───────────────────────────────────────────────
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
                    headlineContent = { Text(stringResource(R.string.pixel_format)) },
                    supportingContent = { Text(stringResource(R.string.coming_soon)) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.debanding)) },
                    supportingContent = { Text(stringResource(R.string.coming_soon)) }
                )
            }
        }

        if (showDecoderDialog) {
            AlertDialog(
                onDismissRequest = { showDecoderDialog = false },
                title = { Text(stringResource(R.string.dialog_default_decoder)) },
                text = {
                    Column {
                        val options = listOf(
                            "mediacodec-copy" to stringResource(R.string.decoder_hw_plus),
                            "mediacodec"      to stringResource(R.string.decoder_hw),
                            "no"              to stringResource(R.string.decoder_sw)
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
                    TextButton(onClick = { showDecoderDialog = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }
    }
}
