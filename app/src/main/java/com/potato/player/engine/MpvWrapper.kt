package com.potato.player.engine

import android.content.Context
import android.util.Log
import android.view.Surface
import `is`.xyz.mpv.MPVLib
import com.potato.player.domain.PlayerController
import `is`.xyz.mpv.MPVNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
class MpvWrapper(context: Context) : MPVLib.EventObserver, PlayerController {

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
    //   engineState — StateFlow.
    //     time-pos, speed, cache arrive many times per second. It is safe and
    //     desirable to drop stale values; only the latest matters.
    //
    // The upper layer (MpvEventProcessor) merges both streams.

    // Relay: unbounded, trySend never fails
    private val _lifecycleRelay = Channel<MpvEvent.Lifecycle>(Channel.UNLIMITED)

    // What callers observe — rendezvous (or small buffer), send() is suspending
    private val _lifecycleEvents = Channel<MpvEvent.Lifecycle>(16)
    val lifecycleEvents: Flow<MpvEvent.Lifecycle> = _lifecycleEvents.receiveAsFlow()

    private val _engineState = MutableStateFlow(PlayerEngineState())
    val engineState: StateFlow<PlayerEngineState> = _engineState.asStateFlow()

    /** Dispatcher coroutine — started in init, cancelled in destroy() */
    private val relayScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var lastPosSendMs = 0L

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    init {
        MPVLib.create(appContext)
        MPVLib.addObserver(this)
        configurator.initOptions(appContext)
        MPVLib.init()
        configurator.registerPropertyObservers()

        relayScope.launch {
            for (event in _lifecycleRelay) {
                _lifecycleEvents.send(event)   // suspending — never drops
            }
        }
    }

    private val destroyed = AtomicBoolean(false)

    /**
     * Tear down MPV completely. Safe to call from any thread; idempotent.
     * After destroy(), all other calls are no-ops.
     */
    fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        // Call detachSurfaceInternal() directly here — the guard in detachSurface() returns early
        // once destroyed == true, so we do the teardown inline via the shared helper.
        detachSurfaceInternal()
        MPVLib.removeObserver(this)
        MPVLib.destroy()

