package com.potato.player.home

import com.potato.player.data.MediaFile

data class FolderGroup(
    val folderName: String,
    val folderPath: String,
    val files: List<MediaFile>,
    val isExpanded: Boolean
)

sealed interface HomeUiState {
    object Loading : HomeUiState
    object PermissionRequired : HomeUiState
    data class Ready(
        val files: List<MediaFile>,
        val recentFiles: List<MediaFile>
    ) : HomeUiState
}
