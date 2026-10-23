package com.streammultichat.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.streammultichat.app.MainActivity
import com.streammultichat.app.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.streammultichat.app.data.ChatSessionManager
import com.streammultichat.app.ui.chat.ChatMessageTextWithEmotes
import com.streammultichat.app.ui.chat.UserBadgeItem
import com.streammultichat.core.model.ChatMessage
import com.streammultichat.core.model.PlatformType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ChatOverlayService"
private const val CHANNEL_ID = "stream_chat_overlay_channel"
private const val NOTIFICATION_ID = 4040
private const val ACTION_STOP = "com.streammultichat.app.overlay.ACTION_STOP"

class ChatOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayLifecycleOwner: OverlayLifecycleOwner
    private var composeView: ComposeView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val isExpandedState = MutableStateFlow(true)
    private var screenWidth = 720
    private var screenHeight = 1600

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            if (!OverlayPermissionHelper.canDrawOverlays(context)) {
                Log.w(TAG, "Cannot start overlay service: permission not granted")
                return
            }
            val intent = Intent(context, ChatOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ChatOverlayService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "ACTION_STOP received, stopping overlay service")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Creating ChatOverlayService")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        updateScreenDimensions()

        overlayLifecycleOwner = OverlayLifecycleOwner().apply {
            onCreate()
            onResume()
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val initialWidth = (screenWidth * 0.88f).toInt().coerceAtMost(dpToPx(360))
        val initialHeight = dpToPx(380)

        layoutParams = WindowManager.LayoutParams(
            initialWidth,
            initialHeight,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - initialWidth) / 2
            y = dpToPx(80)
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)

            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    val isExpanded by isExpandedState.collectAsStateWithLifecycle()
                    val messages by ChatSessionManager.messages.collectAsStateWithLifecycle()
                    val unreadCount by ChatSessionManager.unreadCount.collectAsStateWithLifecycle()

                    ChatOverlayRoot(
                        isExpanded = isExpanded,
                        messages = messages,
                        unreadCount = unreadCount,
                        onToggleExpand = { toggleExpanded() },
                        onClose = { stopSelf() },
                        onDrag = { dx, dy -> moveOverlay(dx, dy) }
                    )
                }
            }
        }

        try {
            windowManager.addView(composeView, layoutParams)
            _isRunning.value = true
            Log.i(TAG, "Overlay view added successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay view to WindowManager", e)
            stopSelf()
        }
    }

    private fun updateScreenDimensions() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun toggleExpanded() {
        val willBeExpanded = !isExpandedState.value
        isExpandedState.value = willBeExpanded

        if (willBeExpanded) {
            ChatSessionManager.resetUnreadCount()
            layoutParams.width = (screenWidth * 0.88f).toInt().coerceAtMost(dpToPx(360))
            layoutParams.height = dpToPx(380)
        } else {
            layoutParams.width = dpToPx(68)
            layoutParams.height = dpToPx(68)
        }

        clampPosition()
        composeView?.let { windowManager.updateViewLayout(it, layoutParams) }
    }

    private fun moveOverlay(dx: Float, dy: Float) {
        layoutParams.x += dx.toInt()
        layoutParams.y += dy.toInt()
        clampPosition()
        composeView?.let {
            try {
                windowManager.updateViewLayout(it, layoutParams)
            } catch (e: Exception) {
                Log.w(TAG, "Error updating overlay view position", e)
            }
        }
    }

    private fun clampPosition() {
        val maxX = (screenWidth - layoutParams.width).coerceAtLeast(0)
        val maxY = (screenHeight - layoutParams.height).coerceAtLeast(0)
        layoutParams.x = layoutParams.x.coerceIn(0, maxX)
        layoutParams.y = layoutParams.y.coerceIn(0, maxY)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chat Flotante Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene activo el chat flotante sobre otras aplicaciones y juegos"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ChatOverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Stream Multi-Chat Flotante")
            .setContentText("Superposición de chat activa sobre la pantalla")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cerrar", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        Log.i(TAG, "Destroying ChatOverlayService")
        _isRunning.value = false
        composeView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing overlay view", e)
            }
        }
        overlayLifecycleOwner.onDestroy()
        composeView = null
        super.onDestroy()
    }
}

