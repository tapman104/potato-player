package com.potato.player.feature.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackSessionManager(
    private val historyManager: PlaybackHistoryManager,
    private val trackManager: TrackManager,
    private val scope: CoroutineScope
) {
    var currentUri: String = ""
        private set
    var currentTitle: String = ""
        private set
    var pendingUri: String? = null
    var pendingSeekPosition: Long = 0L
        private set
    var lastLoadedUri: String? = null
        private set

    fun prepareUri(
        uri: String,
        title: String,
        onReady: (uri: String, title: String, resumePosition: Long) -> Unit
    ) {
        if (lastLoadedUri == uri) return
        lastLoadedUri = uri

        scope.launch(Dispatchers.IO) {
            val history = historyManager.getByUri(uri)
            val resumePos = if (history != null && history.lastPlayedPositionSec > 0)
                (history.lastPlayedPositionSec * 1000).toLong() else 0L
            withContext(Dispatchers.Main) {
                onReady(uri, title, resumePos)
            }
        }
    }

    fun executeLoadFile(
        uri: String,
        title: String,
        resumePosition: Long,
        hasSurface: Boolean,
        onStateUpdate: (uri: String, title: String) -> Unit,
        onUiInitial: (initialName: String) -> Unit,
        onResolveFileName: (uri: String) -> Unit,
        onLoadFile: (uri: String) -> Unit
    ) {
        lastLoadedUri = uri
        currentUri = uri
        currentTitle = title
        trackManager.resetAutoSubApplied()
        val initialName = if (title.isNotBlank()) title else "Video"
        onStateUpdate(uri, title)
        onUiInitial(initialName)
        if (title.isBlank()) {
            onResolveFileName(uri)
        }
        pendingSeekPosition = resumePosition
        if (hasSurface) {
            onLoadFile(uri)
        } else {
            pendingUri = uri
        }
    }

    fun handleFileLoaded(
        onTracksLoaded: () -> Unit,
        onSeekIfNeeded: (positionMs: Long) -> Unit,
        onUiUpdate: () -> Unit
    ) {
        onUiUpdate()
        if (pendingSeekPosition > 0L) {
            onSeekIfNeeded(pendingSeekPosition)
            pendingSeekPosition = 0L
        }
        onTracksLoaded()
    }

    fun handleEndFile(
        reason: Int,
        onUiError: () -> Unit,
        onUiNormalEnd: () -> Unit,
        onSaveHistory: () -> Unit,
        onUiOther: () -> Unit,
        onResetFastForward: () -> Unit
    ) {
        if (reason == 3) {
            onUiError()
        } else if (reason == 0) {
            onUiNormalEnd()
            onSaveHistory()
            onResetFastForward()
        } else {
            onUiOther()
        }
    }
}
