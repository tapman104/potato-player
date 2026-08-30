package com.potato.player.engine

import android.content.Context
import android.util.Log
import android.view.Surface
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.atomic.AtomicBoolean

// ---------------------------------------------------------------------------
// MpvWrapper — the ONLY class that calls MPVLib directly.
//
// Responsibilities:
//   • MPV engine lifecycle (create → configure → init → destroy)
//   • Surface attach/detach
//   • Playback commands (load, play, pause, seek, speed, tracks, subtitles)
//   • Event bridge → typed MpvEvent stream
//
// Non-responsibilities (kept out deliberately):
//   • Playback policy (e.g. "seek to recover renderer" — removed)
//   • State persistence, history, preferences
//   • Any Android UI concern
//
// Thread safety:
//   All public fun must be called from the Main thread unless documented
//   otherwise. MPVLib JNI is not safe to call from arbitrary threads.
//   AtomicBoolean `destroyed` guards post-destroy calls.
// ---------------------------------------------------------------------------
class MpvWrapper(context: Context) : MPVLib.EventObserver {

    private val appContext: Context = context.applicationContext
    private val configurator = MpvOptionsConfigurator()

    // ── Event channels ────────────────────────────────────────────────────────
    //
    // Two separate channels with different overflow strategies:
    //
    //   lifecycleEvents — Channel (rendezvous, no drop).
    //     FILE_LOADED, END_FILE, PLAYBACK_RESTART must never be lost.
    //     Capacity = 16 is more than enough; these arrive infrequently.
    //
    //   propertyEvents — SharedFlow with DROP_OLDEST.
    //     time-pos, speed, cache arrive many times per second. It is safe and
    //     desirable to drop stale values; only the latest matters.
    //
    // The upper layer (MpvEventProcessor) merges both streams.

    private val _lifecycleEvents = Channel<MpvEvent.Lifecycle>(
        capacity       = 16,
        onBufferOverflow = BufferOverflow.SUSPEND   // backpressure, never drop
    )
    val lifecycleEvents: Flow<MpvEvent.Lifecycle> = _lifecycleEvents.receiveAsFlow()

    private val _propertyEvents = MutableSharedFlow<MpvEvent.Property>(
        extraBufferCapacity = 64,
        onBufferOverflow    = BufferOverflow.DROP_OLDEST
    )
    val propertyEvents: SharedFlow<MpvEvent.Property> = _propertyEvents.asSharedFlow()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    init {
        MpvFontInstaller.install(appContext)
        MPVLib.create(appContext)
        MPVLib.addObserver(this)
        configurator.initOptions(appContext)
        MPVLib.init()
        configurator.registerPropertyObservers()
    }

    private val destroyed = AtomicBoolean(false)

