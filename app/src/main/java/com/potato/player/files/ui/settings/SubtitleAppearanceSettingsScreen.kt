package com.potato.player.files.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Subtitle Appearance", 
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
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Live Preview Card
            Text(
                text = "Live Preview",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0F2027),
                                Color(0xFF203A43),
                                Color(0xFF2C5364)
                            )
                        )
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Play button icon overlay representing paused video content
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                )

                // Mock video progress bar overlay at the bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("01:23", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .padding(horizontal = 8.dp)
                                .background(Color.White.copy(alpha = 0.3f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.4f)
                                    .background(Color.White.copy(alpha = 0.9f))
                            )
                        }
                        Text("03:45", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Sample subtitle text reacting to parameters
                Text(
                    text = "Sample Subtitle Text",
                    color = Color.White,
                    fontSize = MaterialTheme.typography.titleLarge.fontSize * (draft.sizeFraction / SubtitleView.DEFAULT_TEXT_SIZE_FRACTION),
                    modifier = Modifier
                        .padding(bottom = (200 * draft.bottomPaddingFraction + 12).dp) // Offset above progress bar
                        .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Sliders Control Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Size Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Text Size",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "%.1f×".format(
                                    draft.sizeFraction / SubtitleView.DEFAULT_TEXT_SIZE_FRACTION
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
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
                            valueRange = 0.5f..1.8f
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Position Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Vertical Position",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Higher value = closer to top",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "%.2f".format(draft.bottomPaddingFraction),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
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
                            valueRange = 0.02f..0.40f
                        )
                    }
                }
            }

            // Reset Button styled nicely
            OutlinedButton(
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
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset to defaults")
            }
        }
    }
}

