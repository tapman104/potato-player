package com.potato.player.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// COLORS — hardcoded to match AMOLED theme, no theme dependency needed
private val PillBackground = Color(0xE81C1C1E)   // ~91% opaque dark grey
private val PillBorder     = Color(0x1AFFFFFF)   // 10% white border
private val IconActive     = Color(0xFFFFFFFF)   // full white
private val IconInactive   = Color(0x66FFFFFF)   // 40% white
private val ItemActive     = Color(0x1AFFFFFF)   // subtle white wash on active item
private val DotColor       = Color(0xFFFFFFFF)   // white dot

enum class PillBarTab { FOLDERS, SETTINGS }

@Composable
fun PotatoPillBar(
    selectedTab: PillBarTab,
    onFoldersClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(50.dp),
            color = PillBackground,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, PillBorder),
            modifier = Modifier  // wrap content width, not full width
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillBarItem(
                    icon = Icons.Rounded.Folder,
                    contentDescription = "Folders",
                    isSelected = selectedTab == PillBarTab.FOLDERS,
                    onClick = onFoldersClick
                )
                PillBarItem(
                    icon = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    isSelected = selectedTab == PillBarTab.SETTINGS,
                    onClick = onSettingsClick
                )
            }
        }
    }
}

@Composable
private fun PillBarItem(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val iconTint by animateColorAsState(
        targetValue = if (isSelected) IconActive else IconInactive,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "iconTint"
    )
    val itemBg by animateColorAsState(
        targetValue = if (isSelected) ItemActive else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "itemBg"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 58.dp, height = 51.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(itemBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // Icon
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.Center)
                // nudge icon up slightly to leave room for dot
                .padding(bottom = 6.dp)
        )

        // Active dot indicator
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(DotColor)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp) // shift the dot up slightly so it fits inside the Box
            )
        }
    }
}
