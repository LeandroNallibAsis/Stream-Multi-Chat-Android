package com.streammultichat.core.chat

import com.streammultichat.core.model.ConnectionStatus
import com.streammultichat.core.model.PlatformType
import com.streammultichat.core.model.StreamChannelConfig
import com.streammultichat.core.model.StreamEvent
import com.streammultichat.core.model.StreamHealth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface StreamConnector {
    val platform: PlatformType
    val connectionStatus: StateFlow<ConnectionStatus>
    val events: SharedFlow<StreamEvent>
    val health: StateFlow<StreamHealth?> get() = MutableStateFlow(null)

    suspend fun connect(config: StreamChannelConfig)
    suspend fun disconnect()
}
