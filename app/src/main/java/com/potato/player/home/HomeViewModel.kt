package com.potato.player.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.potato.player.data.MediaFile
import com.potato.player.data.MediaFileRepository
import com.potato.player.files.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val applicationContext: Context,
    private val repository: MediaFileRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    fun checkPermissions() {
        val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.READ_MEDIA_VIDEO) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            androidx.core.content.ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.READ_MEDIA_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        
        _permissionGranted.value = hasPermission
        if (hasPermission) {
            loadFiles()
        }
    }

    private val _rawFiles = MutableStateFlow<List<MediaFile>>(emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _expandedFolders = MutableStateFlow<Set<String>>(emptySet())

    private val _isLoading = MutableStateFlow(false)
    private val _permissionGranted = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        _isLoading,
        _permissionGranted,
        _rawFiles,
        _searchQuery,
        _expandedFolders
    ) { isLoading, permissionGranted, files, query, expandedFolders ->
        if (!permissionGranted) return@combine HomeUiState.PermissionRequired
        if (isLoading) return@combine HomeUiState.Loading

        val recentUris = appPreferences.getAllSavedPositionUris()
        val recentFiles = files.filter { it.uri.toString() in recentUris }
            .sortedByDescending { appPreferences.getPlaybackPosition(it.uri.toString()) }

        val filteredFiles = if (query.isBlank()) {
            files
        } else {
            files.filter { it.displayName.contains(query, ignoreCase = true) }
        }

        val grouped = filteredFiles.groupBy { it.folderPath }
        val folderGroups = grouped.map { (path, folderFiles) ->
            FolderGroup(
                folderName = folderFiles.firstOrNull()?.folderName ?: "Unknown",
                folderPath = path,
                files = folderFiles.sortedBy { it.displayName },
                isExpanded = expandedFolders.contains(path)
            )
        }.sortedBy { it.folderName }

        HomeUiState.Ready(
            folders = folderGroups,
            recentFiles = recentFiles
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState.Loading)

    init {
        checkPermissions()
    }

    fun onPermissionResult(granted: Boolean) {
        _permissionGranted.value = granted
        if (granted) {
            loadFiles()
        }
    }

    fun loadFiles() {
        if (!_permissionGranted.value) return
        viewModelScope.launch {
            _isLoading.value = true
            val files = repository.queryMediaFiles()
            _rawFiles.value = files
            _isLoading.value = false
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun refresh() {
        loadFiles()
    }

    fun onToggleFolder(folderPath: String) {
        _expandedFolders.value = _expandedFolders.value.toMutableSet().apply {
            if (contains(folderPath)) remove(folderPath) else add(folderPath)
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    context.applicationContext,
                    MediaFileRepository(context.applicationContext),
                    AppPreferences(context.applicationContext)
                ) as T
            }
        }
    }
}