        relayScope.cancel()
        _lifecycleRelay.close()
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
    override fun attachSurface(surface: Surface) {
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
    override fun detachSurface() {
        if (destroyed.get()) return
        detachSurfaceInternal()
    }

    /**
     * Shared teardown: set VO→null, clear force-window, then detach.
     * Called from both detachSurface() (live) and destroy() (bypasses the
     * destroyed guard directly since the flag is already set by then).
     */
    private fun detachSurfaceInternal() {
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
    override fun loadFile(uri: String) {
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

    override fun pause()      { ifAlive("pause")      { MPVLib.setPropertyBoolean(MpvProp.PAUSE, true) } }
    fun resume()              { ifAlive("resume")     { MPVLib.setPropertyBoolean(MpvProp.PAUSE, false) } }
    override fun play()       { resume() }
    override fun togglePlay() { ifAlive("togglePlay") { MPVLib.command("cycle", MpvProp.PAUSE) } }

    // ── Seeking ───────────────────────────────────────────────────────────────
    //
    // seekFast    — keyframe-aligned, instant response. Use while scrubbing.
    // seekAccurate — frame-accurate. Use on seek-bar release / resume position.
    // seekRelative — relative keyframe seek. Use for ±10 s skip buttons.
    //
    // The old seekTo(exact: Boolean = true) made every seek exact by default,
    // which caused unnecessary decoder work during drag-to-seek interactions.

    /** Keyframe-aligned seek. Fast; use during continuous scrubbing. */
    override fun seekFast(ms: Long) {
        ifAlive("seekFast") {
            MPVLib.command("seek", (ms.coerceAtLeast(0L) / 1000.0).toString(), "absolute+keyframes")
        }
    }

    /** Frame-accurate seek. Use on seek-bar finger-up / resume. */
    override fun seekAccurate(ms: Long) {
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

    fun setVolume(percent: Int) {
        ifAlive("setVolume") { MPVLib.setPropertyInt(MpvProp.VOLUME, percent) }
    }

    fun setSubtitleScale(scale: Double) {
        ifAlive("setSubtitleScale") { MPVLib.setPropertyDouble(MpvProp.SUB_SCALE, scale) }
    }

    fun setSubtitlePosition(pos: Int) {
        ifAlive("setSubtitlePosition") { MPVLib.setPropertyInt(MpvProp.SUB_POS, pos) }
    }

    fun setRotation(degrees: Int) {
        ifAlive("setRotation") { MPVLib.setPropertyInt(MpvProp.VIDEO_ROTATE, degrees) }
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
        when (name) {
            MpvProp.PAUSE          -> _engineState.update { it.copy(paused = value) }
            MpvProp.PAUSED_FOR_CACHE -> _engineState.update { it.copy(pausedForCache = value) }
        }
    }

    override fun eventProperty(name: String, value: Double) {
        val msLong = (value * 1000).toLong()
        when (name) {
            MpvProp.TIME_POS             -> {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastPosSendMs >= 200L) {
                    lastPosSendMs = now
                    _engineState.update { it.copy(positionMs = msLong) }
                }
            }
            MpvProp.DURATION             -> _engineState.update { it.copy(durationMs = msLong) }
            MpvProp.DEMUXER_CACHE_TIME   -> _engineState.update { it.copy(cacheTimeMs = msLong) }
            MpvProp.DEMUXER_CACHE_DURATION -> _engineState.update { it.copy(cacheDurMs = msLong) }
            MpvProp.SPEED                -> _engineState.update { it.copy(speed = value) }
            MpvProp.SUB_SCALE            -> _engineState.update { it.copy(subScale = value) }
        }
    }

    override fun eventProperty(name: String, value: Long) {
        when (name) {
            MpvProp.SUB_POS               -> _engineState.update { it.copy(subPos = value) }
            MpvProp.VIDEO_PARAMS_W         -> _engineState.update { it.copy(videoWidth = value) }
            MpvProp.VIDEO_PARAMS_H         -> _engineState.update { it.copy(videoHeight = value) }
            MpvProp.CACHE_BUFFERING_STATE  -> _engineState.update { it.copy(cacheBufferingState = value.toInt()) }
        }
    }

    override fun eventProperty(name: String, value: String) {
        when (name) {
            MpvProp.TRACK_LIST    -> _engineState.update { it.copy(trackListJson = value) }
            MpvProp.HWDEC_CURRENT -> _engineState.update { it.copy(hwdecActive = value) }
        }
    }

    override fun eventProperty(name: String, value: MPVNode) {
        // MPVNode properties not observed — intentionally ignored.
    }

    override fun event(eventId: Int, eventNode: MPVNode) {
        val lifecycle = when (eventId) {
            MpvEventId.FILE_LOADED      -> MpvEvent.Lifecycle.FileLoaded
            MpvEventId.END_FILE         -> {
                val reason = (eventNode as? MPVNode.MapNode)?.value?.get("reason")?.let { (it as? MPVNode.IntNode)?.value?.toInt() } ?: 0
                MpvEvent.Lifecycle.EndFile(reason)
            }
            MpvEventId.PLAYBACK_RESTART -> MpvEvent.Lifecycle.PlaybackRestart
            else                        -> MpvEvent.Lifecycle.Unknown(eventId)
        }
        _lifecycleRelay.trySend(lifecycle)
    }

    companion object {
        private const val TAG = "MpvWrapper"

        /**
         * Installs font assets on [Dispatchers.IO]. Must be called before the
         * first subtitle is rendered; safe to call concurrently with MPV init
         * because MPV reads sub-fonts-dir lazily at subtitle render time.
         *
         * Call from Activity.onCreate() using lifecycleScope.
         */
        fun installAssets(appContext: Context, scope: CoroutineScope) {
            scope.launch(Dispatchers.IO) {
                MpvFontInstaller.install(appContext)
            }
        }
    }
}
