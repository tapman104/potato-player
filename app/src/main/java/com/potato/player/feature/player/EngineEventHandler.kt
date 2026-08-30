package com.potato.player.feature.player

import com.potato.player.engine.MpvWrapper
import com.potato.player.engine.MpvEvent
import com.potato.player.engine.PlayerEngineState
import com.potato.player.data.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class EngineEventHandler(
    private val wrapper: MpvWrapper,
    private val prefsRepository: UserPreferencesRepository,
    private val scope: CoroutineScope
) {
    fun start(
        onLifecycleEvent: (MpvEvent.Lifecycle) -> Unit,
        onEngineState: (PlayerEngineState) -> Unit,
        onPrefsChanged: (Triple<Double, Int, Boolean>) -> Unit
    ) {
        scope.launch { wrapper.lifecycleEvents.collect { onLifecycleEvent(it) } }
        scope.launch { wrapper.engineState.collect { onEngineState(it) } }
        scope.launch {
            combine(
                prefsRepository.subScaleFlow,
                prefsRepository.subPosFlow,
                prefsRepository.autoRotationFlow
            ) { scale, pos, autoRot ->
                Triple(scale, pos, autoRot)
            }.collect { onPrefsChanged(it) }
        }
    }
}
