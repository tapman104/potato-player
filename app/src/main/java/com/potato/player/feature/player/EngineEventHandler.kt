package com.potato.player.feature.player

import com.potato.player.engine.MpvWrapper
import com.potato.player.engine.MpvEvent
import com.potato.player.engine.PlayerEngineState
import com.potato.player.data.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class PlayerPrefs(
    val subScale: Double,
    val subPos: Int,
    val autoRotation: Boolean,
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
        onEngineState: (PlayerEngineState) -> Unit,
        onPrefsChanged: (PlayerPrefs) -> Unit,
        onTrackListChanged: (String) -> Unit
    ) {
        scope.launch { wrapper.lifecycleEvents.collect { onLifecycleEvent(it) } }
        scope.launch { 
            wrapper.engineState.collect { state ->
                onEngineState(state)
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
                prefsRepository.autoRotationFlow,
                prefsRepository.gesturesEnabledFlow,
                prefsRepository.lockButtonEnabledFlow,
                prefsRepository.defaultDecoderFlow,
                prefsRepository.defaultSpeedFlow,
                prefsRepository.controlsHideDelayFlow
            ) { values ->
                PlayerPrefs(
                    subScale          = values[0] as Double,
                    subPos            = values[1] as Int,
                    autoRotation      = values[2] as Boolean,
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
