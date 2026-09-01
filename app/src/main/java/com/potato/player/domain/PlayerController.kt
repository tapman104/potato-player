package com.potato.player.domain

import android.view.Surface

/**
 * Abstraction over the MPV engine for playback commands.
 *
 * All implementations must be called from the Main thread unless
 * the specific method documents otherwise.
 */
interface PlayerController {

    /** Attach a rendering [surface]. Call when the SurfaceHolder becomes available. */
    fun attachSurface(surface: Surface)

    /** Detach the current rendering surface. Call when the SurfaceHolder is destroyed. */
    fun detachSurface()

    /**
     * Load and immediately start playing the media at [uri].
     * Replaces any currently loaded file.
     */
    fun loadFile(uri: String)

    /** Resume playback (un-pause). */
    fun play()

    /** Pause playback. */
    fun pause()

    /** Toggle between paused and playing states. */
    fun togglePlay()

    /**
     * Keyframe-aligned seek to [ms] milliseconds from the start.
     * Fast; use during continuous scrubbing.
     */
    fun seekFast(ms: Long)

    /**
     * Frame-accurate seek to [ms] milliseconds from the start.
     * Use on seek-bar finger-up or resume position restore.
     */
    fun seekAccurate(ms: Long)
}
