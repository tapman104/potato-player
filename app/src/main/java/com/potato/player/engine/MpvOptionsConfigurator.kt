package com.potato.player.engine

import android.content.Context
import android.util.Log
import `is`.xyz.mpv.MPVLib

// ---------------------------------------------------------------------------
// MpvFontInstaller — single responsibility: font asset deployment.
// Extracted from MpvOptionsConfigurator (SRP violation fix).
// ---------------------------------------------------------------------------
internal object MpvFontInstaller {

    private const val TAG          = "MpvFontInstaller"
    private const val FONT_ASSET   = "Roboto-Regular.ttf"
    // Bump FONT_VERSION whenever the bundled font changes.
    // The installed file is named with the version embedded so the
    // existence check alone is sufficient — no .available() call needed.
    private const val FONT_VERSION = 1
    private val FONT_FILE_NAME     get() = "mpv_font_v$FONT_VERSION.ttf"

    /**
     * Copies the bundled font to [Context.filesDir]/fonts if absent.
     *
     * Staleness is determined by version: if [FONT_FILE_NAME] already exists
     * the copy is skipped entirely. Old versioned font files are deleted to
     * prevent accumulation across app updates.
     *
     * MUST be called from a background thread (disk I/O).
     */
    fun install(context: Context) {
        val fontsDir = java.io.File(context.filesDir, "fonts")
        if (!fontsDir.exists()) fontsDir.mkdirs()

        val fontFile = java.io.File(fontsDir, FONT_FILE_NAME)

        // Clean up stale versions (any mpv_font_v*.ttf that isn't current).
        fontsDir.listFiles { f ->
            f.name.startsWith("mpv_font_v") && f.name.endsWith(".ttf") && f.name != FONT_FILE_NAME
        }?.forEach { stale ->
            stale.delete()
            Log.d(TAG, "Deleted stale font: ${stale.name}")
        }

        if (fontFile.exists()) return  // already installed and up-to-date

        try {
            context.assets.open(FONT_ASSET).use { src ->
                fontFile.outputStream().use { src.copyTo(it) }
            }
            Log.d(TAG, "Font installed: $FONT_FILE_NAME")
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
        MPVLib.observeProperty(MpvProp.TRACK_LIST,              MpvFmt.STRING)
        MPVLib.observeProperty(MpvProp.PAUSED_FOR_CACHE,        MpvFmt.FLAG)
        MPVLib.observeProperty(MpvProp.CACHE_BUFFERING_STATE,   MpvFmt.INT64)
    }
}

/** Cache sizing. Adjust after profiling on target device classes. */
private object MpvCache {
    const val MAX_BYTES      = "150MiB"
    const val MAX_BACK_BYTES = "50MiB"
    const val SECS           = "60"
}
