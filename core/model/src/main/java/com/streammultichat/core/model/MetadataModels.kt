package com.streammultichat.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class NotificationType {
    FOLLOW,
    SUBSCRIPTION,
    DONATION,
    RAID,
    CHANNEL_POINTS
}

@Serializable
data class UserBadge(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val description: String? = null
)

@Serializable
data class Emote(
    val id: String,
    val code: String,
    val imageUrl: String
)
