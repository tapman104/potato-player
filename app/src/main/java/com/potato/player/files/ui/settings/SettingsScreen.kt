package com.potato.player.files.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onGesturesClick: () -> Unit,
    onPlaybackClick: () -> Unit,
    onAppearanceClick: () -> Unit,
    onSubtitleAppearanceClick: () -> Unit,
    onHomeScreenClick: () -> Unit,
    onMiscClick: () -> Unit,
    onAboutClick: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Settings", 
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back"
                        )
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
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
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
                        SettingMenuItem(
                            title = "Gestures",
                            subtitle = "Configure seek, volume, and zoom inputs",
                            icon = Icons.Outlined.TouchApp,
                            badgeColor = Color(0xFFFF9800),
                            onClick = onGesturesClick
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                        SettingMenuItem(
                            title = "Home Screen",
                            subtitle = "Manage video file display and picker",
                            icon = Icons.Outlined.Home,
                            badgeColor = Color(0xFFE91E63),
                            onClick = onHomeScreenClick
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                        SettingMenuItem(
                            title = "Playback",
                            subtitle = "Resume behavior, speed settings, and controls",
                            icon = Icons.Outlined.PlayCircle,
                            badgeColor = Color(0xFF2196F3),
                            onClick = onPlaybackClick
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                        SettingMenuItem(
                            title = "Appearance",
                            subtitle = "Dark theme modes and orientation behaviors",
                            icon = Icons.Outlined.Palette,
                            badgeColor = Color(0xFF9C27B0),
                            onClick = onAppearanceClick
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                        SettingMenuItem(
                            title = "Subtitle Appearance",
                            subtitle = "Text size, visual position, and styling",
                            icon = Icons.Outlined.ClosedCaption,
                            badgeColor = Color(0xFF009688),
                            onClick = onSubtitleAppearanceClick
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                        SettingMenuItem(
                            title = "Miscellaneous",
                            subtitle = "Experimental and other settings",
                            icon = Icons.Outlined.Build,
                            badgeColor = Color(0xFF795548),
                            onClick = onMiscClick
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                        SettingMenuItem(
                            title = "About",
                            subtitle = "Version, developer link, and licenses",
                            icon = Icons.Outlined.Info,
                            badgeColor = Color(0xFF607D8B),
                            onClick = onAboutClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingIconBadge(icon: ImageVector, backgroundColor: Color, tint: Color = Color.White) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SettingMenuItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeColor: Color,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            SettingIconBadge(icon = icon, backgroundColor = badgeColor)
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        },
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

