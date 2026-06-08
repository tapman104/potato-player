package com.potato.player.files.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.potato.player.files.preferences.AppPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiscSettingsScreen(
    onBack: () -> Unit,
    appPreferences: AppPreferences
) {
    val enablePlaybackService by appPreferences.enablePlaybackService.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Miscellaneous", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                ListItem(
                    headlineContent = { Text("Enable Playback Service") },
                    supportingContent = { Text("Use a background service for media playback (Experimental)") },
                    trailingContent = {
                        Switch(
                            checked = enablePlaybackService,
                            onCheckedChange = { appPreferences.setEnablePlaybackService(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
            }
        }
    }
}
