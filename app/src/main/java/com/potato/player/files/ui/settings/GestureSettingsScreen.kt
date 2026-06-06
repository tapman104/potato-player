package com.potato.player.files.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                title = { Text("Gestures") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Double-tap to seek", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = doubleTapToSeek, onCheckedChange = { doubleTapToSeek = it })
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Long-press for 2× speed", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = longPressForSpeed, onCheckedChange = { longPressForSpeed = it })
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Swipe for volume", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = swipeForVolume, onCheckedChange = { swipeForVolume = it })
            }
        }
    }
}
