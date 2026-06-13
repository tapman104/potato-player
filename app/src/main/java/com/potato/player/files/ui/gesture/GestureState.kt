package com.potato.player.player.ui.gesture

/**
 * Discriminated union representing the gesture currently active on the player surface.
 *
 * Only one gesture can be active at a time. [None] is the idle state.
 */
sealed interface ActiveGesture {
    /** No gesture in progress. */
    data object None : ActiveGesture

    /** 2× speed hold active via long-press. Released when finger lifts. */
    data object LongPressSpeed : ActiveGesture

    /**
     * Vertical drag controlling media volume.
     *
     * @param fraction Normalised volume in [0f, 1f].
     */
    data class VolumeSwipe(val fraction: Float) : ActiveGesture

    /**
     * Vertical drag controlling screen brightness.
     *
     * @param fraction Normalised brightness in [0f, 1f].
     */
    data class BrightnessSwipe(val fraction: Float) : ActiveGesture

    /**
     * Double tap to seek forward or backward.
     */
    data class DoubleTapSeek(val isForward: Boolean) : ActiveGesture

    /**
     * Horizontal swipe to scrub to a target position.
     *
     * @param targetMs Target seek position in milliseconds.
     */
    data class SeekScrub(val targetMs: Long) : ActiveGesture
}

/**
 * Immutable snapshot of the gesture layer's runtime state.
 *
 * Consumed by [GestureOverlay] for rendering and by [PlayerScreen] for
 * auto-hide suppression.
 *
 * @param active              The gesture currently in progress.
 * @param speedBeforeLongPress Speed saved when a long-press starts so the
 *                             exact value can be restored on release.
 * @param volumeAccumulator    Accumulated fractional delta for volume changes.
 */
data class GestureState(
    val active: ActiveGesture = ActiveGesture.None,
    val speedBeforeLongPress: Float = 1f,
    val volumeAccumulator: Float = 0f,
    val brightnessAccumulator: Float = 0f,
    val seekStartPositionMs: Long = 0L,
    val seekAccumulator: Float = 0f,
    val seekDuration: Long = 0L,
)
