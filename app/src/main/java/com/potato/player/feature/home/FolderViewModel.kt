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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@HiltViewModel
class FolderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val bucketId: Long = savedStateHandle.get<Long>("bucketId") ?: -1L

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    val videos: StateFlow<List<VideoItem>> = if (bucketId != -1L) {
        MediaLibraryRepository.getVideosInFolder(context, bucketId)
            .onStart { _isLoading.value = true }
            .onCompletion { _isLoading.value = false }
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    } else {
        flowOf(emptyList<VideoItem>())
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }
}
