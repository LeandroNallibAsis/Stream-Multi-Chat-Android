package com.streammultichat.core.model

import kotlinx.serialization.Serializable

@Serializable
data class StreamHealth(
    val platform: PlatformType,
    val isLive: Boolean = false,
    val viewerCount: Long = 0,
    val startedAt: Long? = null,
    val title: String? = null,
    val channelName: String? = null,
    val previewUrl: String? = null,
    val targetResolution: String = "144p/360p"
) {
    val uptimeFormatted: String
        get() {
            if (!isLive || startedAt == null || startedAt <= 0L) return "--:--"
            val diffSec = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0)
            val hours = diffSec / 3600
            val minutes = (diffSec % 3600) / 60
            val seconds = diffSec % 60
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    val viewersFormatted: String
        get() {
            return when {
                viewerCount >= 1_000_000 -> String.format("%.1fM", viewerCount / 1_000_000.0)
                viewerCount >= 1_000 -> String.format("%.1fk", viewerCount / 1_000.0)
                viewerCount > 0 -> viewerCount.toString()
                else -> "0"
            }
        }
}
