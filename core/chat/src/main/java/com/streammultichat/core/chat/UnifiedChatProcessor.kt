package com.streammultichat.core.chat

import com.streammultichat.core.model.ChatMessage
import com.streammultichat.core.model.ConnectionStatus
import com.streammultichat.core.model.PlatformType
import com.streammultichat.core.model.StreamChannelConfig
import com.streammultichat.core.model.StreamEvent
import com.streammultichat.core.model.StreamHealth
import com.streammultichat.core.model.StreamNotification
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.LinkedHashMap

class UnifiedChatProcessor(
    private val scope: CoroutineScope,
    private val maxBufferCapacity: Int = 250,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val connectors = mutableMapOf<PlatformType, StreamConnector>()
    private val connectorJobs = mutableMapOf<PlatformType, Job>()

    private val messageBuffer = MessageRingBuffer<ChatMessage>(maxBufferCapacity)
    private val notificationBuffer = MessageRingBuffer<StreamNotification>(100)

    // Real-time incoming event stream (ideal for audio/TTS and reactive listeners)
    private val _incomingEvents = MutableSharedFlow<StreamEvent>(extraBufferCapacity = 100)
    val incomingEvents: SharedFlow<StreamEvent> = _incomingEvents.asSharedFlow()

    // Deduplication cache with LRU order (max 500 recent IDs)
    private val lruLock = Any()
    private val recentMessageIds = object : LinkedHashMap<String, Boolean>(500, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
            return size > 500
        }
    }

    private fun isNewMessage(id: String): Boolean {
        synchronized(lruLock) {
            if (recentMessageIds.containsKey(id)) {
                return false
            }
            recentMessageIds[id] = true
            return true
        }
    }

    // Raw messages snapshot
    private val _rawMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val rawMessages: StateFlow<List<ChatMessage>> = _rawMessages.asStateFlow()

    // Notifications
    private val _notifications = MutableStateFlow<List<StreamNotification>>(emptyList())
    val notifications: StateFlow<List<StreamNotification>> = _notifications.asStateFlow()

    // Platform connection states
    private val _connectionStatuses = MutableStateFlow<Map<PlatformType, ConnectionStatus>>(emptyMap())
    val connectionStatuses: StateFlow<Map<PlatformType, ConnectionStatus>> = _connectionStatuses.asStateFlow()

    // Stream health states (Viewers, Uptime, Title)
    private val _streamHealth = MutableStateFlow<Map<PlatformType, StreamHealth>>(emptyMap())
    val streamHealth: StateFlow<Map<PlatformType, StreamHealth>> = _streamHealth.asStateFlow()

    // Active platform filters (empty means all enabled)
    val activeFilters = MutableStateFlow<Set<PlatformType>>(emptySet())

    // Auto-scroll control
    val isAutoScrollPaused = MutableStateFlow(false)

    // High-performance incoming message queue with backpressure handling
    private val incomingQueue = Channel<ChatMessage>(capacity = 1000, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    // Filtered messages exposed to UI
    val filteredMessages: StateFlow<List<ChatMessage>> = combine(rawMessages, activeFilters) { messages, filters ->
        if (filters.isEmpty()) {
            messages
        } else {
            messages.filter { it.platform in filters }
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        // High-frequency batch updater: groups messages arriving within a 40ms window
        // to deliver silky-smooth 60/120 FPS UI updates without hammering the Compose runtime.
        scope.launch(dispatcher) {
            while (isActive) {
                var messageReceived = incomingQueue.tryReceive().getOrNull()
                if (messageReceived == null) {
                    // Wait for the next message
                    messageReceived = incomingQueue.receiveCatching().getOrNull()
                }

                if (messageReceived != null) {
                    var addedCount = 1
                    messageBuffer.push(messageReceived)

                    // Drain all pending messages currently available in the queue (batching)
                    while (true) {
                        val next = incomingQueue.tryReceive().getOrNull() ?: break
                        messageBuffer.push(next)
                        addedCount++
                    }

                    if (addedCount > 0) {
                        _rawMessages.value = messageBuffer.toList()
                    }
                }
                delay(40) // ~25 FPS batching window for UI state publication
            }
        }
    }

    fun registerConnector(connector: StreamConnector) {
        connectors[connector.platform] = connector
        updateStatus(connector.platform, ConnectionStatus.DISCONNECTED)
    }

    fun connectPlatform(config: StreamChannelConfig) {
        val connector = connectors[config.platform] ?: return
        connectorJobs[config.platform]?.cancel()

        val job = scope.launch(dispatcher) {
            launch {
                connector.connectionStatus.collect { status ->
                    updateStatus(config.platform, status)
                }
            }
            launch {
                connector.events.collect { event ->
                    handleEvent(event)
                }
            }
            connector.connect(config)
        }
        connectorJobs[config.platform] = job
    }

    fun disconnectPlatform(platform: PlatformType) {
        val connector = connectors[platform]
        connectorJobs[platform]?.cancel()
        connectorJobs.remove(platform)
        scope.launch(dispatcher) {
            connector?.disconnect()
            updateStatus(platform, ConnectionStatus.DISCONNECTED)
            val updatedHealth = _streamHealth.value.toMutableMap()
            updatedHealth.remove(platform)
            _streamHealth.value = updatedHealth
        }
    }

    fun toggleFilter(platform: PlatformType) {
        val current = activeFilters.value
        activeFilters.value = if (platform in current) {
            current - platform
        } else {
            current + platform
        }
    }

    fun clearChat() {
        messageBuffer.clear()
        _rawMessages.value = emptyList()
    }

    fun handleEvent(event: StreamEvent) {
        when (event) {
            is StreamEvent.Message -> {
                // Deduplicate at the gate before emitting to real-time event bus or batch queue
                if (isNewMessage(event.message.id)) {
                    _incomingEvents.tryEmit(event)
                    incomingQueue.trySend(event.message)
                }
            }
            is StreamEvent.Notification -> {
                _incomingEvents.tryEmit(event)
                scope.launch(Dispatchers.Default) {
                    notificationBuffer.push(event.notification)
                    _notifications.value = notificationBuffer.toList()
                }
            }
            is StreamEvent.StatusChanged -> {
                _incomingEvents.tryEmit(event)
                updateStatus(event.platform, event.status)
            }
            is StreamEvent.HealthUpdate -> {
                _incomingEvents.tryEmit(event)
                updateHealth(event.health)
            }
        }
    }

    private fun updateHealth(health: StreamHealth) {
        val updated = _streamHealth.value.toMutableMap()
        updated[health.platform] = health
        _streamHealth.value = updated
    }

    private fun updateStatus(platform: PlatformType, status: ConnectionStatus) {
        val updated = _connectionStatuses.value.toMutableMap()
        updated[platform] = status
        _connectionStatuses.value = updated
    }
}
