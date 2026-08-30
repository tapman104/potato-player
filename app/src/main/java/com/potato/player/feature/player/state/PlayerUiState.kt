package com.potato.player.feature.player.state



import com.potato.player.feature.player.VideoFitMode

enum class ActiveDialog { None, Audio, Subtitle, Speed, MoreMenu, Decoder }

enum class OrientationMode { AUTO, LOCK_LANDSCAPE, LOCK_PORTRAIT }

data class PlaybackProgressState(
    val positionSec: Double = 0.0,
    val durationSec: Double = 0.0,
    val cachedSec: Double = 0.0,
    val cacheDurationSec: Double = 0.0,
    val dragPositionSec: Double? = null
)

data class PlayerGestureState(
    val swipeSeekTargetSec: Double? = null,
    val isSwipingVolumeOrBrightness: Boolean = false,
    val volume: Int = 100,
    val videoZoom: Float = 1.0f,
    val videoPanX: Float = 0f,
    val videoPanY: Float = 0f
)

data class PlayerUiState(
    val fileName:        String = "",
    val isPlaying:       Boolean = false,
    val playbackSpeed:   Double  = 1.0,
    val isFastForwarding: Boolean = false,
    val fileLoaded:      Boolean = false,
    val isLoading:       Boolean = false,
    val error:           String? = null,
    val hwdecCurrent:    String  = "HW+",
    val subScale: Double = 1.0,
    val subPos: Int = 100,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val orientationMode: OrientationMode = OrientationMode.AUTO,
    val isAutoRotation: Boolean = false,
    val isInPipMode: Boolean = false,
    val isLocked: Boolean = false,
    val fitMode: VideoFitMode = VideoFitMode.FIT,
    val currentPlaylistIndex: Int = -1
)
