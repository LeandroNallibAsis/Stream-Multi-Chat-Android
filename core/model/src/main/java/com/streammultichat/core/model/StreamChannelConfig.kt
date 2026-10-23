package com.streammultichat.core.model

import kotlinx.serialization.Serializable

@Serializable
data class StreamChannelConfig(
    val platform: PlatformType,
    val channelName: String,
    val isEnabled: Boolean = true,
    val authToken: String? = null
)
