package com.potato.player.files.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onGesturesClick: () -> Unit,
    onAppearanceClick: () -> Unit,
    onSubtitleAppearanceClick: () -> Unit,
    onAboutClick: () -> Unit,
    onBack: () -> Unit
) {
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
                    title = { Text("Settings") },
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
                        headlineContent = { Text("Gestures", color = Color.White) },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color.White
                            )
                        },
                        modifier = Modifier.clickable { onGesturesClick() },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Appearance", color = Color.White) },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color.White
                            )
                        },
                        modifier = Modifier.clickable { onAppearanceClick() },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Subtitle Appearance", color = Color.White) },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.ClosedCaption,
                                contentDescription = null,
                                tint = Color.White
                            )
                        },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color.White
                            )
                        },
                        modifier = Modifier.clickable { onSubtitleAppearanceClick() },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("About", color = Color.White) },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color.White
                            )
                        },
                        modifier = Modifier.clickable { onAboutClick() },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}
