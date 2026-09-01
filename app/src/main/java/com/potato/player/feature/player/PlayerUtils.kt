package com.potato.player.feature.player

internal fun hwdecLabel(mode: String): String = when {
    mode == "no"                  -> "SW"
    mode == "mediacodec"          -> "HW"
    mode == "mediacodec-copy"     -> "HW+"
    mode.startsWith("mediacodec") -> "HW+"
    mode.isEmpty()                -> "HW+"
    else                          -> "HW"
}
