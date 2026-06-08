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
import com.potato.player.engine.AudioTrack

/**
 * A modal dialog that presents the list of available audio tracks and lets
 * the user pick one.
 *
 * Follows the **pure data-in / intent-out** pattern: all state is passed in
 * as parameters and selections are communicated back through lambdas. The
 * composable holds no ViewModel reference and carries no business logic â€”
 * it is a pure function of its inputs.
 *
 * Typical usage from a caller that owns the state:
 * ```kotlin
 * if (showAudioDialog) {
 *     AudioTrackDialog(
 *         tracks     = trackState.audioTracks,
 *         selectedId = trackState.selectedAudioTrackId,
 *         onSelect   = { id -> viewModel.selectAudioTrack(id) },
 *         onDismiss  = { showAudioDialog = false },
 *     )
 * }
 * ```
 *
 * @param tracks     The ordered list of audio tracks to display. Passing an
 *                   empty list renders the dialog with a "no tracks" message.
 * @param selectedId The [AudioTrack.id] of the currently active track, or
 *                   `null` if none is selected / unknown.
 * @param onSelect   Called with the chosen [AudioTrack.id] when the user taps
 *                   a row. The dialog does **not** dismiss itself; the caller
 *                   must set a state variable (or call [onDismiss]) in response
 *                   if auto-dismiss is desired.
 * @param onDismiss  Called when the user taps outside the dialog or the
 *                   "Cancel" button.
 */
@Composable
fun AudioTrackDialog(
    tracks: List<AudioTrack>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Audio Track",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            AudioTrackList(
                tracks = tracks,
                selectedId = selectedId,
                onSelect = onSelect,
            )
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// ---------------------------------------------------------------------------------------
// Private sub-composable
// ---------------------------------------------------------------------------------------

@Composable
private fun AudioTrackList(
    tracks: List<AudioTrack>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    if (tracks.isEmpty()) {
        Text(
            text = "No audio tracks available.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        return
    }

    LazyColumn {
        items(items = tracks, key = { it.id }) { track ->
            AudioTrackRow(
                track = track,
                selected = track.id == selectedId,
                onSelect = { onSelect(track.id) },
            )
        }
    }
}

@Composable
private fun AudioTrackRow(
    track: AudioTrack,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onSelect,
            )
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
