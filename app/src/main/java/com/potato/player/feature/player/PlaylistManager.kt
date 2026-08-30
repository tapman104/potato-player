package com.potato.player.feature.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaylistManager {
    private val _playlist = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    private val _currentIndex = MutableStateFlow(-1)

    val playlist: StateFlow<List<Pair<String, String>>> = _playlist.asStateFlow()
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    val currentItem: Pair<String, String>? get() = _playlist.value.getOrNull(_currentIndex.value)
    val hasPrevious: Boolean get() = _currentIndex.value > 0
    val hasNext: Boolean get() = _currentIndex.value >= 0 && _currentIndex.value < _playlist.value.lastIndex

    fun setPlaylist(items: List<Pair<String, String>>, startIndex: Int) {
        _playlist.value = items
        _currentIndex.value = startIndex
    }

    fun moveNext(): Pair<String, String>? {
        if (!hasNext) return null
        _currentIndex.value++
        return currentItem
    }

    fun movePrevious(): Pair<String, String>? {
        if (!hasPrevious) return null
        _currentIndex.value--
        return currentItem
    }
}
