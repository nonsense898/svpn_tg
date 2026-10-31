package com.sdocument.sdk.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sdocument.sdk.R
import com.sdocument.sdk.di.ConfigProvider
import com.sdocument.sdk.network.RetrofitClient
import com.sdocument.sdk.utils.NetworkLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URL

class PushService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("scraper_config", Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_banned", false)) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification())
            } else {
                startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification())
            }
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        scope.launch {
            while (isActive) {
                try {
                    pollAndNotify()
                } catch (_: Exception) {}
                delay(POLL_INTERVAL_MS)
            }
        }
        return START_STICKY
    }

    private suspend fun pollAndNotify() {
        val deviceId = NetworkLogger.getDeviceId()
        if (deviceId == "UNKNOWN") return

        val prefs = getSharedPreferences("scraper_config", Context.MODE_PRIVATE)
        val appId = prefs.getInt("app_id", 1)

        val response = RetrofitClient.apiService.pollPush(deviceId, appId)
        if (response.isSuccessful && response.body()?.status == "success") {
            response.body()?.pushes?.forEach { push ->
                showPushNotification(push.title, push.description, push.actionUrl, push.imageUrl)
            }
        }
    }

    private fun showPushNotification(title: String, message: String, actionUrl: String?, imageUrl: String?) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = if (!actionUrl.isNullOrEmpty()) {
            Intent(Intent.ACTION_VIEW, Uri.parse(actionUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            packageManager.getLaunchIntentForPackage(packageName)?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK } ?: Intent()
        }

        val pendingIntent = PendingIntent.getActivity(
            this, title.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, PUSH_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (!imageUrl.isNullOrEmpty()) {
            try {
                val bitmap = BitmapFactory.decodeStream(URL(imageUrl).openConnection().getInputStream())
                builder.setLargeIcon(bitmap)
                builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap))
            } catch (_: Exception) {}
        }

        notificationManager.notify(title.hashCode(), builder.build())
    }

    private fun buildServiceNotification() =
        NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(ConfigProvider.currentConfig.notificationConfig.serviceTitle)
            .setContentText(ConfigProvider.currentConfig.notificationConfig.serviceText)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(SERVICE_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(SERVICE_CHANNEL_ID, "Background Service", NotificationManager.IMPORTANCE_MIN).apply {
                        setShowBadge(false)
                    }
                )
            }
            if (manager.getNotificationChannel(PUSH_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(PUSH_CHANNEL_ID, "Push Notifications", NotificationManager.IMPORTANCE_HIGH)
                )
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
        // Restart self when killed by system (not when force-stopped by user)
        startService(Intent(this, PushService::class.java))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val SERVICE_NOTIFICATION_ID = 9001
        private const val SERVICE_CHANNEL_ID = "push_service_channel"
        const val PUSH_CHANNEL_ID = "push_channel"
        const val POLL_INTERVAL_MS = 60_000L

        fun start(context: Context) {
            val intent = Intent(context, PushService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Ignore background start restrictions
            }
        }
    }
}
