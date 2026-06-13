package com.potato.player.player.ui.gesture

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import com.potato.player.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Translates raw touch events into player/system intents.
 *
 * All gesture-to-action logic lives here so [PlayerScreen] stays a thin
 * wiring layer. The class is intentionally free of Compose imports so it
 * can be tested without a Compose runtime.
 *
 * @param viewModel      The player ViewModel. Used for speed and playback control.
 * @param audioManager   System [AudioManager] for volume stream queries/changes.
 * @param screenHeightPx Physical screen height in pixels. Injected for testability.
 * @param context        Application/activity context used for brightness Settings access.
 */
class PlayerGestureHandler(
    private val viewModel: PlayerViewModel,
    private val audioManager: AudioManager,
    private val screenHeightPx: Float,
    private val context: Context,
    private val appPreferences: com.potato.player.files.preferences.AppPreferences,
) {
    // Internal mutable state; expose as read-only StateFlow<GestureState>
    private val _gestureState = MutableStateFlow(GestureState())
    val gestureState: StateFlow<GestureState> = _gestureState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Whether the active drag started on the left half (brightness) or right half (volume). */
    private var dragOnLeftSide = false

    fun release() {
        scope.cancel()
    }

    // ── Gesture callbacks ─────────────────────────────────────────────────────

    // GESTURE: long-press-start
    /** Called when the player surface receives a long-press event. */
    fun onLongPressStart() {
        if (!appPreferences.longPressForSpeed.value) return
        val currentSpeed = viewModel.uiState.value.playbackSpeed
        _gestureState.update { it.copy(speedBeforeLongPress = currentSpeed) }
        viewModel.setPlaybackSpeed(2f)
        _gestureState.update { it.copy(active = ActiveGesture.LongPressSpeed) }
    }

    // GESTURE: long-press-end
    /** Called when the finger lifts after a long-press. */
    fun onLongPressEnd() {
        if (!appPreferences.longPressForSpeed.value) return
        viewModel.setPlaybackSpeed(_gestureState.value.speedBeforeLongPress)
        _gestureState.update { it.copy(active = ActiveGesture.None) }
    }

    // GESTURE: vertical-drag-start
    /**
     * Called at the beginning of a vertical drag.
     *
     * Records which side of the screen the drag started on so [onVerticalDrag]
     * knows whether to control brightness (left) or volume (right).
     *
     * @param startX     X coordinate of the touch-down event in pixels.
     * @param totalWidth Total width of the gesture area in pixels.
     */
    fun onVerticalDragStart(startX: Float, totalWidth: Float) {
        if (!appPreferences.swipeForVolume.value) return
        dragOnLeftSide = startX < totalWidth / 2f
        // Reset the appropriate accumulator so each drag starts clean.
        _gestureState.update { state ->
            if (dragOnLeftSide) state.copy(brightnessAccumulator = 0f)
            else state.copy(volumeAccumulator = 0f)
        }
    }

    // GESTURE: vertical-drag
    /**
     * Called for each pointer move event during a vertical drag.
     *
     * Drag upward (negative [deltaY] in screen coordinates) increases the
     * controlled value; drag downward decreases it.
     *
     * Left side  → brightness ([Settings.System.SCREEN_BRIGHTNESS], 0-255)
     * Right side → media volume
     *
     * @param deltaY Change in Y since the last call, in pixels (positive = down).
     */
    fun onVerticalDrag(deltaY: Float) {
        if (!appPreferences.swipeForVolume.value) return
        val fractionalDelta = deltaY / screenHeightPx * -1f // drag up = positive
        if (dragOnLeftSide) {
            handleBrightnessDrag(fractionalDelta)
        } else {
            handleVolumeDrag(fractionalDelta)
        }
    }

    // GESTURE: vertical-drag-end
    /** Called when the vertical drag gesture ends or is cancelled. */
    fun onVerticalDragEnd() {
        if (!appPreferences.swipeForVolume.value) return
        _gestureState.update { it.copy(active = ActiveGesture.None) }
    }

    // GESTURE: tap
    /**
     * No-op in this class; exists so callers never bypass the handler.
     *
     * Controls-visibility toggling is a UI concern owned by [PlayerScreen].
     */
    fun onTap() {
        // Intentionally empty — UI caller handles the toggle.
    }

    fun onDoubleTap(isForward: Boolean) {
        if (!appPreferences.doubleTapToSeek.value) return
        if (isForward) {
            viewModel.seekForward10()
        } else {
            viewModel.seekBackward10()
        }

        _gestureState.update { it.copy(active = ActiveGesture.DoubleTapSeek(isForward)) }

        scope.launch {
            delay(600)
            _gestureState.update {
                if (it.active is ActiveGesture.DoubleTapSeek) {
                    it.copy(active = ActiveGesture.None)
                } else {
                    it
                }
            }
        }
    }

    // GESTURE: horizontal-drag-start (seek scrub)
    /** Called when a horizontal drag is classified as a seek-scrub gesture. */
    fun onHorizontalDragStart() {
        val currentPos = viewModel.positionState.value.positionMs
        val duration = viewModel.positionState.value.durationMs
        _gestureState.update { it.copy(
            active = ActiveGesture.SeekScrub(currentPos),
            seekStartPositionMs = currentPos,
            seekAccumulator = 0f,
            seekDuration = duration,
        )}
    }

    // GESTURE: horizontal-drag (seek scrub)
    /**
     * Called for each pointer-move event during a horizontal seek-scrub drag.
     *
     * A full swipe across the screen maps to 90 seconds of seek distance.
     *
     * @param deltaX        Change in X since the last call, in pixels (positive = right).
     * @param viewportWidth Width of the gesture area in pixels.
     */
    fun onHorizontalDrag(deltaX: Float, viewportWidth: Float) {
        val state = _gestureState.value
        if (state.active !is ActiveGesture.SeekScrub) return
        val duration = state.seekDuration
        if (duration <= 0L) return
        // Full swipe across screen = 90 seconds seek
        val secondsPerPixel = 90f / viewportWidth
        val newAccumulator = state.seekAccumulator + deltaX * secondsPerPixel
        val targetMs = (state.seekStartPositionMs + (newAccumulator * 1000f).toLong())
            .coerceIn(0L, duration)
        _gestureState.update { it.copy(
            active = ActiveGesture.SeekScrub(targetMs),
            seekAccumulator = newAccumulator,
        )}
        viewModel.seekTo(targetMs)
    }

    // GESTURE: horizontal-drag-end (seek scrub)
    /** Called when the horizontal seek-scrub drag ends or is cancelled. */
    fun onHorizontalDragEnd() {
        _gestureState.update { it.copy(active = ActiveGesture.None) }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun handleVolumeDrag(fractionalDelta: Float) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVolume == 0) return

        val threshold = 1f / maxVolume

        _gestureState.update { state ->
            val newAccumulator = state.volumeAccumulator + fractionalDelta

            if (kotlin.math.abs(newAccumulator) >= threshold) {
                val stepsToApply = (newAccumulator / threshold).toInt()
                val remainder = newAccumulator - (stepsToApply * threshold)

                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val newVolume = (currentVolume + stepsToApply).coerceIn(0, maxVolume)

                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    newVolume,
                    0
                )
                appPreferences.saveVolume(currentVolumeFraction())

                state.copy(
                    active = ActiveGesture.VolumeSwipe(currentVolumeFraction()),
                    volumeAccumulator = remainder
                )
            } else {
                state.copy(
                    active = ActiveGesture.VolumeSwipe(currentVolumeFraction()),
                    volumeAccumulator = newAccumulator
                )
            }
        }
    }

    /**
     * Controls screen brightness for this window only via [WindowManager.LayoutParams].
     *
     * Uses [android.view.WindowManager.LayoutParams.screenBrightness] (0f–1f, -1 = system
     * default) which requires NO special permissions, unlike [Settings.System.SCREEN_BRIGHTNESS].
     * Changes are local to the player Activity and restore automatically when the window closes.
     */
    private fun handleBrightnessDrag(fractionalDelta: Float) {
        val activity = context.findActivity() ?: return
        val window   = activity.window
        val lp       = window.attributes

        // -1 means "use system default"; treat it as 0.5 so first drag feels natural.
        val currentBrightness = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness

        // Use fine steps (same 1/255 granularity as before) so small drags accumulate.
        val threshold = 1f / 255f
        val state     = _gestureState.value
        val newAccumulator = state.brightnessAccumulator + fractionalDelta

        if (kotlin.math.abs(newAccumulator) >= threshold) {
            val stepsToApply  = (newAccumulator / threshold).toInt()
            val remainder     = newAccumulator - (stepsToApply * threshold)
            val newBrightness = (currentBrightness + stepsToApply * threshold).coerceIn(0.05f, 1.0f)

            lp.screenBrightness = newBrightness
            window.attributes   = lp
            appPreferences.saveBrightness(newBrightness)

            _gestureState.value = state.copy(
                active               = ActiveGesture.BrightnessSwipe(newBrightness),
                brightnessAccumulator = remainder,
            )
        } else {
            _gestureState.value = state.copy(
                active               = ActiveGesture.BrightnessSwipe(currentBrightness),
                brightnessAccumulator = newAccumulator,
            )
        }
    }

    /** Walk up the ContextWrapper chain to find the underlying Activity. */
    private fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun currentVolumeFraction(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) current.toFloat() / max.toFloat() else 0f
    }
}
