package com.potato.player.files.ui.settings

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
fun AppearanceSettingsScreen(onBack: () -> Unit, appPreferences: AppPreferences) {
    val themeSelection by appPreferences.themeSelection.collectAsState()
    val themeOptions = listOf("Light", "Dark", "System")
    val dynamicColor by appPreferences.dynamicColor.collectAsState()
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Appearance") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                item {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                    )
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        themeOptions.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = themeSelection == index,
                                onClick = { appPreferences.setThemeSelection(index) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = themeOptions.size)
                            ) {
                                Text(option)
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    ListItem(
                        headlineContent = { Text("Dynamic Color") },
                        supportingContent = { Text("Uses wallpaper colors on Android 12+") },
                        trailingContent = {
                            Switch(checked = dynamicColor, onCheckedChange = { appPreferences.setDynamicColor(it) })
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
}
