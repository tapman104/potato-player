package com.potato.player.files.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.ui.SubtitleView
import com.potato.player.files.preferences.AppPreferences
import com.potato.player.player.ui.subtitle.SubtitleSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleAppearanceSettingsScreen(
    onBack: () -> Unit,
    appPreferences: AppPreferences,
) {
    val coroutineScope = rememberCoroutineScope()
    val initialSettings by appPreferences.getSubtitleSettings().collectAsState(
        initial = SubtitleSettings()
    )
    
    var draft by remember(initialSettings) { mutableStateOf(initialSettings) }

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
                    title = { Text("Subtitle Appearance") },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Size Slider
                Column {
                    Text(
                        text = "Text Size",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = draft.sizeFraction / SubtitleView.DEFAULT_TEXT_SIZE_FRACTION,
                            onValueChange = { multiplier ->
                                draft = draft.copy(
                                    sizeFraction = multiplier * SubtitleView.DEFAULT_TEXT_SIZE_FRACTION
                                )
                            },
                            onValueChangeFinished = {
                                coroutineScope.launch {
                                    appPreferences.saveSubtitleSettings(draft)
                                }
                            },
                            valueRange = 0.5f..1.8f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "%.1f×".format(
                                draft.sizeFraction / SubtitleView.DEFAULT_TEXT_SIZE_FRACTION
                            ),
                            modifier = Modifier.width(48.dp),
                            textAlign = TextAlign.End,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Position Slider
                Column {
                    Text(
                        text = "Vertical Position",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = "Higher value = closer to top",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = draft.bottomPaddingFraction,
                            onValueChange = { padding ->
                                draft = draft.copy(bottomPaddingFraction = padding)
                            },
                            onValueChangeFinished = {
                                coroutineScope.launch {
                                    appPreferences.saveSubtitleSettings(draft)
                                }
                            },
                            valueRange = 0.02f..0.40f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "%.2f".format(draft.bottomPaddingFraction),
                            modifier = Modifier.width(48.dp),
                            textAlign = TextAlign.End,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Reset Button
                TextButton(
                    onClick = {
                        val defaultSettings = SubtitleSettings(
                            sizeFraction = SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 0.90f,
                            bottomPaddingFraction = 0.12f
                        )
                        draft = defaultSettings
                        coroutineScope.launch {
                            appPreferences.saveSubtitleSettings(defaultSettings)
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Reset to defaults")
                }

                Spacer(modifier = Modifier.weight(1f))

                // Live Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color(0xFF1E1E1E)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = "Sample Subtitle Text",
                        color = Color.White,
                        fontSize = MaterialTheme.typography.titleLarge.fontSize * (draft.sizeFraction / SubtitleView.DEFAULT_TEXT_SIZE_FRACTION),
                        modifier = Modifier
                            .padding(bottom = (200 * draft.bottomPaddingFraction).dp)
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
