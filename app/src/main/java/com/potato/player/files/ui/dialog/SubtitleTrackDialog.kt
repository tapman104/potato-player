package com.potato.player.player.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mediaengine.SubtitleTrack

/**
 * A modal dialog that presents the list of available subtitle tracks and
 * lets the user pick one or turn subtitles off entirely.
 *
 * Follows the **pure data-in / intent-out** pattern: all state is passed in
 * as parameters and selections are communicated back through lambdas. The
 * composable holds no ViewModel reference and carries no business logic â€”
 * it is a pure function of its inputs.
 *
 * An **"Off"** option is always prepended to the list so the user can
 * disable subtitles without needing a separate button. Selecting "Off" calls
 * [onDisable] instead of [onSelect].
 *
 * Typical usage from a caller that owns the state:
 * ```kotlin
 * if (showSubtitleDialog) {
 *     SubtitleTrackDialog(
 *         tracks     = trackState.subtitleTracks,
 *         selectedId = trackState.selectedSubtitleTrackId,
 *         onSelect   = { id -> viewModel.selectSubtitleTrack(id) },
 *         onDisable  = { viewModel.clearSubtitleTrack() },
 *         onDismiss  = { showSubtitleDialog = false },
 *     )
 * }
 * ```
 *
 * @param tracks     The ordered list of subtitle tracks to display. An empty
 *                   list still shows the "Off" option so the user can confirm
 *                   the disabled state.
 * @param selectedId The [SubtitleTrack.id] of the currently active track, or
 *                   `null` when subtitles are disabled (which corresponds to
 *                   the "Off" row being selected).
 * @param onSelect   Called with the chosen [SubtitleTrack.id] when the user
 *                   taps a track row. The dialog does **not** dismiss itself;
 *                   the caller must update state and/or call [onDismiss].
 * @param onDisable  Called when the user taps the "Off" row, signalling that
 *                   subtitles should be cleared/disabled.
 * @param onDismiss  Called when the user taps outside the dialog or the
 *                   "Cancel" button.
 * @param onAppearanceClick Called when the "Appearance" button is tapped.
 */
@Composable
fun SubtitleTrackDialog(
    tracks: List<SubtitleTrack>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit,
    onAppearanceClick: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Subtitles",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            SubtitleTrackList(
                tracks = tracks,
                selectedId = selectedId,
                onSelect = onSelect,
                onDisable = onDisable,
            )
        },
        confirmButton = {},
        dismissButton = {
            Row {
                TextButton(onClick = onAppearanceClick) {
                    Text("Appearance")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------------------
// Private sub-composables
// ---------------------------------------------------------------------------------------

@Composable
private fun SubtitleTrackList(
    tracks: List<SubtitleTrack>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDisable: () -> Unit,
) {
    LazyColumn {
        // "Off" option is always the first item; selected when no track is active.
        item(key = OFF_ITEM_KEY) {
            SubtitleOffRow(
                selected = selectedId == null,
                onDisable = onDisable,
            )
        }

        if (tracks.isEmpty()) {
            item(key = EMPTY_ITEM_KEY) {
                Text(
                    text = "No subtitle tracks available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            items(items = tracks, key = { it.id }) { track ->
                SubtitleTrackRow(
                    track = track,
                    selected = track.id == selectedId,
                    onSelect = { onSelect(track.id) },
                )
            }
        }
    }
}

@Composable
private fun SubtitleOffRow(
    selected: Boolean,
    onDisable: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDisable)
            .semantics { role = Role.RadioButton }
            .padding(vertical = 4.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onDisable,
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "Off",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SubtitleTrackRow(
    track: SubtitleTrack,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .semantics { role = Role.RadioButton }
            .padding(vertical = 4.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (track.language != null) {
                Text(
                    text = track.language,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------------------

private const val OFF_ITEM_KEY = "__subtitle_off__"
private const val EMPTY_ITEM_KEY = "__subtitle_empty__"