    /**
     * Tear down MPV completely. Safe to call from any thread; idempotent.
     * After destroy(), all other calls are no-ops.
     */
    fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        // Call MPVLib directly here — the guard in detachSurface() returns early
        // once destroyed == true, so we do the teardown inline.
        MPVLib.setPropertyString(MpvProp.VO,           "null")
        MPVLib.setPropertyString(MpvProp.FORCE_WINDOW, "no")
        MPVLib.detachSurface()
        MPVLib.removeObserver(this)
        MPVLib.destroy()
        _lifecycleEvents.close()
    }

    // ── Surface ───────────────────────────────────────────────────────────────

    /**
     * Attach a new rendering surface. Call when SurfaceHolder.surfaceCreated fires.
     *
     * No seek is performed here. The previous behaviour of issuing an
     * "absolute+exact" seek to "kick" the renderer was wrong: it caused
     * unnecessary decoder work on every rotation/PiP transition and coupled
     * surface recovery to playback state. MPV resumes rendering correctly on
     * its own once the VO is re-pointed to the new surface.
     */
    fun attachSurface(surface: Surface) {
        if (destroyed.get()) return
        if (!surface.isValid) { Log.w(TAG, "attachSurface called with invalid surface"); return }
        MPVLib.attachSurface(surface)
        MPVLib.setPropertyString(MpvProp.FORCE_WINDOW, "yes")
        MPVLib.setPropertyString(MpvProp.VO,           "gpu")
    }

    /**
     * Detach the current rendering surface. Call when SurfaceHolder.surfaceDestroyed fires.
     * VO is switched to null BEFORE detach so MPV stops rendering before the surface dies.
     */
    fun detachSurface() {
        if (destroyed.get()) return
        MPVLib.setPropertyString(MpvProp.VO,           "null")
        MPVLib.setPropertyString(MpvProp.FORCE_WINDOW, "no")
        MPVLib.detachSurface()
    }

    // ── Session management ────────────────────────────────────────────────────

    /**
     * Allocates a new playback generation token. The caller stores this and
     * passes it to [stopIfGeneration] to cancel only the session it started.
     * Renamed from play() — this method does NOT start playback.
     */
    fun nextGeneration(): Int {
        ifAlive("nextGeneration") { return ++playbackGeneration }
        return -1
    }

    private var playbackGeneration: Int = 0

    /**
     * Load and immediately start playing [uri].
     * Replaces the current file if one is loaded.
     */
    fun loadFile(uri: String) {
        ifAlive("loadFile") {
            MPVLib.command("loadfile", uri, "replace")
            MPVLib.setPropertyBoolean(MpvProp.PAUSE, false)
        }
    }

    /**
     * Stops playback only if [gen] matches the current generation.
     * Returns true if the stop was issued.
     */
    fun stopIfGeneration(gen: Int): Boolean {
        if (destroyed.get()) return false
        return if (gen == playbackGeneration) {
            MPVLib.command("stop")
            true
        } else false
    }

    // ── Playback control ──────────────────────────────────────────────────────

    fun pause()      { ifAlive("pause")      { MPVLib.setPropertyBoolean(MpvProp.PAUSE, true) } }
    fun resume()     { ifAlive("resume")     { MPVLib.setPropertyBoolean(MpvProp.PAUSE, false) } }
    fun togglePlay() { ifAlive("togglePlay") { MPVLib.command("cycle", MpvProp.PAUSE) } }

    // ── Seeking ───────────────────────────────────────────────────────────────
    //
    // seekFast    — keyframe-aligned, instant response. Use while scrubbing.
    // seekAccurate — frame-accurate. Use on seek-bar release / resume position.
    // seekRelative — relative keyframe seek. Use for ±10 s skip buttons.
    //
    // The old seekTo(exact: Boolean = true) made every seek exact by default,
    // which caused unnecessary decoder work during drag-to-seek interactions.

    /** Keyframe-aligned seek. Fast; use during continuous scrubbing. */
    fun seekFast(ms: Long) {
        ifAlive("seekFast") {
            MPVLib.command("seek", (ms.coerceAtLeast(0L) / 1000.0).toString(), "absolute+keyframes")
        }
    }

    /** Frame-accurate seek. Use on seek-bar finger-up / resume. */
    fun seekAccurate(ms: Long) {
        ifAlive("seekAccurate") {
            MPVLib.command("seek", (ms.coerceAtLeast(0L) / 1000.0).toString(), "absolute+exact")
        }
    }

    /** Relative keyframe seek. Use for skip-forward / skip-back buttons. */
    fun seekRelative(sec: Double) {
        ifAlive("seekRelative") {
            MPVLib.command("seek", sec.toString(), "relative+keyframes")
        }
    }

    // ── Tracks ────────────────────────────────────────────────────────────────

    fun setAudioTrack(id: Int) {
        ifAlive("setAudioTrack") {
            MPVLib.setPropertyString(MpvProp.AID, if (id == -1) "no" else id.toString())
        }
    }

    fun setSubTrack(id: Int) {
        ifAlive("setSubTrack") {
            MPVLib.setPropertyString(MpvProp.SID, if (id == -1) "no" else id.toString())
        }
    }

    fun addExternalSubtitle(path: String) {
        ifAlive("addExternalSubtitle") { MPVLib.command("sub-add", path, "select") }
    }

    // ── Video / audio parameters ──────────────────────────────────────────────

    fun setSpeed(speed: Double) {
        ifAlive("setSpeed") { MPVLib.setPropertyString(MpvProp.SPEED, speed.toString()) }
    }

    fun setDecoder(hwdec: String) {
        ifAlive("setDecoder") { MPVLib.setPropertyString(MpvProp.HWDEC, hwdec) }
    }

    fun setSubScale(scale: Double) {
        ifAlive("setSubScale") { MPVLib.setPropertyDouble(MpvProp.SUB_SCALE, scale) }
    }

    fun setSubPos(pos: Int) {
        ifAlive("setSubPos") { MPVLib.setPropertyInt(MpvProp.SUB_POS, pos) }
    }

    // ── Internal property accessors (not for callers outside this package) ────
    //
    // These are `internal` so upper layers (ViewModel etc.) are forced to use
    // named commands above instead of poking raw MPV properties. Direct access
    // to MPV properties from outside the engine package breaks the wrapper
    // boundary — that is the architectural problem we are fixing.

    internal fun getPropertyInt(name: String): Int?         = if (destroyed.get()) null else MPVLib.getPropertyInt(name)
    internal fun getPropertyString(name: String): String?   = if (destroyed.get()) null else MPVLib.getPropertyString(name)
    internal fun getPropertyBoolean(name: String): Boolean? = if (destroyed.get()) null else MPVLib.getPropertyBoolean(name)
    internal fun getPropertyDouble(name: String): Double?   = if (destroyed.get()) null else MPVLib.getPropertyDouble(name)

    internal fun setPropertyInt(name: String, value: Int)       { ifAlive("setPropertyInt")    { MPVLib.setPropertyInt(name, value) } }
    internal fun setPropertyDouble(name: String, value: Double) { ifAlive("setPropertyDouble") { MPVLib.setPropertyDouble(name, value) } }
    internal fun setPropertyString(name: String, value: String) { ifAlive("setPropertyString") { MPVLib.setPropertyString(name, value) } }

    // ── Destroyed guard helper ────────────────────────────────────────────────

    private inline fun ifAlive(op: String, block: () -> Unit) {
        if (destroyed.get()) { Log.w(TAG, "Skipping $op — wrapper destroyed"); return }
        block()
    }

    // ── MPVLib.EventObserver callbacks ────────────────────────────────────────
    //
    // These are called from MPV's internal event thread. Only tryEmit/trySend
    // (non-blocking) may be used here.

    override fun eventProperty(name: String) {
        // Unformatted property notification — no value delivered.
        // Intentionally ignored; the formatted overloads below carry the value.
    }

    override fun eventProperty(name: String, value: Boolean) {
        val event = when (name) {
            MpvProp.PAUSE -> MpvEvent.Property.Paused(value)
            else          -> MpvEvent.Property.Unknown(name)
        }
        _propertyEvents.tryEmit(event)
    }

    override fun eventProperty(name: String, value: Double) {
        val msLong = (value * 1000).toLong()
        val event = when (name) {
            MpvProp.TIME_POS             -> MpvEvent.Property.Position(msLong)
            MpvProp.DURATION             -> MpvEvent.Property.Duration(msLong)
            MpvProp.DEMUXER_CACHE_TIME   -> MpvEvent.Property.CacheTime(msLong)
            MpvProp.DEMUXER_CACHE_DURATION -> MpvEvent.Property.CacheTime(msLong)
            MpvProp.SPEED                -> MpvEvent.Property.Speed(value)
            MpvProp.SUB_SCALE            -> MpvEvent.Property.SubScale(value)
            else                         -> MpvEvent.Property.Unknown(name)
        }
        _propertyEvents.tryEmit(event)
    }

    override fun eventProperty(name: String, value: Long) {
        val event = when (name) {
            MpvProp.SUB_POS       -> MpvEvent.Property.SubPos(value)
            MpvProp.VIDEO_PARAMS_W -> MpvEvent.Property.VideoWidth(value)
            MpvProp.VIDEO_PARAMS_H -> MpvEvent.Property.VideoHeight(value)
            else                   -> MpvEvent.Property.Unknown(name)
        }
        _propertyEvents.tryEmit(event)
    }

    override fun eventProperty(name: String, value: String) {
        val event = when (name) {
            MpvProp.TRACK_LIST    -> MpvEvent.Property.TrackList(value)
            MpvProp.HWDEC_CURRENT -> MpvEvent.Property.HwdecActive(value)
            else                   -> MpvEvent.Property.Unknown(name)
        }
        _propertyEvents.tryEmit(event)
    }

    override fun eventProperty(name: String, value: MPVNode) {
        // MPVNode properties not observed — intentionally ignored.
    }

    override fun event(eventId: Int, eventNode: MPVNode) {
        val lifecycle = when (eventId) {
            MpvEventId.FILE_LOADED      -> MpvEvent.Lifecycle.FileLoaded
            MpvEventId.END_FILE         -> MpvEvent.Lifecycle.EndFile
            MpvEventId.PLAYBACK_RESTART -> MpvEvent.Lifecycle.PlaybackRestart
            else                        -> MpvEvent.Lifecycle.Unknown(eventId)
        }
        // trySend on a Channel with SUSPEND overflow; will succeed unless the
        // consumer is catastrophically backed up (16-slot buffer).
        _lifecycleEvents.trySend(lifecycle)
    }

    companion object { private const val TAG = "MpvWrapper" }
}
