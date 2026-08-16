package com.potato.player.feature.player

import android.content.Context
import com.potato.player.R
import com.potato.player.engine.TrackInfo

data class TrackUiModel(
    val id: Int,
    val title: String,
    val language: String,
    val isExternal: Boolean,
    val context: Context
) {
    fun displayLabel(): String = when {
        title.isNotBlank() -> title
        language.isNotBlank() -> language
        else -> context.getString(R.string.player_track_label_fallback, id)
    }
}

fun TrackInfo.toUiModel(context: Context) = TrackUiModel(
    id = id,
    title = title ?: "",
    language = lang ?: "",
    isExternal = isExternal,
    context = context
)
