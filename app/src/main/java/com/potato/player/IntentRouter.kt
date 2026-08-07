package com.potato.player

import android.content.Intent
import android.net.Uri

/**
 * Represents the screen the app should start on.
 *
 * Resolved synchronously from the launch Intent BEFORE setContent, so the
 * NavHost can set its startDestination without ever composing HomeScreen first.
 */
sealed class PlayerStartDestination {
    /** Normal home launch — no video intent. */
    data object Home : PlayerStartDestination()

    /**
     * External video open (ACTION_VIEW / ACTION_SEND with a video URI).
     *
     * [uri]   — the raw, un-encoded Uri from the intent.
     * [title] — last path segment, or null if none.
     */
    data class Player(val uri: Uri, val title: String?) : PlayerStartDestination()
}

/**
 * Pure routing function — reads intent metadata and returns the correct
 * start destination.
 *
 * CONTRACT:
 *   - No side effects (no permission grants, no ContentResolver reads).
 *   - No Context dependency — only what the Intent itself carries.
 *   - Safe to call before setContent().
 *
 * URI-permission grants and readability checks are the caller's responsibility
 * (see MainActivity.kt) because they require ContentResolver.
 */
fun resolveStartDestination(intent: Intent): PlayerStartDestination {
    val action = intent.action
    if (action != Intent.ACTION_VIEW && action != Intent.ACTION_SEND) {
        return PlayerStartDestination.Home
    }

    val uri: Uri = if (action == Intent.ACTION_SEND) {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: intent.data
    } else {
        intent.data
    } ?: return PlayerStartDestination.Home

    val title = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }

    return PlayerStartDestination.Player(uri = uri, title = title)
}
