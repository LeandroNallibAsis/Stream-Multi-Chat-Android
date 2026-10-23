package com.streammultichat.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR;

    val isOnline: Boolean
        get() = this == CONNECTED
}
