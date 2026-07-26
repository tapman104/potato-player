package com.potato.player.feature.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PlayerDialogStateHolder {
    private val _activeSheet = MutableStateFlow(ActiveSheet.NONE)
    val activeSheet: StateFlow<ActiveSheet> = _activeSheet.asStateFlow()

    fun onMoreMenuToggle() {
        _activeSheet.update { if (it == ActiveSheet.MORE_MENU) ActiveSheet.NONE else ActiveSheet.MORE_MENU }
    }
    fun onMoreMenuDismiss() { _activeSheet.value = ActiveSheet.NONE }
    fun onShowAudioDialog() { _activeSheet.value = ActiveSheet.AUDIO }
    fun onDismissAudioDialog() { _activeSheet.value = ActiveSheet.NONE }
    fun onShowSubtitleDialog() { _activeSheet.value = ActiveSheet.SUBTITLE }
    fun onDismissSubtitleDialog() { _activeSheet.value = ActiveSheet.NONE }
    fun onShowSpeedDialog() { _activeSheet.value = ActiveSheet.SPEED }
    fun onDismissSpeedDialog() { _activeSheet.value = ActiveSheet.NONE }
    fun onShowDecoderDialog() { _activeSheet.value = ActiveSheet.DECODER }
    fun onDismissDecoderDialog() { _activeSheet.value = ActiveSheet.NONE }
}
