package com.potato.player.data

class VideoHistoryRepository(private val dao: VideoHistoryDao) {
    suspend fun getByUri(uri: String): VideoHistory? {
        return dao.getByUri(uri)
    }

    suspend fun upsert(entry: VideoHistory) {
        dao.upsert(entry)
    }
}
