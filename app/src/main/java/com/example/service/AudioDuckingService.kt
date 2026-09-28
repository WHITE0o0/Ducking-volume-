package com.example.service

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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.AudioDuckingEngine
import com.example.model.DuckingPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Foreground Service that runs continuous ambient audio monitoring in the background
 * and displays a persistent, battery-efficient notification with the live dB reading.
 */
class AudioDuckingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var notificationUpdateJob: Job? = null
    private lateinit var engine: AudioDuckingEngine
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        engine = AudioDuckingEngine.getInstance(applicationContext)
        notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_MONITORING -> {
                engine.stopMonitoring()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                if (!engine.hasAudioPermission()) {
                    engine.stopMonitoring()
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundWithMicrophoneType(
                    buildNotification(
                        dbReading = engine.telemetryFlow.value.currentDb,
                        thresholdDb = engine.settingsFlow.value.sensitivityDb,
                        reductionPercent = engine.settingsFlow.value.volumeReductionPercent,
                        phase = DuckingPhase.MONITORING
                    )
                )
                engine.startMonitoring()
                startNotificationTicker()
                return START_STICKY
            }
        }
    }

    private fun startForegroundWithMicrophoneType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startNotificationTicker() {
        if (notificationUpdateJob?.isActive == true) return
        notificationUpdateJob = serviceScope.launch {
            while (isActive) {
                val telemetry = engine.telemetryFlow.value
                val settings = engine.settingsFlow.value
                if (!settings.isMonitoringEnabled && telemetry.phase == DuckingPhase.STANDBY) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
                val notification = buildNotification(
                    dbReading = telemetry.currentDb,
                    thresholdDb = settings.sensitivityDb,
                    reductionPercent = settings.volumeReductionPercent,
                    phase = telemetry.phase
                )
                runCatching {
                    notificationManager.notify(NOTIFICATION_ID, notification)
                }
                // Update notification every 750ms to remain battery-friendly and avoid NotificationManager rate-limiting
                delay(750L)
            }
        }
    }

    private fun buildNotification(
        dbReading: Float,
        thresholdDb: Float,
        reductionPercent: Int,
        phase: DuckingPhase
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AudioDuckingService::class.java).apply {
            action = ACTION_STOP_MONITORING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val roundedDb = dbReading.roundToInt()
        val roundedThreshold = thresholdDb.roundToInt()
        val title = "Live Level: $roundedDb dB • ${phase.displayLabel}"
        val contentText =
            "Threshold: $roundedThreshold dB • Ducking: -$reductionPercent% media volume"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(contentText)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(95, roundedDb.coerceIn(0, 95), false)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.notification_action_stop),
                stopPendingIntent
            )
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        notificationUpdateJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "audio_ducking_monitor_channel"
        const val NOTIFICATION_ID = 4102
        const val ACTION_START_MONITORING = "com.example.action.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.example.action.STOP_MONITORING"

        fun startService(context: Context) {
            val intent = Intent(context, AudioDuckingService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AudioDuckingService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            runCatching {
                context.startService(intent)
            }.onFailure {
                context.stopService(Intent(context, AudioDuckingService::class.java))
            }
        }
    }
}
