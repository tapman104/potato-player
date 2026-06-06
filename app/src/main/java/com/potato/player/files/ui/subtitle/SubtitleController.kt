package com.potato.player.player.ui.subtitle

import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView

/**
 * Immutable settings that govern how subtitles are rendered in [SubtitleView].
 *
 * @param sizeFraction          Fractional text size passed to
 *                              [SubtitleView.setFractionalTextSize]. Defaults to
 *                              90% of the ExoPlayer default — large and readable
 *                              without being intrusive.
 * @param bottomPaddingFraction Fraction of the view height that acts as bottom
 *                              padding, passed to [SubtitleView.setBottomPaddingFraction].
 *                              Defaults to 0.12 — lifts the baseline 12% from
 *                              the bottom edge, placing text roughly 70–75% down
 *                              the video frame.
 */
data class SubtitleSettings(
    val sizeFraction: Float = SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 0.90f,
    val bottomPaddingFraction: Float = 0.12f,
)

/**
 * Applies [settings] to [subtitleView], configuring caption style, embedded-style
 * overrides, fractional text size, and bottom padding in one call.
 *
 * This is the single source of truth for subtitle appearance. Call it:
 *  - Once inside the [AndroidView] factory block (initial setup).
 *  - Again whenever [SubtitleSettings] changes (e.g. after the user adjusts
 *    size or position through [SubtitleSizeDialog]).
 *
 * @param subtitleView The [SubtitleView] extracted from a [PlayerView].
 * @param settings     The desired rendering settings.
 */
fun applySubtitleSettings(subtitleView: SubtitleView, settings: SubtitleSettings) {
    val style = CaptionStyleCompat(
        android.graphics.Color.WHITE,
        android.graphics.Color.TRANSPARENT,
        android.graphics.Color.TRANSPARENT,
        CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
        android.graphics.Color.BLACK,
        null,
    )
    subtitleView.setStyle(style)
    subtitleView.setApplyEmbeddedStyles(false)
    subtitleView.setApplyEmbeddedFontSizes(false)
    subtitleView.setFractionalTextSize(settings.sizeFraction)
    subtitleView.setBottomPaddingFraction(settings.bottomPaddingFraction)
}
