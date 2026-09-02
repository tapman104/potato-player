package com.potato.player.feature.player.state

data class UiStateUpdate(
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val hwdecActive: String,
    val videoWidth: Int,
    val videoHeight: Int,
    val playbackSpeed: Double,
    val subScale: Double,
    val subPos: Int
)
