package com.potato.player.feature.player

import com.potato.player.engine.MpvEvent
import com.potato.player.engine.MpvEventId
import com.potato.player.engine.MpvProp

class MpvEventProcessor(
    private val onPlaybackStarted: () -> Unit,
    private val onPlaybackPaused: () -> Unit,
    private val onDurationChanged: (ms: Long) -> Unit,
    private val onPositionChanged: (ms: Long) -> Unit,
    private val onTracksChanged: (json: String) -> Unit,
    private val onHwdecChanged: (mode: String) -> Unit,
    private val onIdleEntered: () -> Unit,
    private val onEndFileReached: () -> Unit,
    private val onFileLoaded: () -> Unit,
    private val onCacheTimeChanged: (sec: Double) -> Unit,
    private val onCacheDurationChanged: (sec: Double) -> Unit,
    private val onSpeedChanged: (speed: Double) -> Unit,
    private val onSubScaleChanged: (scale: Double) -> Unit,
    private val onSubPosChanged: (pos: Int) -> Unit,
    private val onVideoWidthChanged: (w: Int) -> Unit,
    private val onVideoHeightChanged: (h: Int) -> Unit
) {
    fun process(event: MpvEvent) {
        when (event) {
            is MpvEvent.Id -> {
                when (event.id) {
                    MpvEventId.FILE_LOADED -> onFileLoaded()
                    MpvEventId.PLAYBACK_RESTART -> onPlaybackStarted()
                    MpvEventId.END_FILE -> onEndFileReached()
                    // If IDLE was a valid MpvEventId, we could map it to onIdleEntered
                }
            }
            is MpvEvent.PropertyBool -> {
                if (event.name == MpvProp.PAUSE) {
                    if (event.value) onPlaybackPaused() else onPlaybackStarted()
                }
            }
            is MpvEvent.PropertyDouble -> {
                when (event.name) {
                    MpvProp.TIME_POS -> onPositionChanged((event.value * 1000).toLong())
                    MpvProp.DURATION -> onDurationChanged((event.value * 1000).toLong())
                    MpvProp.DEMUXER_CACHE_TIME -> onCacheTimeChanged(event.value)
                    MpvProp.DEMUXER_CACHE_DURATION -> onCacheDurationChanged(event.value)
                    MpvProp.SPEED -> onSpeedChanged(event.value)
                    MpvProp.SUB_SCALE -> onSubScaleChanged(event.value)
                }
            }
            is MpvEvent.PropertyLong -> {
                when (event.name) {
                    MpvProp.SUB_POS -> onSubPosChanged(event.value.toInt())
                    MpvProp.VIDEO_PARAMS_W -> onVideoWidthChanged(event.value.toInt())
                    MpvProp.VIDEO_PARAMS_H -> onVideoHeightChanged(event.value.toInt())
                }
            }
            is MpvEvent.PropertyString -> {
                when (event.name) {
                    MpvProp.HWDEC_CURRENT -> onHwdecChanged(event.value)
                    "track-list" -> onTracksChanged(event.value)
                }
            }
        }
    }
}
