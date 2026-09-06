package com.potato.player.feature.player

import com.potato.player.engine.MpvWrapper
import com.potato.player.engine.MpvEvent
import com.potato.player.engine.PlayerEngineState
import com.potato.player.data.UserPreferencesRepository
import com.potato.player.feature.player.state.UiStateUpdate
import com.potato.player.feature.player.state.ProgressStateUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class PlayerPrefs(
    val subScale: Double,
    val subPos: Int,
    val videoOrientation: String,
    val gesturesEnabled: Boolean,
    val lockButtonEnabled: Boolean,
    val defaultDecoder: String,
    val defaultSpeed: Double,
    val controlsHideDelay: Int
)

class EngineEventHandler(
    private val wrapper: MpvWrapper,
    private val prefsRepository: UserPreferencesRepository,
    private val scope: CoroutineScope
) {
    private var lastTrackListJson: String = ""

    fun start(
        onLifecycleEvent: (MpvEvent.Lifecycle) -> Unit,
        onEngineState: (UiStateUpdate, ProgressStateUpdate) -> Unit,
        isDragging: () -> Boolean,
        onPrefsChanged: (PlayerPrefs) -> Unit,
        onTrackListChanged: (String) -> Unit
    ) {
        scope.launch {
            wrapper.lifecycleEvents.collect { event ->
                onLifecycleEvent(event)
                if (event is MpvEvent.Lifecycle.FileLoaded) {
                    // observeProperty for "width"/"height" is silently broken on
                    // this device — poll until MPV has real dimensions ready.
                    scope.launch {
                        var found = false
                        repeat(20) { attempt ->
                            if (found) return@repeat
                            delay(100)
                            val w = wrapper.getVideoWidth()
                            val h = wrapper.getVideoHeight()
                            val r = wrapper.getVideoRotate()
                            android.util.Log.d("EngineEventHandler", "Dimension poll #$attempt: w=$w, h=$h, r=$r")
                            if (w > 0 && h > 0) {
                                val current = wrapper.engineState.value
                                val uiUpdate = UiStateUpdate(
                                    isPlaying    = !current.paused,
                                    isBuffering  = current.pausedForCache || current.cacheBufferingState < 100,
                                    hwdecActive  = current.hwdecActive,
                                    videoWidth   = w,
                                    videoHeight  = h,
                                    videoRotate  = r,
                                    playbackSpeed = current.speed,
                                    subScale     = current.subScale,
                                    subPos       = current.subPos.toInt()
                                )
                                val progressUpdate = com.potato.player.feature.player.state.ProgressStateUpdate(
                                    positionSec      = current.positionMs / 1000.0,
                                    durationSec      = current.durationMs / 1000.0,
                                    cachedSec        = current.cacheTimeMs / 1000.0,
                                    cacheDurationSec = current.cacheDurMs / 1000.0
                                )
                                onEngineState(uiUpdate, progressUpdate)
                                found = true
                            }
                        }
                    }
                }
            }
        }
        scope.launch { 
            wrapper.engineState.collect { state ->
                val isBuffering = state.pausedForCache || state.cacheBufferingState < 100
                val uiUpdate = UiStateUpdate(
                    isPlaying = !state.paused,
                    isBuffering = isBuffering,
                    hwdecActive = state.hwdecActive,
                    videoWidth = state.videoWidth.toInt(),
                    videoHeight = state.videoHeight.toInt(),
                    videoRotate = state.videoRotate,
                    playbackSpeed = state.speed,
                    subScale = state.subScale,
                    subPos = state.subPos.toInt()
                )
                val progressUpdate = ProgressStateUpdate(
                    positionSec = if (!isDragging()) state.positionMs / 1000.0 else null,
                    durationSec = state.durationMs / 1000.0,
                    cachedSec = state.cacheTimeMs / 1000.0,
                    cacheDurationSec = state.cacheDurMs / 1000.0
                )
                onEngineState(uiUpdate, progressUpdate)
                if (state.trackListJson.isNotBlank() && state.trackListJson != lastTrackListJson) {
                    lastTrackListJson = state.trackListJson
                    onTrackListChanged(state.trackListJson)
                }
            }
        }
        scope.launch {
            combine(
                prefsRepository.subScaleFlow,
                prefsRepository.subPosFlow,
                prefsRepository.videoOrientationFlow,
                prefsRepository.gesturesEnabledFlow,
                prefsRepository.lockButtonEnabledFlow,
                prefsRepository.defaultDecoderFlow,
                prefsRepository.defaultSpeedFlow,
                prefsRepository.controlsHideDelayFlow
            ) { values ->
                PlayerPrefs(
                    subScale          = values[0] as Double,
                    subPos            = values[1] as Int,
                    videoOrientation  = values[2] as String,
                    gesturesEnabled   = values[3] as Boolean,
                    lockButtonEnabled = values[4] as Boolean,
                    defaultDecoder    = values[5] as String,
                    defaultSpeed      = values[6] as Double,
                    controlsHideDelay = values[7] as Int
                )
            }.collect { prefs -> onPrefsChanged(prefs) }
        }
    }
}
