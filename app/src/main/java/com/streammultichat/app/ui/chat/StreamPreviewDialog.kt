package com.streammultichat.app.ui.chat

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.streammultichat.core.model.ConnectionStatus
import com.streammultichat.core.model.PlatformType
import com.streammultichat.core.model.StreamHealth

@Composable
fun StreamPreviewDialog(
    initialPlatform: PlatformType = PlatformType.TWITCH,
    isSimulationActive: Boolean,
    connectionStatuses: Map<PlatformType, ConnectionStatus>,
    streamHealthMap: Map<PlatformType, StreamHealth>,
    channels: Map<PlatformType, String>,
    onDismiss: () -> Unit
) {
    val platforms = listOf(
        PlatformType.TWITCH,
        PlatformType.KICK,
        PlatformType.YOUTUBE,
        PlatformType.STARVIOS
    )

    var selectedIndex by remember {
        val idx = platforms.indexOf(initialPlatform)
        mutableIntStateOf(if (idx >= 0) idx else 0)
    }

    val currentPlatform = platforms[selectedIndex]
    val currentHealth = streamHealthMap[currentPlatform]
    val currentStatus = connectionStatuses[currentPlatform] ?: ConnectionStatus.DISCONNECTED
    val currentChannel = channels[currentPlatform].orEmpty()
    val context = LocalContext.current

    // Preference between real embedded video stream and simulated canvas feed
    var preferLivePlayer by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                // Top Header with Title and Close Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Monitor & Preview On-Demand",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Monitoreo en tiempo real y vista previa",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                // Platform Selector Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    edgePadding = 12.dp,
                    indicator = { tabPositions ->
                        if (selectedIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                                color = Color(currentPlatform.brandColorHex)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    platforms.forEachIndexed { index, platform ->
                        val isSelected = index == selectedIndex
                        val status = connectionStatuses[platform] ?: ConnectionStatus.DISCONNECTED
                        val isConnected = status == ConnectionStatus.CONNECTED || isSimulationActive

                        Tab(
                            selected = isSelected,
                            onClick = { selectedIndex = index },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isConnected) Color(0xFF4CAF50) else Color.Gray
                                            )
                                    )
                                    Text(
                                        text = platform.displayName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(platform.brandColorHex) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Stream Health & Telemetry Info Card
                StreamHealthHeaderCard(
                    platform = currentPlatform,
                    health = currentHealth,
                    status = currentStatus,
                    channel = currentChannel,
                    isSimulationActive = isSimulationActive
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Toggle between Real Live Video Player and Lightweight Simulation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (preferLivePlayer) "Reproductor en Directo (Web)" else "Modo Simulación (Sin datos)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            onClick = { preferLivePlayer = true },
                            shape = RoundedCornerShape(6.dp),
                            color = if (preferLivePlayer) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "▶ Video Real",
                                fontSize = 10.sp,
                                fontWeight = if (preferLivePlayer) FontWeight.Bold else FontWeight.Normal,
                                color = if (preferLivePlayer) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        Surface(
                            onClick = { preferLivePlayer = false },
                            shape = RoundedCornerShape(6.dp),
                            color = if (!preferLivePlayer) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "📊 Simulado",
                                fontSize = 10.sp,
                                fontWeight = if (!preferLivePlayer) FontWeight.Bold else FontWeight.Normal,
                                color = if (!preferLivePlayer) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Video Player Container (16:9 Aspect Ratio)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (preferLivePlayer && currentChannel.isNotBlank()) {
                        // Real Stream Embed WebView Player with Immediate Resource Disposal on Close
                        RealStreamWebView(
                            platform = currentPlatform,
                            channelOrVideoId = currentChannel,
                            health = currentHealth
                        )
                    } else if (!preferLivePlayer) {
                        // Simulated Low-Power Canvas Player (zero data usage, 60fps retro telemetry feed)
                        SimulatedStreamPreviewView(
                            platform = currentPlatform,
                            channel = currentChannel,
                            health = currentHealth
                        )
                    } else {
                        // Offline or Disconnected Placeholder
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "${currentPlatform.displayName} no tiene canal configurado",
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Configura el canal o activa el Modo Simulación para previsualizar.",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val externalUrl = getExternalPlatformUrl(currentPlatform, currentChannel)
                            if (externalUrl.isNotBlank()) {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(externalUrl))
                                context.startActivity(intent)
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("App Externa", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cerrar Vista Previa", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StreamHealthHeaderCard(
    platform: PlatformType,
    health: StreamHealth?,
    status: ConnectionStatus,
    channel: String,
    isSimulationActive: Boolean
) {
    val isLive = (status == ConnectionStatus.CONNECTED && (health?.isLive == true)) || (isSimulationActive && status == ConnectionStatus.CONNECTED)
    val isConnected = status == ConnectionStatus.CONNECTED

    val streamStartedAt = health?.startedAt

    // Real-time ticking clock for uptime when stream is live
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isLive, streamStartedAt) {
        if (isLive && streamStartedAt != null && streamStartedAt > 0L) {
            while (isActive) {
                currentTimeMs = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    val dynamicUptimeText = if (isLive && streamStartedAt != null && streamStartedAt > 0L) {
        val diffSec = ((currentTimeMs - streamStartedAt) / 1000).coerceAtLeast(0)
        val hours = diffSec / 3600
        val minutes = (diffSec % 3600) / 60
        val seconds = diffSec % 60
        if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    } else {
        "--:--"
    }

    val viewerText = if (isLive) health?.viewersFormatted ?: "En vivo" else if (isConnected) "Idle" else "Offline"
    val streamTitle = health?.title ?: if (isLive) "Transmisión en vivo de $channel" else if (isConnected) "Canal conectado: ${channel.ifBlank { "Sin configurar" }}" else "Canal: ${channel.ifBlank { "Sin configurar" }} (Offline)"

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live status pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isLive) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE91E63))
                        )
                        Text(
                            text = "EN VIVO",
                            color = Color(0xFFE91E63),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    } else if (isConnected) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFA000))
                        )
                        Text(
                            text = "OFFLINE",
                            color = Color(0xFFFFA000),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Gray)
                        )
                        Text(
                            text = "DESCONECTADO",
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                // Viewers & Uptime metrics
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Espectadores",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = viewerText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Uptime",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = dynamicUptimeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Title & Channel info
            Text(
                text = streamTitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * On-Demand WebView video player for real streams.
 * Crucially implements instant deallocation in DisposableEffect when modal is dismissed,
 * preventing any memory/GPU leak on entry-level hardware like the Samsung Galaxy A04.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RealStreamWebView(
    platform: PlatformType,
    channelOrVideoId: String,
    health: StreamHealth? = null
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(platform, channelOrVideoId, health?.channelName, health?.previewUrl) {
        onDispose {
            // Immediate destruction of native WebView and GPU surfaces
            webViewInstance?.let { wv ->
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.clearHistory()
                wv.removeAllViews()
                wv.destroy()
            }
            webViewInstance = null
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(AndroidColor.BLACK)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    userAgentString = "Mozilla/5.0 (Linux; Android 10; SM-A042M) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                }
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                webViewInstance = this
                loadHtmlEmbed(this, platform, channelOrVideoId, health)
            }
        },
        update = { wv ->
            // If already created, update URL if target changed
            loadHtmlEmbed(wv, platform, channelOrVideoId, health)
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun loadHtmlEmbed(webView: WebView, platform: PlatformType, channelOrVideoId: String, health: StreamHealth?) {
    val clean = channelOrVideoId.trim()
    val healthPreview = health?.previewUrl
    val healthChannel = health?.channelName
    when (platform) {
        PlatformType.YOUTUBE -> {
            val videoId = when {
                clean.contains("watch?v=") -> clean.substringAfter("watch?v=").substringBefore("&")
                clean.contains("youtu.be/") -> clean.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                clean.contains("/live/") -> clean.substringAfter("/live/").substringBefore("?").substringBefore("&")
                clean.contains("/embed/") -> clean.substringAfter("/embed/").substringBefore("?").substringBefore("&")
                Regex("^[a-zA-Z0-9_-]{11}$").matches(clean) -> clean
                healthPreview != null && healthPreview.contains("/embed/") ->
                    healthPreview.substringAfter("/embed/").substringBefore("?").substringBefore("&")
                healthChannel != null && Regex("^[a-zA-Z0-9_-]{11}$").matches(healthChannel) ->
                    healthChannel
                else -> null
            }

            if (videoId != null && Regex("^[a-zA-Z0-9_-]{11}$").matches(videoId)) {
                val embedUrl = "https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&controls=1&enablejsapi=1&origin=https://streammultichat.app&widget_referrer=https://streammultichat.app"
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <meta name="referrer" content="strict-origin-when-cross-origin">
                        <style>
                            * { margin: 0; padding: 0; box-sizing: border-box; }
                            body, html { width: 100%; height: 100%; background: #000; overflow: hidden; display: flex; align-items: center; justify-content: center; }
                            iframe { width: 100%; height: 100%; border: none; }
                        </style>
                    </head>
                    <body>
                        <iframe src="$embedUrl" 
                                referrerpolicy="strict-origin-when-cross-origin" 
                                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" 
                                allowfullscreen="true"></iframe>
                    </body>
                    </html>
                """.trimIndent()
                webView.loadDataWithBaseURL("https://streammultichat.app", html, "text/html", "UTF-8", null)
            } else {
                val handle = if (clean.startsWith("@")) clean else "@$clean"
                webView.loadUrl("https://m.youtube.com/$handle/live")
            }
        }
        PlatformType.TWITCH -> {
            val channel = clean.removePrefix("#").lowercase()
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body, html { width: 100%; height: 100%; background: #000; overflow: hidden; display: flex; align-items: center; justify-content: center; }
                        iframe { width: 100%; height: 100%; border: none; }
                    </style>
                </head>
                <body>
                    <iframe src="https://player.twitch.tv/?channel=$channel&parent=streammultichat.app&parent=localhost&muted=false&autoplay=true" 
                            allowfullscreen="true"></iframe>
                </body>
                </html>
            """.trimIndent()
            webView.loadDataWithBaseURL("https://streammultichat.app", html, "text/html", "UTF-8", null)
        }
        PlatformType.KICK -> {
            val channel = clean.lowercase()
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body, html { width: 100%; height: 100%; background: #000; overflow: hidden; display: flex; align-items: center; justify-content: center; }
                        iframe { width: 100%; height: 100%; border: none; }
                    </style>
                </head>
                <body>
                    <iframe src="https://player.kick.com/$channel?autoplay=true&muted=false" 
                            allowfullscreen="true"></iframe>
                </body>
                </html>
            """.trimIndent()
            webView.loadDataWithBaseURL("https://kick.com", html, "text/html", "UTF-8", null)
        }
        PlatformType.STARVIOS -> {
            val cleanChannel = clean
                .removePrefix("https://www.starvios.com/")
                .removePrefix("https://starvios.com/")
                .removePrefix("@")
                .trim()
            webView.loadUrl("https://starvios.com/$cleanChannel")
        }
        PlatformType.TIKTOK -> {
            webView.loadUrl("about:blank")
        }
    }
}

/**
 * Animated low-power simulation player for quick testing.
 * Uses zero internet data, runs at 60 FPS on low GPU memory.
 */
@Composable
fun SimulatedStreamPreviewView(
    platform: PlatformType,
    channel: String,
    health: StreamHealth?
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sim_stream")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )

    val brandColor = Color(platform.brandColorHex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F141C)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerY = height * 0.65f

            // Dynamic audio/video waveform
            val path = Path()
            path.moveTo(0f, height)
            path.lineTo(0f, centerY)

            val step = width / 20f
            for (i in 0..20) {
                val x = i * step
                val rad = Math.toRadians(i * 24.0 + waveOffset).toFloat()
                val y = centerY + kotlin.math.sin(rad) * 20f
                path.lineTo(x, y)
            }
            path.lineTo(width, height)
            path.close()

            drawPath(path, brandColor.copy(alpha = 0.25f))

            // Grid lines to simulate broadcast monitor
            for (line in 1..4) {
                val y = height * (line / 5f)
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
            }
        }

        // Overlay stream metadata badge
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${platform.displayName} Live Stream",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Canal: ${channel.ifBlank { platform.identifier }}",
                color = brandColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }
    }
}

private fun getExternalPlatformUrl(platform: PlatformType, channel: String): String {
    val clean = channel.trim()
    return when (platform) {
        PlatformType.TWITCH -> "https://twitch.tv/${clean.removePrefix("#")}"
        PlatformType.KICK -> "https://kick.com/$clean"
        PlatformType.YOUTUBE -> {
            if (clean.length == 11) "https://youtube.com/watch?v=$clean" else "https://youtube.com/@$clean"
        }
        PlatformType.STARVIOS -> "https://starvios.com/$clean"
        PlatformType.TIKTOK -> "https://tiktok.com/@$clean"
    }
}
