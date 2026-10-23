package com.streammultichat.platform.kick

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class KickChannelInfo(
    val id: Long? = null,
    val chatroom: KickChatroomInfo? = null,
    val livestream: KickLivestreamInfo? = null
)

@Serializable
data class KickLivestreamInfo(
    val id: Long? = null,
    @SerialName("is_live")
    val isLive: Boolean = false,
    @SerialName("session_title")
    val sessionTitle: String? = null,
    @SerialName("viewer_count")
    val viewerCount: Long = 0,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class KickChatroomInfo(
    val id: Long
)

// POST https://web.kick.com/api/v1/realtime/channels/{id}/chat/connection
@Serializable
data class KickRealtimeConnectionRequest(
    val client: KickClientDescriptor,
    val capabilities: KickCapabilities
)

@Serializable
data class KickClientDescriptor(
    val id: String,
    val type: String = "web"
)

@Serializable
data class KickCapabilities(
    @SerialName("accepted_providers")
    val acceptedProviders: List<KickProviderItem>
)

@Serializable
data class KickProviderItem(
    val provider: String
)

@Serializable
data class KickRealtimeConnectionResponse(
    val data: KickRealtimeConnectionData? = null
)

@Serializable
data class KickRealtimeConnectionData(
    val connections: List<KickConnectionItem>? = null,
    val mode: String? = null
)

@Serializable
data class KickConnectionItem(
    val provider: String,
    val credentials: KickCredentials? = null
)

@Serializable
data class KickCredentials(
    val url: String? = null
)

// POST https://web.kick.com/api/v1/realtime/auth/connection
@Serializable
data class KickAuthConnectionRequest(
    @SerialName("client_id")
    val clientId: String
)

@Serializable
data class KickAuthConnectionResponse(
    val data: KickAuthTokenData? = null
)

@Serializable
data class KickAuthTokenData(
    val token: String
)

// Centrifugo Frames (Client -> Server)
@Serializable
data class CentrifugoConnectCommand(
    val id: Int = 1,
    val connect: CentrifugoConnectData
)

@Serializable
data class CentrifugoConnectData(
    val token: String
)

@Serializable
data class CentrifugoSubscribeCommand(
    val id: Int = 2,
    val subscribe: CentrifugoSubscribeData
)

@Serializable
data class CentrifugoSubscribeData(
    val channel: String
)

// Centrifugo Inbound Frame (Flexible)
@Serializable
data class CentrifugoFrame(
    val id: Int? = null,
    val connect: JsonObject? = null,
    val subscribe: JsonObject? = null,
    val push: CentrifugoPush? = null
)

@Serializable
data class CentrifugoPush(
    val channel: String? = null,
    val pub: CentrifugoPub? = null
)

@Serializable
data class CentrifugoPub(
    val data: JsonElement? = null
)

// Chat message payload inside pub.data
@Serializable
data class KickChatMessagePayload(
    val id: String = "",
    @SerialName("chatroom_id")
    val chatroomId: Long? = null,
    val content: String? = null,
    val message: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val sender: KickSender? = null
)

@Serializable
data class KickSender(
    val id: Long? = null,
    val username: String? = null,
    val slug: String? = null,
    val identity: KickIdentity? = null
)

@Serializable
data class KickIdentity(
    val color: String? = null,
    val badges: List<KickBadge>? = null,
    @SerialName("badges_v2")
    val badgesV2: List<KickBadgeV2>? = null
)

@Serializable
data class KickBadge(
    val type: String? = null,
    val text: String? = null,
    val count: Int? = null
)

@Serializable
data class KickBadgeV2(
    val name: String? = null,
    @SerialName("badge_type")
    val badgeType: String? = null,
    @SerialName("image_url")
    val imageUrl: String? = null
)
