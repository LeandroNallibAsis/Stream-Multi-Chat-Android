package com.streammultichat.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String,
    val platform: PlatformType,
    val channelName: String,
    val authorName: String,
    val authorColorHex: Long? = null,
    val messageText: String,
    val badges: List<UserBadge> = emptyList(),
    val emotes: List<Emote> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val isHighlight: Boolean = false,
    val rawPayload: String? = null
)

@Serializable
data class StreamNotification(
    val id: String,
    val platform: PlatformType,
    val type: NotificationType,
    val user: String,
    val title: String,
    val description: String,
    val amount: Double? = null,
    val currency: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface StreamEvent {
    data class Message(val message: ChatMessage) : StreamEvent
    data class Notification(val notification: StreamNotification) : StreamEvent
    data class StatusChanged(
        val platform: PlatformType,
        val status: ConnectionStatus,
        val message: String? = null
    ) : StreamEvent
    data class HealthUpdate(val health: StreamHealth) : StreamEvent
}
