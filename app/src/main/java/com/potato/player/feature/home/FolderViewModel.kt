package com.potato.player.feature.home

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potato.player.data.library.MediaLibraryRepository
import com.potato.player.data.library.VideoItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FolderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val bucketId: Long = savedStateHandle.get<Long>("bucketId") ?: -1L
    
    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadVideos()
    }

    private fun loadVideos() {
        if (bucketId == -1L) {
            _isLoading.value = false
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _videos.value = MediaLibraryRepository.getVideosInFolder(context, bucketId)
            } catch (e: Exception) {
                // empty state handled by UI
            } finally {
                _isLoading.value = false
            }
        }
    }
}
