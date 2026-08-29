package com.potato.player.engine

import android.content.Context
import android.util.Log
import `is`.xyz.mpv.MPVLib

class MpvOptionsConfigurator {

    fun copyFontAssets(context: Context) {
        val fontsDir = java.io.File(context.filesDir, "fonts")
        if (!fontsDir.exists()) fontsDir.mkdirs()
        val fontFile = java.io.File(fontsDir, "Roboto-Regular.ttf")
        try {
            val assetSize = context.assets.open("Roboto-Regular.ttf").use { it.available().toLong() }
            // Copy if the file is missing or its size differs from the bundled asset.
            // A size mismatch is a reliable signal that the APK was updated with a new font.
            if (!fontFile.exists() || fontFile.length() != assetSize) {
                context.assets.open("Roboto-Regular.ttf").use { input ->
                    fontFile.outputStream().use { input.copyTo(it) }
                }
                Log.d(TAG, "Font asset copied (size changed or missing)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy font asset", e)
        }
    }

    fun initOptions(context: Context) {
        val filesDir = context.filesDir.path

        // Core engine config
        MPVLib.setOptionString("config",       "yes")
        MPVLib.setOptionString("config-dir",   filesDir)
        MPVLib.setOptionString("idle",         "yes")

        // Video output
        MPVLib.setOptionString("profile",      "fast")
        MPVLib.setOptionString("vo",           "gpu")
        MPVLib.setOptionString("gpu-context",  "android")

        // Hardware decoding: HW+ → HW → SW fallback chain
        MPVLib.setOptionString("hwdec",        "mediacodec-copy")
        MPVLib.setOptionString("hwdec-codecs", "all")

        // Cache — capped for mobile memory
        MPVLib.setOptionString("demuxer-max-bytes",      MpvCache.MAX_BYTES)
        MPVLib.setOptionString("demuxer-max-back-bytes", MpvCache.MAX_BACK_BYTES)
        MPVLib.setOptionString("cache-secs",             MpvCache.SECS)

        // Logging — keep quiet in production
        MPVLib.setOptionString("msg-level", "all=warn")

        // Rendering optimizations
        MPVLib.setOptionString("opengl-early-flush",  "no")
        MPVLib.setOptionString("video-sync",          "display-resample")
        MPVLib.setOptionString("scale",               "bilinear")
        MPVLib.setOptionString("cscale",              "bilinear")
        MPVLib.setOptionString("dscale",              "bilinear")
        MPVLib.setOptionString("vd-lavc-threads", "0")

        // Subtitle defaults — minimal setup, no auto-selection
        MPVLib.setOptionString("sub-font-provider", "none")
        MPVLib.setOptionString("sub-fonts-dir",     "$filesDir/fonts")
        MPVLib.setOptionString("sub-font",          "Roboto")
        MPVLib.setOptionString("sub-font-size",     "55")
        MPVLib.setOptionString("sub-bold",          "yes")
        MPVLib.setOptionString("sub-color",         "#FFFFFF")
        MPVLib.setOptionString("sub-border-color",  "#000000")
        MPVLib.setOptionString("sub-border-size",   "3")
        MPVLib.setOptionString("sub-auto",          "no")

        // Audio
        MPVLib.setOptionString("audio-pitch-correction", "yes")

        // Behaviour
        MPVLib.setPropertyBoolean("keep-open",              true)
        MPVLib.setPropertyBoolean("input-default-bindings", true)
    }

    fun postInitOptions() {
        // Debanding off by default — can be toggled later in Phase 8
        MPVLib.setOptionString("deband", "no")
    }

    fun registerPropertyObservers() {
        MPVLib.observeProperty(MpvProp.PAUSE,              MpvFmt.FLAG)
        MPVLib.observeProperty(MpvProp.TIME_POS,           MpvFmt.DOUBLE)
        MPVLib.observeProperty(MpvProp.DURATION,           MpvFmt.DOUBLE)
        MPVLib.observeProperty(MpvProp.DEMUXER_CACHE_TIME, MpvFmt.DOUBLE)
        MPVLib.observeProperty(MpvProp.DEMUXER_CACHE_DURATION, MpvFmt.DOUBLE)
        MPVLib.observeProperty(MpvProp.SPEED,              MpvFmt.DOUBLE)
        MPVLib.observeProperty(MpvProp.HWDEC_CURRENT,      MpvFmt.STRING)
        MPVLib.observeProperty(MpvProp.SUB_SCALE,          MpvFmt.DOUBLE)
        MPVLib.observeProperty(MpvProp.SUB_POS,            MpvFmt.INT64)
        MPVLib.observeProperty(MpvProp.VIDEO_PARAMS_W,     MpvFmt.INT64)
        MPVLib.observeProperty(MpvProp.VIDEO_PARAMS_H,     MpvFmt.INT64)
        MPVLib.observeProperty("track-list",               MpvFmt.STRING)
    }

    companion object { private const val TAG = "MpvOptionsConfigurator" }
}

// ponytail: values correspond directly to mpv_format C enum in mpv/client.h
private object MpvFmt {
    const val FLAG   = 3
    const val STRING = 4
    const val DOUBLE = 5
    const val INT64  = 6
}

private object MpvCache {
    const val MAX_BYTES      = "150MiB"
    const val MAX_BACK_BYTES = "50MiB"
    const val SECS           = "60"
}
