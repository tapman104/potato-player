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
    displayLabel = buildDisplayLabel(context)
)

private fun TrackInfo.buildDisplayLabel(context: Context): String {
    val langName = lang?.let { langCodeToName(it) }
    val hasTitle = !title.isNullOrBlank()
    val hasLang  = langName != null

    return when {
        hasLang && hasTitle  -> "$langName — ${title}"
        hasLang              -> langName!!
        hasTitle             -> title!!
        else                 -> context.getString(R.string.player_track_label_fallback, id)
    }
}

private fun langCodeToName(code: String): String? = when (code.lowercase().trim()) {
    "eng", "en"             -> "English"
    "kor", "ko"             -> "Korean"
    "jpn", "ja"             -> "Japanese"
    "zho", "zh", "chi"      -> "Chinese"
    "zho-hans", "zh-hans"   -> "Chinese (Simplified)"
    "zho-hant", "zh-hant"   -> "Chinese (Traditional)"
    "hin", "hi"             -> "Hindi"
    "ara", "ar"             -> "Arabic"
    "fra", "fr"             -> "French"
    "deu", "de"             -> "German"
    "spa", "es"             -> "Spanish"
    "por", "pt"             -> "Portuguese"
    "rus", "ru"             -> "Russian"
    "ita", "it"             -> "Italian"
    "tur", "tr"             -> "Turkish"
    "vie", "vi"             -> "Vietnamese"
    "tha", "th"             -> "Thai"
    "ind", "id"             -> "Indonesian"
    "msa", "ms"             -> "Malay"
    "pol", "pl"             -> "Polish"
    "nld", "nl"             -> "Dutch"
    "swe", "sv"             -> "Swedish"
    "nor", "no"             -> "Norwegian"
    "dan", "da"             -> "Danish"
    "fin", "fi"             -> "Finnish"
    "ces", "cs"             -> "Czech"
    "hun", "hu"             -> "Hungarian"
    "ron", "ro"             -> "Romanian"
    "ukr", "uk"             -> "Ukrainian"
    "heb", "he"             -> "Hebrew"
    "tam", "ta"             -> "Tamil"
    "tel", "te"             -> "Telugu"
    "mal", "ml"             -> "Malayalam"
    "kan", "kn"             -> "Kannada"
    "ben", "bn"             -> "Bengali"
    "urd", "ur"             -> "Urdu"
    else                    -> null
}
