package com.streammultichat.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val GITHUB_REPO_URL = "https://github.com/LeandroNallibAsis/Stream-Multi-Chat-Android"

@Composable
fun AboutHelpDialog(
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Stream Multi-Chat",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Guía completa, funciones y acerca de",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Cómo Usar", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Funciones", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Acerca de", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedTab) {
                    0 -> HowToUseSection()
                    1 -> FeaturesSection()
                    2 -> AboutProjectSection(
                        onOpenGitHub = { uriHandler.openUri(GITHUB_REPO_URL) }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Entendido")
            }
        },
        dismissButton = {
            if (selectedTab == 2) {
                OutlinedButton(
                    onClick = { uriHandler.openUri(GITHUB_REPO_URL) }
                ) {
                    Text("Ver GitHub")
                }
            }
        }
    )
}

@Composable
private fun HowToUseSection() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HelpCard(
            step = "1",
            title = "Configurar tus Canales",
            description = "Toca los 3 puntitos arriba a la derecha y elige 'Configurar Canales' (o presiona sobre la barra de plataformas):\n" +
                    "• Twitch: Escribe tu usuario (ej: leanx).\n" +
                    "• Kick: Escribe tu usuario o slug de canal.\n" +
                    "• YouTube: Escribe tu handle con arroba (ej: @Leanx-x) o ID de video. Si usas tu @usuario, la app detectará automáticamente tus directos cada vez que prendas stream.\n" +
                    "• Starvios: Escribe tu usuario de canal."
        )

        HelpCard(
            step = "2",
            title = "Lector de Voz (TTS)",
            description = "Toca el icono del altavoz en la barra superior para alternar modos:\n" +
                    "• Desactivado: Voz apagada.\n" +
                    "• Solo Alertas: Solo lee donaciones, bits y suscripciones.\n" +
                    "• Todos los mensajes: Lee cada mensaje del chat en voz alta.\n" +
                    "En Ajustes > Voz TTS puedes regular velocidad, tono y longitud máxima."
        )

        HelpCard(
            step = "3",
            title = "Monitoreo y Vista Previa en Vivo",
            description = "Toca el icono de pantalla en la barra superior para abrir la Vista Previa de Stream. Podrás ver el video en vivo, verificar los espectadores en tiempo real y el tiempo activo de transmisión (uptime)."
        )

        HelpCard(
            step = "4",
            title = "Ventana Flotante / Overlay",
            description = "Toca el icono de capas flotantes para activar la burbuja overlay sobre tus juegos u otras apps. Te permite leer el chat consolidado mientras juegas o transmites desde el mismo teléfono."
        )

        HelpCard(
            step = "5",
            title = "Filtros Rápidos de Plataforma",
            description = "Usa las etiquetas superiores (Todos, Twitch, Kick, YouTube, Starvios) para ver el chat de todas las plataformas juntas o filtrar una sola cuando haya mucho movimiento."
        )

        HelpCard(
            step = "6",
            title = "Modo Simulación",
            description = "Toca el botón 'Play' en la barra superior para iniciar una simulación con chat continuo y alertas ficticias. Ideal para calibrar la voz, probar el diseño y verificar las notificaciones sin estar en vivo."
        )
    }
}

@Composable
private fun FeaturesSection() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FeatureCard(
            title = "Consolidación Multi-Plataforma",
            description = "Conexión simultánea a Twitch, Kick, YouTube y Starvios en una sola lista unificada y ordenada cronológicamente en tiempo real."
        )

        FeatureCard(
            title = "Cero Cuentas ni Contraseñas (Modo Invitado)",
            description = "Conexión anónima y segura. No requiere iniciar sesión con cuentas de desarrollador, ni ingresar claves de transmisión (stream keys) privadas."
        )

        FeatureCard(
            title = "Detección Automática de YouTube",
            description = "Basta con ingresar tu handle (@canal) una sola vez. Cuando inicias directo en OBS o YouTube Studio, la app detecta automáticamente la transmisión activa y conecta el chat solo."
        )

        FeatureCard(
            title = "Conteo de Espectadores en Vivo",
            description = "Sondeo periódico y unificado en las 4 plataformas (Twitch GQL, Kick API v2, YouTube InnerTube y Starvios) para que sepas cuánta gente te está viendo en cada una."
        )

        FeatureCard(
            title = "Motor de Voz TTS Inteligente",
            description = "Síntesis de voz con protección contra saturación, sanitización de emotes, descarte de enlaces molestos y cola fluida sin cortes de audio."
        )

        FeatureCard(
            title = "Alto Rendimiento y Bajo Consumo",
            description = "Desarrollada nativamente en Kotlin con Jetpack Compose. Pesa solo ~2.2 MB, consume menos de 45 MB de RAM y mantiene 60 FPS estables."
        )
    }
}

@Composable
private fun AboutProjectSection(
    onOpenGitHub: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Stream Multi-Chat Android",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Versión 1.0.0 (Release Nativo)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Creado por Leandro Nallib Asis (NallibX) para la comunidad de streamers de habla hispana.",
                    fontSize = 13.sp
                )
            }
        }

        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Código Abierto y Repositorio",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "El proyecto está publicado bajo la licencia abierta MIT en GitHub. Puedes ver el código fuente, colaborar o reportar sugerencias.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onOpenGitHub,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Abrir Repositorio en GitHub", color = Color.White)
                }
            }
        }

        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Privacidad y Seguridad",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• 100% Local: Toda la conexión y procesamiento de chat ocurre únicamente en tu dispositivo.\n" +
                            "• Sin Recopilación: No se envían estadísticas personales ni datos de usuario a servidores externos.\n" +
                            "• Sin Claves Críticas: No almacena contraseñas ni Stream Keys de tus cuentas de streaming.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HelpCard(
    step: String,
    title: String,
    description: String
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = step,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    description: String
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
