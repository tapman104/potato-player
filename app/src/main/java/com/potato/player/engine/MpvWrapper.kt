package com.potato.player.engine

import android.content.Context
import android.view.Surface
import android.view.SurfaceHolder
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean

class MpvWrapper(context: Context) : MPVLib.EventObserver {

    private val appContext: Context = context.applicationContext

    // DROP_OLDEST ensures the buffer never blocks the MPV event thread and never
    // silently returns false. Chatty property events (time-pos, etc.) are shed first;
    // infrequent lifecycle events (FILE_LOADED, END_FILE) always get a slot.
    private val configurator = MpvOptionsConfigurator()

    private val _events = MutableSharedFlow<MpvEvent>(
        extraBufferCapacity = 128,
        onBufferOverflow    = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<MpvEvent> = _events.asSharedFlow()

    init {
        configurator.copyFontAssets(appContext)
        MPVLib.create(appContext)
        MPVLib.addObserver(this)
        
        configurator.initOptions(appContext)
        
        configurator.postInitOptions()
        configurator.registerPropertyObservers()
        
        MPVLib.init()
    }

    @Volatile private var playbackGeneration: Int = 0

    fun attachSurface(surface: Surface) {
        if (destroyed.get()) { android.util.Log.w("MpvWrapper", "Skipping attachSurface — wrapper already destroyed"); return }
        if (!surface.isValid) return
        MPVLib.attachSurface(surface)
        MPVLib.setPropertyString("force-window", "yes")
        MPVLib.setPropertyString("vo", "gpu")
    }

    fun reattachRenderer() {
        if (destroyed.get()) return
        MPVLib.setPropertyString("force-window", "yes")
        MPVLib.setPropertyString("vo", "gpu")
        MPVLib.setPropertyBoolean("pause", false)
    }

    fun detachSurface() {
        if (destroyed.get()) { android.util.Log.w("MpvWrapper", "Skipping detachSurface — wrapper already destroyed"); return }
        MPVLib.setPropertyBoolean("pause", true)
        MPVLib.setPropertyString("force-window", "yes")
        MPVLib.detachSurface()
    }

    fun play(): Int {
        if (destroyed.get()) { android.util.Log.w("MpvWrapper", "Skipping play — wrapper already destroyed"); return -1 }
        return ++playbackGeneration
    }

    fun loadFile(uri: String) {
        if (destroyed.get()) { android.util.Log.w("MpvWrapper", "Skipping loadFile — wrapper already destroyed"); return }
        MPVLib.command("loadfile", uri, "replace")
        MPVLib.setPropertyBoolean(MpvProp.PAUSE, false)
    }

    fun stopIfGeneration(gen: Int): Boolean {
        if (destroyed.get()) return false
        if (gen == playbackGeneration) {
            MPVLib.command("stop")
            return true
        }
        return false
    }

    fun pause() {
        if (destroyed.get()) { android.util.Log.w("MpvWrapper", "Skipping pause — wrapper already destroyed"); return }
        MPVLib.setPropertyBoolean(MpvProp.PAUSE, true)
    }

    fun togglePlay() {
        if (destroyed.get()) { android.util.Log.w("MpvWrapper", "Skipping togglePlay — wrapper already destroyed"); return }
        MPVLib.command("cycle", MpvProp.PAUSE)
    }

    fun resume() {
        if (destroyed.get()) { android.util.Log.w("MpvWrapper", "Skipping resume — wrapper already destroyed"); return }
        MPVLib.setPropertyBoolean(MpvProp.PAUSE, false)
    }

    fun seekTo(ms: Long) {
        if (destroyed.get()) { android.util.Log.w("MpvWrapper", "Skipping seekTo — wrapper already destroyed"); return }
        val safeMs = ms.coerceAtLeast(0L)
        MPVLib.command("seek", (safeMs / 1000.0).toString(), "absolute+exact")
    }

    fun seekRelative(sec: Double) {
        if (destroyed.get()) { android.util.Log.w("MpvWrapper", "Skipping seekRelative — wrapper already destroyed"); return }
        MPVLib.command("seek", sec.toString(), "relative+exact")
    }

    fun setAudioTrack(id: Int) {
        if (destroyed.get()) { android.util.Log.w("MpvWrapper", "Skipping setAudioTrack — wrapper already destroyed"); return }
        MPVLib.setPropertyString(MpvProp.AID, if (id == -1) "no" else id.toString())
    }

    fun setSubTrack(id: Int) {
        if (destroyed.get()) { android.util.Log.w("MpvWrapper", "Skipping setSubTrack — wrapper already destroyed"); return }
        val valStr = if (id == -1) "no" else id.toString()
        MPVLib.setPropertyString(MpvProp.SID, valStr)
    }

    fun setSpeed(speed: Double) {
        if (destroyed.get()) { android.util.Log.w("MpvWrapper", "Skipping setSpeed — wrapper already destroyed"); return }
        MPVLib.setPropertyString(MpvProp.SPEED, speed.toString())
    }

    fun setDecoder(hwdec: String) {
        if (destroyed.get()) { android.util.Log.w("MpvWrapper", "Skipping setDecoder — wrapper already destroyed"); return }
        MPVLib.setPropertyString(MpvProp.HWDEC, hwdec)
    }

    fun setSubScale(scale: Double) {
        if (destroyed.get()) { android.util.Log.w("MpvWrapper", "Skipping setSubScale — wrapper already destroyed"); return }
        MPVLib.setPropertyDouble(MpvProp.SUB_SCALE, scale)
    }

    fun setSubPos(pos: Int) {
        if (destroyed.get()) { android.util.Log.w("MpvWrapper", "Skipping setSubPos — wrapper already destroyed"); return }
        MPVLib.setPropertyInt(MpvProp.SUB_POS, pos)
    }

    fun addExternalSubtitle(path: String) {
        if (destroyed.get()) { android.util.Log.w("MpvWrapper", "Skipping addExternalSubtitle — wrapper already destroyed"); return }
        MPVLib.command("sub-add", path, "select")
    }

    fun getPropertyInt(name: String): Int? {
        if (destroyed.get()) return null
        return MPVLib.getPropertyInt(name)
    }
    fun getPropertyString(name: String): String? {
        if (destroyed.get()) return null
        return MPVLib.getPropertyString(name)
    }
    fun getPropertyBoolean(name: String): Boolean? {
        if (destroyed.get()) return null
        return MPVLib.getPropertyBoolean(name)
    }

    fun setPropertyInt(name: String, value: Int) {
        if (destroyed.get()) { android.util.Log.w("MpvWrapper", "Skipping setPropertyInt — wrapper already destroyed"); return }
        MPVLib.setPropertyInt(name, value)
    }

    fun setPropertyDouble(name: String, value: Double) {
        if (destroyed.get()) { android.util.Log.w("MpvWrapper", "Skipping setPropertyDouble — wrapper already destroyed"); return }
        MPVLib.setPropertyDouble(name, value)
    }

    fun setPropertyString(name: String, value: String) {
        if (destroyed.get()) { android.util.Log.w("MpvWrapper", "Skipping setPropertyString — wrapper already destroyed"); return }
        MPVLib.setPropertyString(name, value)
    }

    // AtomicBoolean makes the check-then-set atomic, preventing two concurrent
    // destroy() calls from both passing the guard before either sets the flag.
    private val destroyed = AtomicBoolean(false)

    fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        // Bypass the destroyed guard in detachSurface() by calling MPV directly.
        // detachSurface() checks destroyed == true and returns early, so we must
        // do the teardown inline here before the flag is read by anything else.
        MPVLib.setPropertyString("vo", "null")
        MPVLib.setPropertyString("force-window", "no")
        MPVLib.detachSurface()
        MPVLib.removeObserver(this)
        MPVLib.destroy()
    }

    override fun eventProperty(name: String) {}
    override fun eventProperty(name: String, value: Long) { _events.tryEmit(MpvEvent.PropertyLong(name, value)) }
    override fun eventProperty(name: String, value: Boolean) {
        _events.tryEmit(MpvEvent.PropertyBool(name, value))
    }
    override fun eventProperty(name: String, value: String) { _events.tryEmit(MpvEvent.PropertyString(name, value)) }
    override fun eventProperty(name: String, value: Double) { _events.tryEmit(MpvEvent.PropertyDouble(name, value)) }
    override fun eventProperty(name: String, value: MPVNode) {}

    override fun event(eventId: Int, eventNode: MPVNode) {
        _events.tryEmit(MpvEvent.Id(eventId))
    }
}
