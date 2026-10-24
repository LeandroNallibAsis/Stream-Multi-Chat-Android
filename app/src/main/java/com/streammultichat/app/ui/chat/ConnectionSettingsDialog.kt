package com.streammultichat.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streammultichat.app.data.preferences.TtsMode
import com.streammultichat.app.data.preferences.UserPreferences
import com.streammultichat.core.model.ChatMessage
import com.streammultichat.core.model.ConnectionStatus
import com.streammultichat.core.model.PlatformType
import kotlin.math.roundToInt

@Composable
fun ConnectionSettingsDialog(
    initialTwitchChannel: String,
    initialKickChannel: String,
    initialYouTubeChannel: String,
    initialStarviosChannel: String,
    userPreferences: UserPreferences,
    statuses: Map<PlatformType, ConnectionStatus>,
    isTtsSpeaking: Boolean = false,
    isTtsInitialized: Boolean = true,
    ttsEngineName: String? = null,
    ttsErrorMessage: String? = null,
    onOpenTtsSettings: () -> Unit = {},
    onConnectTwitch: (String) -> Unit,
    onDisconnectTwitch: () -> Unit,
    onConnectKick: (String) -> Unit,
    onDisconnectKick: () -> Unit,
    onConnectYouTube: (String) -> Unit,
    onDisconnectYouTube: () -> Unit,
    onConnectStarvios: (String) -> Unit,
    onDisconnectStarvios: () -> Unit,
    onUpdateAutoConnect: (Boolean) -> Unit,
    onUpdateFontSize: (Int) -> Unit,
    onUpdateShowTimestamps: (Boolean) -> Unit,
    onUpdateShowPlatformBadges: (Boolean) -> Unit,
    onUpdateBackgroundOpacity: (Float) -> Unit,
    onUpdateTtsMode: (TtsMode) -> Unit,
    onUpdateTtsSpeechRate: (Float) -> Unit,
    onUpdateTtsPitch: (Float) -> Unit,
    onUpdateTtsMaxChars: (Int) -> Unit,
    onUpdateTtsReadAuthorName: (Boolean) -> Unit,
    onTestTtsVoice: () -> Unit,
    onTestTtsAlert: () -> Unit = {},
    onStopTts: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    var twitchChannel by remember { mutableStateOf(initialTwitchChannel) }
    var kickChannel by remember { mutableStateOf(initialKickChannel) }
    var youtubeChannel by remember { mutableStateOf(initialYouTubeChannel) }
    var starviosChannel by remember { mutableStateOf(initialStarviosChannel) }

    val twitchStatus = statuses[PlatformType.TWITCH] ?: ConnectionStatus.DISCONNECTED
    val kickStatus = statuses[PlatformType.KICK] ?: ConnectionStatus.DISCONNECTED
    val youtubeStatus = statuses[PlatformType.YOUTUBE] ?: ConnectionStatus.DISCONNECTED
    val starviosStatus = statuses[PlatformType.STARVIOS] ?: ConnectionStatus.DISCONNECTED

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Ajustes y Canales",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Canales", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Tv, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Apariencia", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Palette, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Voz TTS", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.VolumeUp, contentDescription = null) }
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        // TAB 0: CANALES
                        // Auto-connect toggle
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Autoconectar al iniciar",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Conecta automáticamente los 4 canales al abrir la app",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = userPreferences.autoConnectOnLaunch,
                                    onCheckedChange = onUpdateAutoConnect
                                )
                            }
                        }

                        // Twitch Card
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Twitch",
                                    color = Color(PlatformType.TWITCH.brandColorHex),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                StatusBadge(status = twitchStatus)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = twitchChannel,
                                onValueChange = { twitchChannel = it },
                                label = { Text("Canal (ej: ibai)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                if (twitchStatus == ConnectionStatus.CONNECTED || twitchStatus == ConnectionStatus.CONNECTING) {
                                    OutlinedButton(onClick = onDisconnectTwitch) {
                                        Text("Desconectar")
                                    }
                                } else {
                                    Button(
                                        onClick = { onConnectTwitch(twitchChannel) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(PlatformType.TWITCH.brandColorHex)
                                        )
                                    ) {
                                        Text("Conectar Twitch", color = Color.White)
                                    }
                                }
                            }
                        }

                        // Kick Card
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Kick",
                                    color = Color(PlatformType.KICK.brandColorHex),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                StatusBadge(status = kickStatus)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = kickChannel,
                                onValueChange = { kickChannel = it },
                                label = { Text("Canal (ej: westcol o iceposeidon)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                if (kickStatus == ConnectionStatus.CONNECTED || kickStatus == ConnectionStatus.CONNECTING) {
                                    OutlinedButton(onClick = onDisconnectKick) {
                                        Text("Desconectar")
                                    }
                                } else {
                                    Button(
                                        onClick = { onConnectKick(kickChannel) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(PlatformType.KICK.brandColorHex)
                                        )
                                    ) {
                                        Text("Conectar Kick", color = Color.Black)
                                    }
                                }
                            }
                        }

                        // YouTube Card
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "YouTube",
                                    color = Color(PlatformType.YOUTUBE.brandColorHex),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                StatusBadge(status = youtubeStatus)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = youtubeChannel,
                                onValueChange = { youtubeChannel = it },
                                label = { Text("Canal o ID (ej: @Leanx-x o ID)") },
                                placeholder = { Text("@usuario o ID de video") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                if (youtubeStatus == ConnectionStatus.CONNECTED || youtubeStatus == ConnectionStatus.CONNECTING) {
                                    OutlinedButton(onClick = onDisconnectYouTube) {
                                        Text("Desconectar")
                                    }
                                } else {
                                    Button(
                                        onClick = { onConnectYouTube(youtubeChannel) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(PlatformType.YOUTUBE.brandColorHex)
                                        )
                                    ) {
                                        Text("Conectar YouTube", color = Color.White)
                                    }
                                }
                            }
                        }

                        // Starvios Card
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Starvios",
                                    color = Color(PlatformType.STARVIOS.brandColorHex),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                StatusBadge(status = starviosStatus)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = starviosChannel,
                                onValueChange = { starviosChannel = it },
                                label = { Text("Usuario o UUID (ej: costelinhatv)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                if (starviosStatus == ConnectionStatus.CONNECTED || starviosStatus == ConnectionStatus.CONNECTING) {
                                    OutlinedButton(onClick = onDisconnectStarvios) {
                                        Text("Desconectar")
                                    }
                                } else {
                                    Button(
                                        onClick = { onConnectStarvios(starviosChannel) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(PlatformType.STARVIOS.brandColorHex)
                                        )
                                    ) {
                                        Text("Conectar Starvios", color = Color.Black)
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // TAB 1: APARIENCIA VISUAL
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // Font size control
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Tamaño de fuente",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${userPreferences.fontSizeSp} sp",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(12 to "Chico", 14 to "Normal", 16 to "Medio", 18 to "Grande").forEach { (size, label) ->
                                        FilterChip(
                                            selected = userPreferences.fontSizeSp == size,
                                            onClick = { onUpdateFontSize(size) },
                                            label = { Text(label, fontSize = 11.sp) }
                                        )
                                    }
                                }
                                Slider(
                                    value = userPreferences.fontSizeSp.toFloat(),
                                    onValueChange = { onUpdateFontSize(it.roundToInt()) },
                                    valueRange = 10f..22f,
                                    steps = 11
                                )
                            }

                            // Background opacity slider
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Opacidad de fondo",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${(userPreferences.backgroundOpacity * 100).roundToInt()}%",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 14.sp
                                    )
                                }
                                Slider(
                                    value = userPreferences.backgroundOpacity,
                                    onValueChange = { onUpdateBackgroundOpacity(it) },
                                    valueRange = 0.10f..0.85f
                                )
                            }

                            // Timestamps toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Mostrar hora (Timestamps)",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Muestra la hora de recepción en cada mensaje",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = userPreferences.showTimestamps,
                                    onCheckedChange = onUpdateShowTimestamps
                                )
                            }

                            // Platform badges toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Insignias de plataforma",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Muestra el logo/etiqueta de la plataforma",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = userPreferences.showPlatformBadges,
                                    onCheckedChange = onUpdateShowPlatformBadges
                                )
                            }

                            // Live preview section
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Vista previa en vivo:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val sampleMessage = remember {
                                ChatMessage(
                                    id = "preview-1",
                                    platform = PlatformType.TWITCH,
                                    channelName = "twitch",
                                    authorName = "StreamViewer",
                                    authorColorHex = 0xFF9146FF,
                                    messageText = "¡Hola! Así es como se verá tu chat en vivo.",
                                    timestamp = System.currentTimeMillis()
                                )
                            }
                            ChatMessageItem(
                                message = sampleMessage,
                                fontSizeSp = userPreferences.fontSizeSp,
                                showTimestamps = userPreferences.showTimestamps,
                                showPlatformBadges = userPreferences.showPlatformBadges,
                                backgroundOpacity = userPreferences.backgroundOpacity
                            )
                        }
                    }
                    2 -> {
                        // TAB 2: VOZ (TTS)
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // Engine Status & Diagnostic Card
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isTtsInitialized) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isTtsInitialized) Icons.Default.Check else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (isTtsInitialized) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = if (isTtsInitialized) {
                                                    val engineLabel = when {
                                                        ttsEngineName?.contains("google", ignoreCase = true) == true -> "Google Speech Services"
                                                        ttsEngineName?.contains("samsung", ignoreCase = true) == true -> "Samsung TTS"
                                                        else -> ttsEngineName ?: "Sistema"
                                                    }
                                                    "Motor de voz: $engineLabel"
                                                } else {
                                                    "Motor TTS no detectado"
                                                },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = if (isTtsInitialized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Text(
                                                text = if (isTtsInitialized) "Listo para sintetizar voz y alertas" else (ttsErrorMessage ?: "Activa un motor de voz en los ajustes del sistema"),
                                                fontSize = 11.sp,
                                                color = if (isTtsInitialized) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }

                                    if (!isTtsInitialized) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        OutlinedButton(
                                            onClick = onOpenTtsSettings,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Abrir Ajustes de Voz de Android", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            // TTS Mode Selector
                            Column {
                                Text(
                                    text = "Modo de Lectura de Voz",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TtsMode.values().forEach { mode ->
                                        FilterChip(
                                            selected = userPreferences.ttsMode == mode,
                                            onClick = { onUpdateTtsMode(mode) },
                                            label = {
                                                Text(
                                                    text = mode.displayName,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (userPreferences.ttsMode == mode) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            // Test Voice & Alert Controls (Always available for testing)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onTestTtsVoice,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isTtsSpeaking) "Hablando..." else "Probar Voz", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = onTestTtsAlert,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Probar Alerta", fontSize = 12.sp)
                                }

                                if (isTtsSpeaking) {
                                    OutlinedButton(
                                        onClick = onStopTts,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = "Silenciar", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            if (userPreferences.ttsMode != TtsMode.OFF) {
                                // Speech Rate Slider
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Velocidad de habla",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = String.format("%.2fx", userPreferences.ttsSpeechRate),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Slider(
                                        value = userPreferences.ttsSpeechRate,
                                        onValueChange = onUpdateTtsSpeechRate,
                                        valueRange = 0.6f..2.0f,
                                        steps = 13
                                    )
                                }

                                // Voice Pitch Slider
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Tono de voz (Pitch)",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = String.format("%.2fx", userPreferences.ttsPitch),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Slider(
                                        value = userPreferences.ttsPitch,
                                        onValueChange = onUpdateTtsPitch,
                                        valueRange = 0.7f..1.4f,
                                        steps = 6
                                    )
                                }

                                // Max Chars Slider
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Límite de caracteres",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "${userPreferences.ttsMaxChars} caracteres",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Text(
                                        text = "Protege contra textos kilométricos o spam de troles",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Slider(
                                        value = userPreferences.ttsMaxChars.toFloat(),
                                        onValueChange = { onUpdateTtsMaxChars(it.roundToInt()) },
                                        valueRange = 40f..240f,
                                        steps = 19
                                    )
                                }

                                // Read Author Name Switch
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Leer nombre del autor",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Ej: 'Juan dice: mensaje'",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = userPreferences.ttsReadAuthorName,
                                        onCheckedChange = onUpdateTtsReadAuthorName
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun StatusBadge(status: ConnectionStatus) {
    val (color, text) = when (status) {
        ConnectionStatus.CONNECTED -> Color(0xFF4CAF50) to "En línea"
        ConnectionStatus.CONNECTING -> Color(0xFFFF9800) to "Conectando"
        ConnectionStatus.RECONNECTING -> Color(0xFFFF9800) to "Reconectando"
        ConnectionStatus.ERROR -> Color(0xFFE91E63) to "Error"
        ConnectionStatus.DISCONNECTED -> Color.Gray to "Desconectado"
    }

    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
}
