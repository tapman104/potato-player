package com.potato.player.engine

import android.content.Context
import android.util.Log
import `is`.xyz.mpv.MPVLib

// ---------------------------------------------------------------------------
// MpvFontInstaller — single responsibility: font asset deployment.
// Extracted from MpvOptionsConfigurator (SRP violation fix).
// ---------------------------------------------------------------------------
internal object MpvFontInstaller {

    private const val TAG       = "MpvFontInstaller"
    private const val FONT_NAME = "Roboto-Regular.ttf"

    /**
     * Copies the bundled font to [Context.filesDir]/fonts if absent or stale.
     * Size comparison is used as a lightweight staleness check — a full hash
     * would be more robust but is overkill for a single font file.
     */
    fun install(context: Context) {
        val fontsDir  = java.io.File(context.filesDir, "fonts")
        if (!fontsDir.exists()) fontsDir.mkdirs()

        val fontFile = java.io.File(fontsDir, FONT_NAME)
        try {
            val assetSize = context.assets.open(FONT_NAME).use { it.available().toLong() }
            if (!fontFile.exists() || fontFile.length() != assetSize) {
                context.assets.open(FONT_NAME).use { src ->
                    fontFile.outputStream().use { src.copyTo(it) }
                }
                Log.d(TAG, "Font installed (size changed or missing)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install font asset", e)
        }
    }
}

// ---------------------------------------------------------------------------
// MpvOptionsConfigurator — configures MPV engine options and observers.
// Only concerns: set MPV options/properties and register observers.
// Does NOT touch the filesystem (delegated to MpvFontInstaller).
// ---------------------------------------------------------------------------
internal class MpvOptionsConfigurator {

    /**
     * Set all startup options. Must be called after MPVLib.create() and
     * before MPVLib.init(). Uses setOptionString for true init-time options,
     * setPropertyBoolean only for the two runtime-safe properties that have
     * no option equivalent (keep-open, input-default-bindings).
     */
    fun initOptions(context: Context) {
        val filesDir = context.filesDir.path

        // ── Core ─────────────────────────────────────────────────────────────
        MPVLib.setOptionString("config",     "yes")
        MPVLib.setOptionString("config-dir", filesDir)
        MPVLib.setOptionString("idle",       "yes")

        // ── Video output ─────────────────────────────────────────────────────
        MPVLib.setOptionString("profile",     "fast")
        MPVLib.setOptionString("vo",          "gpu")
        MPVLib.setOptionString("gpu-context", "android")

        // ── Hardware decoding ─────────────────────────────────────────────────
        // mediacodec-copy: HW decode → CPU copy → GPU texture.
        // Keeps the rendering path simple; swap to "mediacodec" (zero-copy) only
        // after benchmarking confirms zero-copy is stable on target devices.
        MPVLib.setOptionString("hwdec",        "mediacodec-copy")
        MPVLib.setOptionString("hwdec-codecs", "all")

        // ── Cache ─────────────────────────────────────────────────────────────
        MPVLib.setOptionString("demuxer-max-bytes",      MpvCache.MAX_BYTES)
        MPVLib.setOptionString("demuxer-max-back-bytes", MpvCache.MAX_BACK_BYTES)
        MPVLib.setOptionString("cache-secs",             MpvCache.SECS)

        // ── Rendering ─────────────────────────────────────────────────────────
        MPVLib.setOptionString("opengl-early-flush",  "no")
        MPVLib.setOptionString("video-sync",          "display-resample")
        MPVLib.setOptionString("scale",               "bilinear")
        MPVLib.setOptionString("cscale",              "bilinear")
        MPVLib.setOptionString("dscale",              "bilinear")
        MPVLib.setOptionString("deband",              "no")
        MPVLib.setOptionString("vd-lavc-threads",     "0")
        MPVLib.setOptionString("vd-lavc-film-grain",  "cpu")

        // ── Subtitles ─────────────────────────────────────────────────────────
        MPVLib.setOptionString("sub-font-provider", "none")
        MPVLib.setOptionString("sub-fonts-dir",     "$filesDir/fonts")
        MPVLib.setOptionString("sub-font",          "Roboto")
        MPVLib.setOptionString("sub-font-size",     "55")
        MPVLib.setOptionString("sub-bold",          "yes")
        MPVLib.setOptionString("sub-color",         "#FFFFFF")
        MPVLib.setOptionString("sub-border-color",  "#000000")
        MPVLib.setOptionString("sub-border-size",   "3")
        MPVLib.setOptionString("sub-auto",          "no")

        // ── Audio ─────────────────────────────────────────────────────────────
        MPVLib.setOptionString("audio-pitch-correction", "yes")

        // ── Logging ───────────────────────────────────────────────────────────
        MPVLib.setOptionString("msg-level", "all=warn")

        // ── Runtime-only properties (no setOption equivalent) ─────────────────
        MPVLib.setPropertyBoolean("keep-open",              true)
        MPVLib.setPropertyBoolean("input-default-bindings", true)
    }

    /**
     * Registers the property observers that feed the event stream.
     * Must be called after MPVLib.init().
     */
    fun registerPropertyObservers() {
        MPVLib.observeProperty(MpvProp.PAUSE,                MpvFmt.FLAG)
        MPVLib.observeProperty(MpvProp.TIME_POS,             MpvFmt.DOUBLE)
        MPVLib.observeProperty(MpvProp.DURATION,             MpvFmt.DOUBLE)
        MPVLib.observeProperty(MpvProp.DEMUXER_CACHE_TIME,   MpvFmt.DOUBLE)
        MPVLib.observeProperty(MpvProp.DEMUXER_CACHE_DURATION, MpvFmt.DOUBLE)
        MPVLib.observeProperty(MpvProp.SPEED,                MpvFmt.DOUBLE)
        MPVLib.observeProperty(MpvProp.HWDEC_CURRENT,        MpvFmt.STRING)
        MPVLib.observeProperty(MpvProp.SUB_SCALE,            MpvFmt.DOUBLE)
        MPVLib.observeProperty(MpvProp.SUB_POS,              MpvFmt.INT64)
        MPVLib.observeProperty(MpvProp.VIDEO_PARAMS_W,       MpvFmt.INT64)
        MPVLib.observeProperty(MpvProp.VIDEO_PARAMS_H,       MpvFmt.INT64)
        MPVLib.observeProperty(MpvProp.TRACK_LIST,           MpvFmt.STRING)
        MPVLib.observeProperty("paused-for-cache",           MpvFmt.FLAG)
        MPVLib.observeProperty("cache-buffering-state",      MpvFmt.INT64)
    }
}

// ---------------------------------------------------------------------------
// Internal constants — not part of the public API surface.
// ---------------------------------------------------------------------------

/** mpv_format values from mpv/client.h */
private object MpvFmt {
    const val FLAG   = 3
    const val STRING = 4
    const val DOUBLE = 5
    const val INT64  = 6
}

/** Cache sizing. Adjust after profiling on target device classes. */
private object MpvCache {
    const val MAX_BYTES      = "150MiB"
    const val MAX_BACK_BYTES = "50MiB"
    const val SECS           = "60"
}
