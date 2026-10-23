package com.streammultichat.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import com.streammultichat.app.data.preferences.TtsMode
import com.streammultichat.app.overlay.ChatOverlayService
import com.streammultichat.app.overlay.OverlayPermissionHelper
import com.streammultichat.core.model.StreamHealth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streammultichat.core.model.ChatMessage
import com.streammultichat.core.model.ConnectionStatus
import com.streammultichat.core.model.PlatformType
import com.streammultichat.core.model.StreamNotification
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val connectionStatuses by viewModel.connectionStatuses.collectAsStateWithLifecycle()
    val activeFilters by viewModel.activeFilters.collectAsStateWithLifecycle()
    val isSimulationActive by viewModel.isSimulationActive.collectAsStateWithLifecycle()
    val twitchChannel by viewModel.twitchChannel.collectAsStateWithLifecycle()
    val kickChannel by viewModel.kickChannel.collectAsStateWithLifecycle()
    val youtubeChannel by viewModel.youtubeChannel.collectAsStateWithLifecycle()
    val starviosChannel by viewModel.starviosChannel.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsStateWithLifecycle()
    val isTtsInitialized by viewModel.isTtsInitialized.collectAsStateWithLifecycle()
    val ttsEngineName by viewModel.ttsEngineName.collectAsStateWithLifecycle()
    val ttsErrorMessage by viewModel.ttsErrorMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val isOverlayRunning by ChatOverlayService.isRunning.collectAsStateWithLifecycle()
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val streamHealth by viewModel.streamHealth.collectAsStateWithLifecycle()

    var showPreviewDialog by remember { mutableStateOf(false) }
    var selectedPreviewPlatform by remember { mutableStateOf(PlatformType.TWITCH) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Explicit auto-scroll state
    var autoScrollEnabled by remember { mutableStateOf(true) }

    // When the user drags the list away from the bottom, pause auto-scroll.
    // If the user scrolls back to the very bottom, re-enable auto-scroll automatically.
    LaunchedEffect(listState.isScrollInProgress) {
        val layoutInfo = listState.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        if (totalItems > 0) {
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (listState.isScrollInProgress) {
                if (lastVisibleItem < totalItems - 1) {
                    autoScrollEnabled = false
                }
            } else {
                if (lastVisibleItem >= totalItems - 1) {
                    autoScrollEnabled = true
                }
            }
        }
    }

    // Auto-scroll on EVERY new message using its unique ID (never freezes when buffer is at capacity)
    val lastMessageId = messages.lastOrNull()?.id
    LaunchedEffect(lastMessageId) {
        if (autoScrollEnabled && messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    // Show floating button when auto-scroll is paused
    val showScrollToBottom by remember {
        derivedStateOf {
            !autoScrollEnabled && messages.isNotEmpty()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Stream Multi-Chat",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        // Simulation toggle button (allows quick test without credentials)
                        IconButton(
                            onClick = { viewModel.toggleSimulation(!isSimulationActive) },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = if (isSimulationActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Modo Simulación",
                                tint = if (isSimulationActive) Color(0xFFE91E63) else MaterialTheme.colorScheme.primary
                            )
                        }

                        // Quick TTS toggle / mute button
                        IconButton(
                            onClick = {
                                if (isTtsSpeaking) {
                                    viewModel.stopTts()
                                } else {
                                    val nextMode = when (userPreferences.ttsMode) {
                                        TtsMode.OFF -> TtsMode.DONATIONS_ONLY
                                        TtsMode.DONATIONS_ONLY -> TtsMode.ALL_MESSAGES
                                        TtsMode.ALL_MESSAGES -> TtsMode.OFF
                                    }
                                    viewModel.updateTtsMode(nextMode)
                                }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            val ttsColor = when {
                                isTtsSpeaking -> Color(0xFF4CAF50)
                                userPreferences.ttsMode != TtsMode.OFF -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }
                            Icon(
                                imageVector = if (userPreferences.ttsMode == TtsMode.OFF) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = "Lector de Voz TTS (${userPreferences.ttsMode.displayName})",
                                tint = ttsColor
                            )
                        }

                        // Stream Health & Preview On-Demand (Fase 10)
                        IconButton(
                            onClick = {
                                selectedPreviewPlatform = PlatformType.TWITCH
                                showPreviewDialog = true
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            val hasLiveStream = isSimulationActive || connectionStatuses.values.any { it == ConnectionStatus.CONNECTED }
                            Icon(
                                imageVector = Icons.Default.LiveTv,
                                contentDescription = "Vista Previa de Stream",
                                tint = if (hasLiveStream) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Floating Chat Overlay (Fase 7)
                        IconButton(
                            onClick = {
                                if (isOverlayRunning) {
                                    ChatOverlayService.stop(context)
                                } else {
                                    if (OverlayPermissionHelper.canDrawOverlays(context)) {
                                        ChatOverlayService.start(context)
                                    } else {
                                        showOverlayPermissionDialog = true
                                    }
                                }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Ventana Flotante / Overlay",
                                tint = if (isOverlayRunning) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Overflow menu (Settings, Clear Chat, Spike Burst)
                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Más opciones"
                                )
                            }

                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                if (isSimulationActive) {
                                    DropdownMenuItem(
                                        text = { Text("Ráfaga de prueba (+100)") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Bolt,
                                                contentDescription = null,
                                                tint = Color(0xFFFFD700)
                                            )
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.triggerBurstSpike()
                                        }
                                    )
                                }

                                DropdownMenuItem(
                                    text = { Text("Probar Alerta / Donación") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Campaign,
                                            contentDescription = null,
                                            tint = Color(0xFFFF9800)
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.simulateTestAlert()
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Configurar Canales") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showSettingsDialog = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Limpiar Chat") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.DeleteSweep,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.clearChat()
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Live status indicators banner (never squished, with live metrics)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlatformStatusDot(
                            platform = PlatformType.TWITCH,
                            status = connectionStatuses[PlatformType.TWITCH] ?: ConnectionStatus.DISCONNECTED,
                            health = streamHealth[PlatformType.TWITCH],
                            onClick = {
                                selectedPreviewPlatform = PlatformType.TWITCH
                                showPreviewDialog = true
                            }
                        )
                        PlatformStatusDot(
                            platform = PlatformType.KICK,
                            status = connectionStatuses[PlatformType.KICK] ?: ConnectionStatus.DISCONNECTED,
                            health = streamHealth[PlatformType.KICK],
                            onClick = {
                                selectedPreviewPlatform = PlatformType.KICK
                                showPreviewDialog = true
                            }
                        )
                        PlatformStatusDot(
                            platform = PlatformType.YOUTUBE,
                            status = connectionStatuses[PlatformType.YOUTUBE] ?: ConnectionStatus.DISCONNECTED,
                            health = streamHealth[PlatformType.YOUTUBE],
                            onClick = {
                                selectedPreviewPlatform = PlatformType.YOUTUBE
                                showPreviewDialog = true
                            }
                        )
                        PlatformStatusDot(
                            platform = PlatformType.STARVIOS,
                            status = connectionStatuses[PlatformType.STARVIOS] ?: ConnectionStatus.DISCONNECTED,
                            health = streamHealth[PlatformType.STARVIOS],
                            onClick = {
                                selectedPreviewPlatform = PlatformType.STARVIOS
                                showPreviewDialog = true
                            }
                        )
                    }
                }

                // Platform filter chips
                FilterChipsBar(
                    activeFilters = activeFilters,
                    onToggleFilter = { viewModel.toggleFilter(it) },
                    onClearFilters = { viewModel.clearFilters() }
                )

                // Event ticker banner (shows latest sub/donation/raid)
                val latestNotif = notifications.lastOrNull()
                if (latestNotif != null) {
                    NotificationBanner(notification = latestNotif)
                }
            }
        },
        floatingActionButton = {
            // Floating pill to return to the bottom when user is reviewing past messages
            AnimatedVisibility(
                visible = showScrollToBottom,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                FloatingActionButton(
                    onClick = {
                        autoScrollEnabled = true
                        coroutineScope.launch {
                            if (messages.isNotEmpty()) {
                                listState.scrollToItem(messages.size - 1)
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Volver al final"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Mensajes recientes",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (messages.isEmpty()) {
                EmptyChatPlaceholder(
                    isSimulationActive = isSimulationActive,
                    onStartSimulation = { viewModel.toggleSimulation(true) },
                    onOpenSettings = { showSettingsDialog = true }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(
                        items = messages,
                        key = { it.id } // Stable key for 60 FPS performance
                    ) { message ->
                        ChatMessageItem(
                            message = message,
                            fontSizeSp = userPreferences.fontSizeSp,
                            showTimestamps = userPreferences.showTimestamps,
                            showPlatformBadges = userPreferences.showPlatformBadges,
                            backgroundOpacity = userPreferences.backgroundOpacity
                        )
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        ConnectionSettingsDialog(
            initialTwitchChannel = twitchChannel,
            initialKickChannel = kickChannel,
            initialYouTubeChannel = youtubeChannel,
            initialStarviosChannel = starviosChannel,
            userPreferences = userPreferences,
            statuses = connectionStatuses,
            isTtsSpeaking = isTtsSpeaking,
            isTtsInitialized = isTtsInitialized,
            ttsEngineName = ttsEngineName,
            ttsErrorMessage = ttsErrorMessage,
            onOpenTtsSettings = { viewModel.openTtsSettings() },
            onConnectTwitch = { viewModel.connectTwitch(it) },
            onDisconnectTwitch = { viewModel.disconnectTwitch() },
            onConnectKick = { viewModel.connectKick(it) },
            onDisconnectKick = { viewModel.disconnectKick() },
            onConnectYouTube = { viewModel.connectYouTube(it) },
            onDisconnectYouTube = { viewModel.disconnectYouTube() },
            onConnectStarvios = { viewModel.connectStarvios(it) },
            onDisconnectStarvios = { viewModel.disconnectStarvios() },
            onUpdateAutoConnect = { viewModel.updateAutoConnect(it) },
            onUpdateFontSize = { viewModel.updateFontSize(it) },
            onUpdateShowTimestamps = { viewModel.updateShowTimestamps(it) },
            onUpdateShowPlatformBadges = { viewModel.updateShowPlatformBadges(it) },
            onUpdateBackgroundOpacity = { viewModel.updateBackgroundOpacity(it) },
            onUpdateTtsMode = { viewModel.updateTtsMode(it) },
            onUpdateTtsSpeechRate = { viewModel.updateTtsSpeechRate(it) },
            onUpdateTtsPitch = { viewModel.updateTtsPitch(it) },
            onUpdateTtsMaxChars = { viewModel.updateTtsMaxChars(it) },
            onUpdateTtsReadAuthorName = { viewModel.updateTtsReadAuthorName(it) },
            onTestTtsVoice = { viewModel.testTtsVoice() },
            onTestTtsAlert = { viewModel.testTtsAlert() },
            onStopTts = { viewModel.stopTts() },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showPreviewDialog) {
        StreamPreviewDialog(
            initialPlatform = selectedPreviewPlatform,
            isSimulationActive = isSimulationActive,
            connectionStatuses = connectionStatuses,
            streamHealthMap = streamHealth,
            channels = mapOf(
                PlatformType.TWITCH to twitchChannel,
                PlatformType.KICK to kickChannel,
                PlatformType.YOUTUBE to youtubeChannel,
                PlatformType.STARVIOS to starviosChannel
            ),
            onDismiss = { showPreviewDialog = false }
        )
    }

    if (showOverlayPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayPermissionDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Permiso de Ventana Flotante",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Para visualizar el chat flotando sobre tus juegos o aplicaciones de transmisión móvil, Android requiere conceder el permiso de superposición ('Aparecer encima de otras apps')."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showOverlayPermissionDialog = false
                    OverlayPermissionHelper.openOverlaySettings(context)
                }) {
                    Text("Abrir Ajustes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayPermissionDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun FilterChipsBar(
    activeFilters: Set<PlatformType>,
    onToggleFilter: (PlatformType) -> Unit,
    onClearFilters: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "Todos" chip
        FilterChip(
            selected = activeFilters.isEmpty(),
            onClick = onClearFilters,
            label = { Text("Todos", fontSize = 12.sp) }
        )

        // Platform specific chips
        PlatformType.entries.forEach { platform ->
            val isSelected = platform in activeFilters
            val platformColor = Color(platform.brandColorHex)

            FilterChip(
                selected = isSelected,
                onClick = { onToggleFilter(platform) },
                label = {
                    Text(
                        text = platform.displayName,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = platformColor.copy(alpha = 0.25f),
                    selectedLabelColor = platformColor
                )
            )
        }
    }
}

@Composable
fun NotificationBanner(notification: StreamNotification) {
    val platformColor = Color(notification.platform.brandColorHex)
    Surface(
        color = platformColor.copy(alpha = 0.15f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(platformColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${notification.platform.displayName}: ${notification.description}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PlatformStatusDot(
    platform: PlatformType,
    status: ConnectionStatus,
    health: StreamHealth? = null,
    onClick: () -> Unit = {}
) {
    val color = when (status) {
        ConnectionStatus.CONNECTED -> Color(0xFF4CAF50)
        ConnectionStatus.CONNECTING, ConnectionStatus.RECONNECTING -> Color(0xFFFF9800)
        ConnectionStatus.ERROR -> Color(0xFFE91E63)
        ConnectionStatus.DISCONNECTED -> Color.Gray
    }

    val isConnected = status == ConnectionStatus.CONNECTED
    val viewerText = if (isConnected && health != null && health.viewerCount > 0) {
        " • 👁 ${health.viewersFormatted}"
    } else ""

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (isConnected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "${platform.displayName}$viewerText",
                fontSize = 10.sp,
                fontWeight = if (isConnected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isConnected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyChatPlaceholder(
    isSimulationActive: Boolean,
    onStartSimulation: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Sin mensajes aún",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Conéctate a Twitch o Kick en vivo desde Configuración, o activa el Modo Simulación para probar la recepción en tiempo real.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = onOpenSettings) {
                Text("Conectar Canales")
            }
            androidx.compose.material3.Button(onClick = onStartSimulation) {
                Text("Activar Simulación")
            }
        }
    }
}
