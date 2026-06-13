package com.potato.player.player.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import com.potato.player.player.ui.gesture.GestureOverlay
import com.potato.player.player.ui.gesture.PinchZoomHandler
import com.potato.player.player.ui.gesture.PlayerGestureHandler
import com.potato.player.viewmodel.PlayerViewModel
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot

@Composable
fun PlayerGestureLayer(
    viewModel: PlayerViewModel,
    zoomHandler: PinchZoomHandler,
    gestureHandler: PlayerGestureHandler,
    controlsVisible: Boolean,
    onControlsVisibleChange: (Boolean) -> Unit,
    isLongPressing: Boolean,
    onIsLongPressingChange: (Boolean) -> Unit,
    deadZoneThresholdPx: Float,
    modifier: Modifier = Modifier,
) {
    val zoomState by zoomHandler.zoomState.collectAsState()
    val gestureState by gestureHandler.gestureState.collectAsState()
    val viewConfiguration = LocalViewConfiguration.current
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(viewModel) {
                // Android timing constants from the system ViewConfiguration
                val longPressMs = viewConfiguration.longPressTimeoutMillis
                val doubleTapMs = viewConfiguration.doubleTapTimeoutMillis
                val dragSlop    = viewConfiguration.touchSlop

                awaitEachGesture {
                    // ── Phase 1: wait for finger down ────────────────────────
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val downX    = firstDown.position.x
                    val downY    = firstDown.position.y
                    val downTime = System.currentTimeMillis()

                    var gestureCommitted = false
                    var isDragging       = false
                    var isLongPressHeld  = false
                    var lastY            = downY

                    // ── Phase 2: classify gesture ─────────────────────────────
                    eventLoop@ while (true) {
                        val elapsed   = System.currentTimeMillis() - downTime
                        val remaining = longPressMs - elapsed

                        val event: PointerEvent? = if (!gestureCommitted && remaining > 0) {
                            withTimeoutOrNull(remaining) { awaitPointerEvent() }
                        } else {
                            awaitPointerEvent()
                        }

                        // Timeout — long-press fires
                        if (event == null) {
                            if (!gestureCommitted) {
                                gestureCommitted = true
                                isLongPressHeld  = true
                                onIsLongPressingChange(true)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                gestureHandler.onLongPressStart()
                            }
                            continue@eventLoop
                        }

                        if (event.changes.size > 1) {
                            // Multi-touch detected! Abandon single-touch gesture.
                            if (isDragging) gestureHandler.onVerticalDragEnd()
                            if (isLongPressHeld) {
                                onIsLongPressingChange(false)
                                gestureHandler.onLongPressEnd()
                            }
                            break@eventLoop
                        }

                        val primary = event.changes.firstOrNull { it.id == firstDown.id }
                            ?: break@eventLoop
                        val dx       = primary.position.x - downX
                        val dy       = primary.position.y - downY
                        val distance = hypot(dx, dy)

                        when {
                            // ── Axis classification (pre-commit) ──────────────────
                            // Once total movement exceeds dragSlop, determine whether
                            // this is a vertical or horizontal drag:
                            //   - |dy| > 24dp  AND  |dy| > |dx|*1.5 → vertical: commit
                            //   - |dx| wins first                    → horizontal: abandon
                            !gestureCommitted && distance > dragSlop -> {
                                val absDx = kotlin.math.abs(dx)
                                val absDy = kotlin.math.abs(dy)
                                when {
                                    // Clearly vertical: cross dead zone AND steeper than 1.5:1 ratio
                                    absDy >= deadZoneThresholdPx && absDy > absDx * 1.5f -> {
                                        gestureCommitted = true
                                        isDragging = true
                                        firstDown.consume()
                                        gestureHandler.onVerticalDragStart(downX, size.width.toFloat())
                                        val firstDy = primary.position.y - lastY
                                        lastY = primary.position.y
                                        primary.consume()
                                        gestureHandler.onVerticalDrag(firstDy)
                                    }
                                    // Horizontal wins first — abandon this gesture entirely
                                    // so the horizontal seek handler can take over.
                                    absDx > absDy * 1.5f -> {
                                        gestureCommitted = true // prevents re-evaluation
                                        // isDragging stays false, so onVerticalDragEnd is NOT called
                                    }
                                    // Ambiguous (diagonal) — keep waiting
                                    else -> Unit
                                }
                            }
                            // ── Continuing vertical drag ────────────────────────
                            isDragging -> {
                                val delta = primary.position.y - lastY
                                lastY = primary.position.y
                                primary.consume()
                                gestureHandler.onVerticalDrag(delta)
                            }
                        }

                        // ── Pointer lifted ──────────────────────────────────────
                        val allUp = event.changes.all { !it.pressed }
                        if (allUp) {
                            when {
                                isDragging -> gestureHandler.onVerticalDragEnd()
                                isLongPressHeld -> {
                                    onIsLongPressingChange(false)
                                    gestureHandler.onLongPressEnd()
                                }
                                else -> {
                                    // UP before slop/long-press → could be tap or first
                                    // half of a double-tap. Wait for a second DOWN.
                                    var secondDown = false

                                    doubleTapLoop@ while (true) {
                                        val dtEvent = withTimeoutOrNull(doubleTapMs) {
                                            awaitPointerEvent()
                                        }
                                        if (dtEvent == null) break@doubleTapLoop

                                        val dtPrimary = dtEvent.changes.firstOrNull { it.pressed }
                                        if (dtPrimary != null) {
                                            secondDown = true
                                            val tapX  = dtPrimary.position.x
                                            val third = size.width / 3f
                                            when {
                                                tapX < third      -> {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    gestureHandler.onDoubleTap(isForward = false)
                                                }
                                                tapX > third * 2f -> {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    gestureHandler.onDoubleTap(isForward = true)
                                                }
                                                else              -> {
                                                    if (zoomState.scale > 1f) {
                                                        zoomHandler.resetZoom()
                                                    } else {
                                                        gestureHandler.onTap()
                                                        onControlsVisibleChange(!controlsVisible)
                                                    }
                                                }
                                            }
                                            withTimeoutOrNull(500L) { awaitPointerEvent() } // drain UP
                                            break@doubleTapLoop
                                        }
                                    }

                                    if (!secondDown) {
                                        // No second tap — plain single tap: toggle controls
                                        onControlsVisibleChange(!controlsVisible)
                                    }
                                }
                            }
                            break@eventLoop
                        }
                    }
                }
            }
    )

    // ── Layer 2b: Gesture overlay ─────────────────────────────────────────────
    GestureOverlay(
        gestureState = gestureState,
        modifier = Modifier.fillMaxSize(),
    )
}
