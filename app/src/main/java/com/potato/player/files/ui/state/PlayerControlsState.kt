package com.potato.player.player.ui.state

import androidx.media3.ui.AspectRatioFrameLayout

/**
 * UI-only state for the player controls overlay.
 *
 * Intentionally separate from [com.potato.player.player.viewmodel.PlayerUiState],
 * which is derived from the engine's playback state. This class captures
 * purely presentational and device-configuration concerns that the engine
 * has no knowledge of and does not need.
 *
 * Kept as a plain `data class` (no flows, no coroutines) so any Composable
 * or ViewModel can hoist and transform it without coupling to a particular
 * lifecycle owner.
 *
 * @property controlsVisible Whether the controls overlay is currently shown.
 *   Toggled by a tap gesture and auto-hidden after a short idle period
 *   while playback is active.
 * @property rotationLocked When `true`, the system's auto-rotate setting is
 *   overridden and the screen stays in the orientation dictated by
 *   [orientationMode]. When `false`, the device sensor drives rotation
 *   normally (equivalent to [OrientationMode.AUTO]).
 * @property orientationMode The active orientation policy. Only meaningful
 *   when [rotationLocked] is `true`; ignored otherwise.
 */
data class PlayerControlsState(
    val controlsVisible: Boolean,
    val rotationLocked: Boolean,
    val orientationMode: OrientationMode,
    val resizeMode: ResizeMode,
) {
    companion object {
        /**
         * Sensible out-of-the-box defaults:
         *  - controls start visible so the user can act immediately,
         *  - rotation follows the system sensor (unlocked, AUTO).
         */
        val Initial = PlayerControlsState(
            controlsVisible = true,
            rotationLocked = false,
            orientationMode = OrientationMode.AUTO,
            resizeMode = ResizeMode.FIT,
        )
    }
}

// ---------------------------------------------------------------------------------------
// Orientation mode
// ---------------------------------------------------------------------------------------

/**
 * Describes the screen orientation policy for the player.
 *
 * Used by the Activity (or a side-effect in PlayerScreen) to call
 * [android.app.Activity.setRequestedOrientation] with the appropriate
 * constant.
 *
 * ```
 * fun OrientationMode.toActivityOrientation(): Int = when (this) {
 *     OrientationMode.AUTO              -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
 *     OrientationMode.LOCKED_PORTRAIT   -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
 *     OrientationMode.LOCKED_LANDSCAPE  -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
 * }
 * ```
 */
enum class OrientationMode {

    /**
     * Device sensor drives rotation. This is the default and matches the
     * system auto-rotate behaviour. Maps to
     * [android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR].
     */
    AUTO,

    /**
     * Orientation is fixed to portrait regardless of sensor input. Maps to
     * [android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT].
     */
    LOCKED_PORTRAIT,

    /**
     * Orientation is fixed to landscape regardless of sensor input. Maps to
     * [android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE].
     */
    LOCKED_LANDSCAPE,
}

// ---------------------------------------------------------------------------------------
// Resize mode
// ---------------------------------------------------------------------------------------

enum class ResizeMode(val value: Int) {
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL(AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);

    fun next(): ResizeMode = when (this) {
        FIT -> FILL
        FILL -> ZOOM
        ZOOM -> FIT
    }
}
