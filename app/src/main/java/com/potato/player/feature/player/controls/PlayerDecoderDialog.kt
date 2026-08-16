package com.potato.player.feature.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DecoderOption(
    val title: String,
    val badge: String,
    val summary: String,
    val description: String,
    val mpvValue: String
)

private val decoderOptions = listOf(
    DecoderOption(
        title = "Hardware+ (HW+)",
        badge = "HW+",
        summary = "Recommended auto-copy mode.",
        description = "Supports video filters and shaders while keeping high hardware efficiency.",
        mpvValue = "mediacodec,mediacodec-copy,no"
    ),
    DecoderOption(
        title = "Hardware Direct (HW)",
        badge = "HW",
        summary = "Direct hardware decoding.",
        description = "Maximum playback speed and lowest battery consumption.",
        mpvValue = "mediacodec"
    ),
    DecoderOption(
        title = "Software (SW)",
        badge = "SW",
        summary = "CPU-based software decoding.",
        description = "Highest compatibility for rare or complex codecs.",
        mpvValue = "no"
    )
)

@Composable
fun PlayerDecoderDialog(
    visible: Boolean,
    currentDecoder: String,
    onSelectDecoder: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val expandedStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            val selectedOption = decoderOptions.find { it.badge == currentDecoder }
            if (selectedOption != null) {
                put(selectedOption.mpvValue, true)
            }
        }
    }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 0.45f else 0f,
        label = "scrimAlpha"
    )

    val transitionState = remember { androidx.compose.animation.core.MutableTransitionState(visible) }
    transitionState.targetState = visible

    if (transitionState.currentState || transitionState.targetState || scrimAlpha > 0f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .pointerInput(visible || scrimAlpha > 0f) {
                    detectTapGestures(onTap = { _ -> onDismiss() })
                }
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                    color = Color(0xFF1E1E1E).copy(alpha = 0.90f),
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(380.dp)
                        .pointerInput(Unit) {
                            detectTapGestures {}
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                // Header — never scrolls
                Text(
                    text = "Select Video Decoder",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Choose the hardware or software decoding engine used by MPV.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Options list — scrollable but no scrollbar shown
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)          // caps height on small screens
                        .verticalScroll(rememberScrollState()) // hidden scrollbar by default
                ) {
                    decoderOptions.forEach { option ->
                        val isSelected = currentDecoder == option.badge
                        val isExpanded = expandedStates[option.mpvValue] == true

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) Color.White.copy(alpha = 0.15f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    onSelectDecoder(option.mpvValue)
                                    onDismiss()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSelectDecoder(option.mpvValue)
                                    onDismiss()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color.White,
                                    unselectedColor = Color.White.copy(alpha = 0.5f)
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.title,
                                    color = Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = option.summary,
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                AnimatedVisibility(visible = isExpanded) {
                                    Column {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = option.description,
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { expandedStates[option.mpvValue] = !isExpanded },
                                modifier = PlayerControlsStyles.dialogIconButtonModifier
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle description",
                                    tint = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Footer — never scrolls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "CLOSE",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
        }
    }
}