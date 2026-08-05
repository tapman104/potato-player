package com.potato.player.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potato.player.data.library.FolderItem
import com.potato.player.data.library.MediaLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _folders = MutableStateFlow<List<FolderItem>>(emptyList())
    val folders: StateFlow<List<FolderItem>> = _folders.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadFolders() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _folders.value = MediaLibraryRepository.getFolders(context)
            } catch (e: Exception) {
                // error handling skipped for brevity, UI will just show empty list
            } finally {
                _isLoading.value = false
            }
        }
    }
}
