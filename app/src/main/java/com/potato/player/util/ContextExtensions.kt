package com.potato.player.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun lockOrientation(activity: Activity?, orientation: Int) {
    if (activity != null && activity.requestedOrientation != orientation) {
        activity.requestedOrientation = orientation
    }
}
