package com.potato.player

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.limitedParallelism

class PotatoPlayerApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val imageLoader = ImageLoader.Builder(this)
            // Register VideoFrameDecoder globally — removed from every individual ImageRequest.
            .components {
                add(VideoFrameDecoder.Factory())
            }
            // Disk cache: 150 MB under <cacheDir>/video_thumbs
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("video_thumbs"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            // Memory cache: 20% of available app heap
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            // Cap concurrent video-frame decode jobs at 2 to prevent CPU spikes
            // during fast scrolls. Video frame extraction is CPU-heavy and
            // unlimited parallelism causes jank on large libraries.
            .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(2))
            .crossfade(true)
            .build()

        Coil.setImageLoader(imageLoader)
    }
}
