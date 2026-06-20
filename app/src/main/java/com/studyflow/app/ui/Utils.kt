package com.studyflow.app.ui

/** MM:SS for timer display */
fun formatTime(millis: Long): String {
    val s = millis / 1_000
    return "%02d:%02d".format(s / 60, s % 60)
}

/**
 * Smart compact duration:
 *   < 60s      → "45s"
 *   < 1h       → "5m 30s"
 *   >= 1h      → "2h 15m 30s"
 */
fun formatDuration(millis: Long): String {
    val totalSec = millis / 1_000
    val hours    = totalSec / 3600
    val minutes  = (totalSec % 3600) / 60
    val seconds  = totalSec % 60
    return when {
        hours > 0   -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else        -> "${seconds}s"
    }
}

/** Short version for charts: "2h 15m" or "45m" — no seconds */
fun formatDurationShort(millis: Long): String {
    val totalSec = millis / 1_000
    val hours    = totalSec / 3600
    val minutes  = (totalSec % 3600) / 60
    return when {
        hours > 0   -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else        -> "${totalSec}s"
    }
}
