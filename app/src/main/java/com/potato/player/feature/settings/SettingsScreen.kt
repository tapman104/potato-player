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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    val prefsRepository = remember(context) { UserPreferencesRepository(context) }
    val preferredSubLang by prefsRepository.preferredSubLangFlow.collectAsStateWithLifecycle(initialValue = "eng")
    var showSubLangDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
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
                    text = "Subtitles",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Default subtitle language") },
                    supportingContent = { 
                        val label = when(preferredSubLang) {
                            "eng" -> "English (eng)"
                            "jpn" -> "Japanese (jpn)"
                            "kor" -> "Korean (kor)"
                            "off" -> "None (off)"
                            else -> preferredSubLang
                        }
                        Text(label) 
                    },
                    leadingContent = { Icon(Icons.Default.Subtitles, contentDescription = null) },
                    modifier = Modifier.clickable { showSubLangDialog = true }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item {
                Text(
                    text = stringResource(R.string.about),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.app_name))
                    },
                    supportingContent = {
                        Text("Potato Player")
                    },
                    leadingContent = {
                        Icon(Icons.Default.Movie, contentDescription = null)
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.app_version_label))
                    },
                    supportingContent = {
                        Text("1.0.0")
                    },
                    leadingContent = {
                        Icon(Icons.Default.Info, contentDescription = null)
                    }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
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
                                        selected = (code == preferredSubLang),
                                        onClick = {
                                            coroutineScope.launch { prefsRepository.setPreferredSubLang(code) }
                                            showSubLangDialog = false
                                        }
                                    )
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (code == preferredSubLang),
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
