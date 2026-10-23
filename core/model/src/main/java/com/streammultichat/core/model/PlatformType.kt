package com.streammultichat.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class PlatformType(val displayName: String, val brandColorHex: Long) {
    TWITCH("Twitch", 0xFF9146FF),
    KICK("Kick", 0xFF53FC18),
    YOUTUBE("YouTube", 0xFFFF0000),
    STARVIOS("Starvios", 0xFFFFA500),
    TIKTOK("TikTok", 0xFF00F2FE);

    val identifier: String
        get() = name.lowercase()
}
