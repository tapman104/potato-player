package com.potato.player.player.ui.gesture

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import com.potato.player.player.viewmodel.PlayerViewModel
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
        val currentSpeed = viewModel.uiState.value.playbackSpeed
        _gestureState.update { it.copy(speedBeforeLongPress = currentSpeed) }
        viewModel.setPlaybackSpeed(2f)
        _gestureState.update { it.copy(active = ActiveGesture.LongPressSpeed) }
    }

    // GESTURE: long-press-end
    /** Called when the finger lifts after a long-press. */
    fun onLongPressEnd() {
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
     * Applies a fractional brightness delta using [Settings.System.SCREEN_BRIGHTNESS] (0–255).
     *
     * Uses a step-accumulator identical to [handleVolumeDrag] so small drags
     * are buffered until they cross a whole-integer boundary.
     * Requires [android.Manifest.permission.WRITE_SETTINGS].
     */
    private fun handleBrightnessDrag(fractionalDelta: Float) {
        val resolver = context.contentResolver
        // On some devices SCREEN_BRIGHTNESS may throw if MODE is AUTO; guard with try/catch.
        val currentRaw = try {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (_: Settings.SettingNotFoundException) {
            128 // fallback to mid-brightness
        }

        // One step = 1/255 of the full range.
        val threshold = 1f / 255f

        _gestureState.update { state ->
            val newAccumulator = state.brightnessAccumulator + fractionalDelta

            if (kotlin.math.abs(newAccumulator) >= threshold) {
                val stepsToApply = (newAccumulator / threshold).toInt()
                val remainder = newAccumulator - (stepsToApply * threshold)

                val newRaw = (currentRaw + stepsToApply).coerceIn(0, 255)
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, newRaw)

                state.copy(
                    active = ActiveGesture.BrightnessSwipe(newRaw / 255f),
                    brightnessAccumulator = remainder,
                )
            } else {
                state.copy(
                    active = ActiveGesture.BrightnessSwipe(currentRaw / 255f),
                    brightnessAccumulator = newAccumulator,
                )
            }
        }
    }

    private fun currentVolumeFraction(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) current.toFloat() / max.toFloat() else 0f
    }
}
