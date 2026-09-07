package com.potato.player.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.potato.player.R
import com.potato.player.feature.home.PillBarTab
import com.potato.player.feature.home.PotatoPillBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToDecoder: () -> Unit,
    onNavigateToSubtitles: () -> Unit,
    onNavigateToAudio: () -> Unit,
    onNavigateToAdvanced: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
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
                ListItem(
                    headlineContent = { Text(stringResource(R.string.category_appearance)) },
                    supportingContent = { Text(stringResource(R.string.settings_desc_appearance)) },
                    leadingContent = {
                        Icon(Icons.Default.Palette, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToAppearance)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.category_player)) },
                    supportingContent = { Text(stringResource(R.string.settings_desc_player)) },
                    leadingContent = {
                        Icon(Icons.Default.PlayCircle, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToPlayer)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.category_decoder)) },
                    supportingContent = { Text(stringResource(R.string.settings_desc_decoder)) },
                    leadingContent = {
                        Icon(Icons.Default.Memory, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToDecoder)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.category_subtitles)) },
                    supportingContent = { Text(stringResource(R.string.settings_desc_subtitles)) },
                    leadingContent = {
                        Icon(Icons.Default.Subtitles, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToSubtitles)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.category_audio)) },
                    supportingContent = { Text(stringResource(R.string.settings_desc_audio)) },
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToAudio)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.category_advanced)) },
                    supportingContent = { Text(stringResource(R.string.settings_desc_advanced)) },
                    leadingContent = {
                        Icon(Icons.Default.Code, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToAdvanced)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about)) },
                    supportingContent = { Text(stringResource(R.string.section_about)) },
                    leadingContent = {
                        Icon(Icons.Default.Info, contentDescription = null)
                    },
                    modifier = Modifier.clickable(onClick = onNavigateToAbout)
                )
            }
        }
    }
}
