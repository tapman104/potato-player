package com.potato.player.feature.player



enum class ActiveDialog { None, Audio, Subtitle, Speed, AspectRatio, MoreMenu, Decoder }

enum class OrientationMode { AUTO, LOCK_LANDSCAPE, LOCK_PORTRAIT }

enum class VideoFitMode { FIT, FILL, STRETCH }

data class PlaybackProgressState(
    val positionSec: Double = 0.0,
    val durationSec: Double = 0.0,
    val cachedSec: Double = 0.0,
    val cacheDurationSec: Double = 0.0,
    val dragPositionSec: Double? = null
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
    val audioTracks:     List<TrackUiModel> = emptyList(),
    val subtitleTracks:  List<TrackUiModel> = emptyList(),
    val currentAudioTrackId: Int = -1,
    val currentSubtitleTrackId: Int = -1,
    val subScale: Double = 1.0,
    val subPos: Int = 100,
    val activeDialog: ActiveDialog = ActiveDialog.None,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val orientationMode: OrientationMode = OrientationMode.AUTO,
    val isAutoRotation: Boolean = false,
    val isInPipMode: Boolean = false,
    val volume: Int = 100,
    val videoZoom: Float = 1.0f,
    val videoPanX: Float = 0f,
    val videoPanY: Float = 0f,
    val isLocked: Boolean = false,
    val progressState: PlaybackProgressState = PlaybackProgressState(),
    val fitMode: VideoFitMode = VideoFitMode.FIT,
    val swipeSeekTargetSec: Double? = null,
    val isSwipingVolumeOrBrightness: Boolean = false,
    val playlist: List<Pair<String, String>> = emptyList(), // (uri, title) pairs
    val currentPlaylistIndex: Int = -1
)
