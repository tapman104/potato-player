package com.potato.player.feature.player.state

import android.content.Context
import com.potato.player.R
import com.potato.player.engine.TrackInfo

data class TrackUiModel(
    val id: Int,
    val title: String,
    val language: String,
    val isExternal: Boolean,
    val displayLabel: String
)

fun TrackInfo.toUiModel(context: Context) = TrackUiModel(
    id = id,
    title = title ?: "",
    language = lang ?: "",
    isExternal = isExternal,
    displayLabel = when {
        !title.isNullOrBlank() -> title
        !lang.isNullOrBlank() -> lang
        else -> context.getString(R.string.player_track_label_fallback, id)
    }
)
