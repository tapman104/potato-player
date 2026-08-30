package com.potato.player.feature.player

import com.potato.player.engine.MpvEvent
import com.potato.player.engine.MpvEventId
import com.potato.player.engine.MpvProp

class MpvEventProcessor(
    private val onPlaybackStarted: () -> Unit,
    private val onPlaybackPaused: () -> Unit,
    // Fired on PLAYBACK_RESTART (seek completion). The ViewModel must query the real
    // PAUSE property here rather than assuming playback is active, because seeks also
    // fire PLAYBACK_RESTART while the player is paused.
    private val onPlaybackRestart: () -> Unit,
    private val onDurationChanged: (ms: Long) -> Unit,
    private val onPositionChanged: (ms: Long) -> Unit,
    private val onTracksChanged: (json: String) -> Unit,
    private val onHwdecChanged: (mode: String) -> Unit,
    private val onEndFileReached: (reason: Int) -> Unit,
    private val onFileLoaded: () -> Unit,
    private val onCacheTimeChanged: (sec: Double) -> Unit,
    private val onCacheDurationChanged: (sec: Double) -> Unit,
    private val onSpeedChanged: (speed: Double) -> Unit,
    private val onSubScaleChanged: (scale: Double) -> Unit,
    private val onSubPosChanged: (pos: Int) -> Unit,
    private val onVideoWidthChanged: (w: Int) -> Unit,
    private val onVideoHeightChanged: (h: Int) -> Unit,
    private val onVolumeChanged: (v: Int) -> Unit
) {
    fun process(event: MpvEvent) {
        when (event) {
            is MpvEvent.Lifecycle -> {
                when (event) {
                    is MpvEvent.Lifecycle.FileLoaded -> onFileLoaded()
                    is MpvEvent.Lifecycle.PlaybackRestart -> onPlaybackRestart()
                    is MpvEvent.Lifecycle.EndFile -> onEndFileReached(event.reason)
                    is MpvEvent.Lifecycle.Unknown -> {}
                }
        }
    }
}
