package com.potato.player.feature.player

import com.potato.player.feature.player.state.ActiveDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DialogStateHolder {
    private val _activeDialog = MutableStateFlow<ActiveDialog>(ActiveDialog.None)
    val activeDialog: StateFlow<ActiveDialog> = _activeDialog.asStateFlow()

    fun show(dialog: ActiveDialog) { _activeDialog.value = dialog }
    fun dismiss() { _activeDialog.value = ActiveDialog.None }
}
