package com.potato.player.player.ui.topbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Top bar for the player overlay.
 *
 * Layout (left → right):
 * ```
 * [Back]  [Title ────────────────────────── ]  [Subtitle]  [Appearance*]  [AudioTrack]
 * ```
 * (* only visible when a subtitle track is active)
 *
 * - **Left section**: back button + optional title. Both are stubs — wire
 *   [onBack] to your nav controller and pass a [title] to surface the
 *   media title when available.
 * - **Right section**: [SubtitleButton], then the appearance button (shown only
 *   when [isSubtitleActive] is `true`), then [AudioTrackButton]. This order
 *   mirrors common player conventions (subs left of audio).
 *
 * The bar respects the status-bar inset via [statusBarsPadding] so it stays
 * below the system status bar in both edge-to-edge and legacy window modes.
 *
 * This composable is what [com.potato.player.player.ui.PlayerScreen] imports.
 * Individual buttons in the `topbar` package are internal to the package.
 *
 * Example wiring:
 * ```kotlin
 * var showSubtitleDialog by remember { mutableStateOf(false) }
 * var showAudioDialog    by remember { mutableStateOf(false) }
 * var showAppearanceDialog by remember { mutableStateOf(false) }
 *
 * PlayerTopBar(
 *     title                    = mediaTitle,
 *     isSubtitleActive         = currentSubtitleTrack != null,
 *     onBack                   = navController::popBackStack,
 *     onSubtitleClick          = { showSubtitleDialog = true },
 *     onSubtitleAppearanceClick = { showAppearanceDialog = true },
 *     onAudioTrackClick        = { showAudioDialog = true },
 * )
 * ```
 *
 * @param title                    Media title shown next to the back button.
 *                                 Pass `null` or empty to omit. Single line with ellipsis.
 * @param isSubtitleActive         Forwarded to [SubtitleButton] for the active indicator.
 *                                 Also controls visibility of the appearance button.
 * @param onBack                   Called when the back button is tapped.
 * @param onSubtitleClick          Called when [SubtitleButton] is tapped.
 * @param onSubtitleAppearanceClick Called when the text-size button is tapped, **or**
 *                                 when [SubtitleButton] is long-pressed. Only relevant
 *                                 when [isSubtitleActive] is `true`.
 * @param onAudioTrackClick        Called when [AudioTrackButton] is tapped.
 * @param modifier                 Applied to the outer [Row].
 * @param tint                     Icon and text colour, defaults to [Color.White].
 */
@Composable
fun PlayerTopBar(
    title: String?,
    isSubtitleActive: Boolean,
    onBack: () -> Unit,
    onSubtitleClick: () -> Unit,
    onAudioTrackClick: () -> Unit,
    onResizeModeClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        // ── Left section ──────────────────────────────────────────────────────────
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = tint,
            )
        }

        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                color = tint,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            // No title — push right-side actions to the far right.
            Spacer(modifier = Modifier.weight(1f))
        }

        // ── Right section ─────────────────────────────────────────────────────────
        SubtitleButton(
            isSubtitleActive = isSubtitleActive,
            onClick = onSubtitleClick,
            tint = tint,
        )

        IconButton(onClick = onResizeModeClick) {
            Icon(
                imageVector = Icons.Default.AspectRatio,
                contentDescription = "Resize Mode",
                tint = tint,
            )
        }

        AudioTrackButton(
            onClick = onAudioTrackClick,
            tint = tint,
        )
    }
}
