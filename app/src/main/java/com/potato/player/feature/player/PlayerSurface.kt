package com.potato.player.feature.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun PlayerSurface(
    callback: SurfaceHolder.Callback,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).also { sv ->
                sv.keepScreenOn = true
                sv.holder.addCallback(callback)
            }
        },
        modifier = modifier
    )
}
