package com.potato.player.feature.player.state

data class ProgressStateUpdate(
    val positionSec: Double?,
    val durationSec: Double,
    val cachedSec: Double,
    val cacheDurationSec: Double
)
