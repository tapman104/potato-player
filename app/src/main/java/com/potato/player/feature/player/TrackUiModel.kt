package com.potato.player.feature.player

import com.potato.player.engine.TrackInfo

data class TrackUiModel(
    val id: Int,
    val title: String,
    val language: String,
    val isExternal: Boolean
) {
    fun displayLabel(): String = when {
        title.isNotBlank() -> title
        language.isNotBlank() -> language
        else -> "Track $id"
    }
}

fun TrackInfo.toUiModel() = TrackUiModel(
    id = id,
    title = title ?: "",
    language = lang ?: "",
    isExternal = isExternal
)
