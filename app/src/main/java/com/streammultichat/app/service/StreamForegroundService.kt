package com.streammultichat.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.streammultichat.app.MainActivity
import com.streammultichat.app.R
import com.streammultichat.app.data.ChatSessionManager
import com.streammultichat.core.model.ConnectionStatus
import com.streammultichat.core.model.PlatformType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "StreamForegroundService"

/**
 * Android Foreground Service to guarantee uninterrupted network streaming
 * and voice reading (TTS) when the screen is locked or the app is minimized.
 */
class StreamForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var notificationUpdateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        createNotificationChannel()
        // Must call startForeground immediately during onCreate() to guarantee compliance with Android foreground start timeout
        try {
            startForegroundWithNotification(buildNotification("Conectando a los streams...", 0))
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate startForegroundWithNotification", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "Received ACTION_STOP, terminating service.")
            stopForegroundService()
            return START_NOT_STICKY
        }

        startMonitoringAndUpdatingNotification()
        return START_STICKY
    }

    private fun startForegroundWithNotification(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
        }
    }

    private fun startMonitoringAndUpdatingNotification() {
        notificationUpdateJob?.cancel()
        notificationUpdateJob = serviceScope.launch {
            // Combine statuses and message count, throttling updates to at most once per 2 seconds
            combine(
                ChatSessionManager.connectionStatuses,
                ChatSessionManager.messages
            ) { statuses, messages ->
                Pair(statuses, messages.size)
            }.collect { (statuses, messageCount) ->
                val statusText = formatStatusText(statuses)
                val notification = buildNotification(statusText, messageCount)
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.notify(NOTIFICATION_ID, notification)
                delay(2000)
            }
        }
    }

    private fun formatStatusText(statuses: Map<PlatformType, ConnectionStatus>): String {
        val connectedList = statuses.filterValues { it == ConnectionStatus.CONNECTED }.keys
        return if (connectedList.isNotEmpty()) {
            "Conectado: " + connectedList.joinToString(", ") { it.displayName }
        } else {
            val connectingList = statuses.filterValues { it == ConnectionStatus.CONNECTING }.keys
            if (connectingList.isNotEmpty()) {
                "Conectando: " + connectingList.joinToString(", ") { it.displayName }
            } else {
                "Monitoreando canales"
            }
        }
    }

    private fun buildNotification(statusText: String, messageCount: Int): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, StreamForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val countText = if (messageCount > 0) " • $messageCount msgs" else ""

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Stream Multi-Chat")
            .setContentText("$statusText$countText")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_view, "Abrir Chat", openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Stream Multi-Chat Segundo Plano",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene las conexiones de chat y lectura de voz activas en segundo plano"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundService() {
        notificationUpdateJob?.cancel()
        _isRunning.value = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground", e)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        _isRunning.value = false
        Log.d(TAG, "StreamForegroundService destroyed cleanly.")
    }

    companion object {
        const val CHANNEL_ID = "stream_chat_foreground_channel"
        const val NOTIFICATION_ID = 5050
        const val ACTION_STOP = "com.streammultichat.app.service.ACTION_STOP"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            try {
                val intent = Intent(context, StreamForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting StreamForegroundService", e)
            }
        }

        fun stop(context: Context) {
            if (!_isRunning.value) return
            try {
                val intent = Intent(context, StreamForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping StreamForegroundService", e)
            }
        }
    }
}
