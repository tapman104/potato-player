package com.potato.player.feature.player.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun PlayerSurface(
    callback: SurfaceHolder.Callback,
    modifier: Modifier = Modifier
) {
    // Track the previously registered callback so we can remove it when it changes.
    val currentCallbackRef = remember { androidx.compose.runtime.mutableStateOf<SurfaceHolder.Callback?>(null) }

    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).also { sv ->
                sv.keepScreenOn = true
                sv.holder.addCallback(callback)
                currentCallbackRef.value = callback
            }
        },
        update = { sv ->
            val prev = currentCallbackRef.value
            if (prev !== callback) {
                prev?.let { sv.holder.removeCallback(it) }
                sv.holder.addCallback(callback)
                currentCallbackRef.value = callback
            }
        },
        modifier = modifier
    )
}
