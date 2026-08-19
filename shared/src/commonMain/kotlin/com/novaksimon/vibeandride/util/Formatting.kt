package com.novaksimon.vibeandride.util

import kotlin.math.round

fun formatSeconds(seconds: Double?): String {
    val totalSeconds = round(seconds?.coerceAtLeast(0.0) ?: 0.0).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%02d:%02d".format(minutes, secs)
    }
}