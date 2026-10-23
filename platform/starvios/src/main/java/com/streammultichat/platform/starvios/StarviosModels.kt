package com.streammultichat.platform.starvios

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StarviosStream(
    val id: String,
    val status: String? = null,
    val title: String? = null,
    @SerialName("started_at")
    val startedAt: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val profiles: StarviosProfile? = null
)

@Serializable
data class StarviosChatMessage(
    val id: String,
    @SerialName("stream_id")
    val streamId: String,
    val body: String,
    @SerialName("created_at")
    val createdAt: String,
    val profiles: StarviosProfile? = null
)

@Serializable
data class StarviosProfile(
    val username: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("chat_color")
    val chatColor: String? = null
)
