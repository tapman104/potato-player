package com.potato.player.files.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.potato.player.files.preferences.AppPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsScreen(
    onBack: () -> Unit,
    appPreferences: AppPreferences
) {
    val resumePlayback by appPreferences.resumePlayback.collectAsState()
    val defaultSpeed by appPreferences.defaultPlaybackSpeed.collectAsState()
    val backgroundPlayback by appPreferences.backgroundPlayback.collectAsState()
    val autoPlayNext by appPreferences.autoPlayNext.collectAsState()

    var showSpeedDialog by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color.White
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Playback") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF121212),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                item {
                    ListItem(
                        headlineContent = { Text("Resume Playback", color = Color.White) },
                        supportingContent = { Text("Remember the last playback position", color = Color.Gray) },
                        trailingContent = {
                            Switch(
                                checked = resumePlayback,
                                onCheckedChange = { appPreferences.setResumePlayback(it) }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Default Playback Speed", color = Color.White) },
                        supportingContent = { Text("${defaultSpeed}x", color = Color.Gray) },
                        modifier = Modifier.clickable { showSpeedDialog = true },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Background Playback", color = Color.White) },
                        supportingContent = { Text("Continue playing audio when app is minimized", color = Color.Gray) },
                        trailingContent = {
                            Switch(
                                checked = backgroundPlayback,
                                onCheckedChange = { appPreferences.setBackgroundPlayback(it) }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Auto-Play Next", color = Color.White) },
                        supportingContent = { Text("Automatically play the next video in folder", color = Color.Gray) },
                        trailingContent = {
                            Switch(
                                checked = autoPlayNext,
                                onCheckedChange = { appPreferences.setAutoPlayNext(it) }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            if (showSpeedDialog) {
                AlertDialog(
                    onDismissRequest = { showSpeedDialog = false },
                    title = { Text("Default Playback Speed") },
                    text = {
                        Column {
                            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                            speeds.forEach { speed ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            appPreferences.setDefaultPlaybackSpeed(speed)
                                            showSpeedDialog = false
                                        }
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${speed}x", color = Color.White)
                                    if (defaultSpeed == speed) {
                                        RadioButton(selected = true, onClick = null)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSpeedDialog = false }) {
                            Text("Close")
                        }
                    },
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White,
                    textContentColor = Color.White
                )
            }
        }
    }
}
