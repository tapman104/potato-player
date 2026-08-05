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

    val appContext: Context = context.applicationContext

    // DROP_OLDEST ensures the buffer never blocks the MPV event thread and never
    // silently returns false. Chatty property events (time-pos, etc.) are shed first;
    // infrequent lifecycle events (FILE_LOADED, END_FILE) always get a slot.
    private val _events = MutableSharedFlow<MpvEvent>(
        extraBufferCapacity = 128,
        onBufferOverflow    = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<MpvEvent> = _events.asSharedFlow()

    private val configurator = MpvOptionsConfigurator()

    // Tri-state: null = no pause event received yet from MPV, true/false = last known value.
    @Volatile private var cachedPause: Boolean? = null

    init {
        configurator.copyFontAssets(appContext)
        MPVLib.create(appContext)
        MPVLib.addObserver(this)
        
        configurator.initOptions(appContext)
        
        configurator.postInitOptions()
        configurator.registerPropertyObservers()
        
        MPVLib.init()
    }

    var onSurfaceReady: (() -> Unit)? = null

    val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            attachSurface(holder.surface)
            onSurfaceReady?.invoke()
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            if (width > 0 && height > 0) {
                MPVLib.setPropertyString("android-surface-size", "${width}x${height}")
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            detachSurface()
        }
    }

    private var currentSurface: Surface? = null

    fun attachSurface(surface: Surface) {
        if (!surface.isValid) return
        currentSurface = surface
        MPVLib.attachSurface(surface)
        MPVLib.setOptionString("force-window", "yes")
        MPVLib.setPropertyString("vo", "gpu")
    }

    fun detachSurface() {
        // Tell the VO to stop rendering before physically removing the surface.
        // Reversing this order can cause MPV to write to an already-released surface.
        MPVLib.setPropertyString("vo", "null")
        MPVLib.setPropertyString("force-window", "no")
        MPVLib.detachSurface()
        currentSurface = null
    }

    fun play(uri: String) {
        MPVLib.command("loadfile", uri, "replace")
        MPVLib.setPropertyBoolean(MpvProp.PAUSE, false)
    }

    fun pause() {
        MPVLib.setPropertyBoolean(MpvProp.PAUSE, true)
    }

    fun togglePlay() {
        MPVLib.command("cycle", MpvProp.PAUSE)
    }

    fun resume() {
        MPVLib.setPropertyBoolean(MpvProp.PAUSE, false)
    }

    fun seekTo(ms: Long) {
        val safeMs = ms.coerceAtLeast(0L)
        MPVLib.command("seek", (safeMs / 1000.0).toString(), "absolute+exact")
    }

    fun seekRelative(sec: Double) {
        MPVLib.command("seek", sec.toString(), "relative+exact")
    }

    fun setAudioTrack(id: Int) {
        MPVLib.setPropertyString(MpvProp.AID, if (id == -1) "no" else id.toString())
    }

    fun setSubTrack(id: Int) {
        val valStr = if (id == -1) "no" else id.toString()
        MPVLib.setPropertyString(MpvProp.SID, valStr)
    }

    fun setSpeed(speed: Double) {
        MPVLib.setPropertyString(MpvProp.SPEED, speed.toString())
    }

    fun setDecoder(hwdec: String) {
        MPVLib.setPropertyString(MpvProp.HWDEC, hwdec)
    }

    fun setSubScale(scale: Double) {
        MPVLib.setPropertyDouble(MpvProp.SUB_SCALE, scale)
    }

    fun setSubPos(pos: Int) {
        MPVLib.setPropertyInt(MpvProp.SUB_POS, pos)
    }

    fun addExternalSubtitle(path: String) {
        MPVLib.command("sub-add", path, "select")
    }

    fun getPropertyInt(name: String): Int? = MPVLib.getPropertyInt(name)
    fun getPropertyString(name: String): String? = MPVLib.getPropertyString(name)
    fun getPropertyBoolean(name: String): Boolean? = MPVLib.getPropertyBoolean(name)

    fun setPropertyInt(name: String, value: Int) {
        MPVLib.setPropertyInt(name, value)
    }

    fun setPropertyDouble(name: String, value: Double) {
        MPVLib.setPropertyDouble(name, value)
    }

    fun setPropertyString(name: String, value: String) {
        MPVLib.setPropertyString(name, value)
    }

    // AtomicBoolean makes the check-then-set atomic, preventing two concurrent
    // destroy() calls from both passing the guard before either sets the flag.
    private val destroyed = AtomicBoolean(false)

    fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        detachSurface()
        MPVLib.removeObserver(this)
        MPVLib.destroy()
    }

    override fun eventProperty(name: String) {}
    override fun eventProperty(name: String, value: Long) { _events.tryEmit(MpvEvent.PropertyLong(name, value)) }
    override fun eventProperty(name: String, value: Boolean) {
        if (name == MpvProp.PAUSE) cachedPause = value
        _events.tryEmit(MpvEvent.PropertyBool(name, value))
    }
    override fun eventProperty(name: String, value: String) { _events.tryEmit(MpvEvent.PropertyString(name, value)) }
    override fun eventProperty(name: String, value: Double) { _events.tryEmit(MpvEvent.PropertyDouble(name, value)) }
    override fun eventProperty(name: String, value: MPVNode) {}

    override fun event(eventId: Int, eventNode: MPVNode) {
        _events.tryEmit(MpvEvent.Id(eventId))
    }
}
