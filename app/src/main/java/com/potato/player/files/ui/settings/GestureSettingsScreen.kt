package com.potato.player.files.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureSettingsScreen(onBack: () -> Unit) {
    var doubleTapToSeek by remember { mutableStateOf(true) }
    var longPressForSpeed by remember { mutableStateOf(true) }
    var swipeForVolume by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Gestures", 
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(vertical = 12.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column {
                    ListItem(
                        headlineContent = { 
                            Text("Double-tap to seek", fontWeight = FontWeight.SemiBold) 
                        },
                        supportingContent = { 
                            Text("Double-tap left/right edge to seek 10s") 
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.TouchApp, 
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(checked = doubleTapToSeek, onCheckedChange = { doubleTapToSeek = it })
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    ListItem(
                        headlineContent = { 
                            Text("Long-press for 2× speed", fontWeight = FontWeight.SemiBold) 
                        },
                        supportingContent = { 
                            Text("Hold anywhere on player to temporarily speed up") 
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Speed, 
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(checked = longPressForSpeed, onCheckedChange = { longPressForSpeed = it })
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    ListItem(
                        headlineContent = { 
                            Text("Swipe gestures", fontWeight = FontWeight.SemiBold) 
                        },
                        supportingContent = { 
                            Text("Swipe vertically for volume and brightness") 
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.VolumeUp, 
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(checked = swipeForVolume, onCheckedChange = { swipeForVolume = it })
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}