@Composable
fun ChatOverlayRoot(
    isExpanded: Boolean,
    messages: List<ChatMessage>,
    unreadCount: Int,
    onToggleExpand: () -> Unit,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    if (isExpanded) {
        OverlayExpandedWindow(
            messages = messages,
            onMinimize = onToggleExpand,
            onClose = onClose,
            onDrag = onDrag
        )
    } else {
        OverlayMinimizedBubble(
            unreadCount = unreadCount,
            onExpand = onToggleExpand,
            onDrag = onDrag
        )
    }
}

@Composable
fun OverlayMinimizedBubble(
    unreadCount: Int,
    onExpand: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var totalDragX = 0f
                    var totalDragY = 0f
                    var isDrag = false

                    drag(down.id) { change ->
                        val dragAmount = change.positionChange()
                        change.consume()
                        totalDragX += kotlin.math.abs(dragAmount.x)
                        totalDragY += kotlin.math.abs(dragAmount.y)
                        if (totalDragX + totalDragY > 12f) {
                            isDrag = true
                        }
                        onDrag(dragAmount.x, dragAmount.y)
                    }

                    if (!isDrag) {
                        onExpand()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge(
                        containerColor = Color(0xFFE91E63),
                        contentColor = Color.White
                    ) {
                        Text(text = if (unreadCount > 99) "99+" else "$unreadCount", fontSize = 10.sp)
                    }
                }
            }
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Forum,
                        contentDescription = "Abrir Chat Flotante",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun OverlayExpandedWindow(
    messages: List<ChatMessage>,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    var opacity by remember { mutableFloatStateOf(0.70f) }
    val listState = rememberLazyListState()

    // Auto-scroll on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF121212).copy(alpha = opacity),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        shadowElevation = 10.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Drag Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = (opacity + 0.2f).coerceAtMost(0.95f)))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Draggable title handle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.x, dragAmount.y)
                            }
                        }
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Stream Overlay",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }

                // Action buttons without drag interference
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Opacity Cycler: 0.35 -> 0.70 -> 0.95
                    IconButton(
                        onClick = {
                            opacity = when (opacity) {
                                0.35f -> 0.70f
                                0.70f -> 0.95f
                                else -> 0.35f
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Opacity,
                            contentDescription = "Cambiar opacidad",
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Minimize to Bubble
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "Minimizar a burbuja",
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Close Overlay
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Chat Messages Feed
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Esperando mensajes en vivo...",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        OverlayChatItem(message = msg)
                    }
                }
            }
        }
    }
}

@Composable
fun OverlayChatItem(message: ChatMessage) {
    val platformColor = when (message.platform) {
        PlatformType.TWITCH -> Color(0xFF9146FF)
        PlatformType.KICK -> Color(0xFF53FC18)
        PlatformType.YOUTUBE -> Color(0xFFFF0000)
        PlatformType.STARVIOS -> Color(0xFFFF69B4)
        PlatformType.TIKTOK -> Color(0xFF00F2FE)
    }

    val timeString = remember(message.timestamp) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E1E1E).copy(alpha = 0.75f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Platform dot
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(platformColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // Badges
                    message.badges.forEach { badge ->
                        UserBadgeItem(
                            badge = badge,
                            sizeDp = 13.dp,
                            fontSizeSp = 8
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                    Text(
                        text = message.authorName,
                        fontWeight = FontWeight.Bold,
                        color = platformColor,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = timeString,
                    color = Color.LightGray.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            ChatMessageTextWithEmotes(
                messageText = message.messageText,
                emotes = message.emotes,
                fontSizeSp = 12,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
