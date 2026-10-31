package com.sdocument.sdk.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sdocument.sdk.R
import com.sdocument.sdk.network.RetrofitClient
import com.sdocument.sdk.utils.NetworkLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

class PushWorker(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val deviceId = NetworkLogger.getDeviceId()
            if (deviceId == "UNKNOWN") return@withContext Result.retry()

            val prefs = context.getSharedPreferences("scraper_config", Context.MODE_PRIVATE)
            val appId = prefs.getInt("app_id", 1)

            val response = RetrofitClient.apiService.pollPush(deviceId, appId)

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.status == "banned") {
                    prefs.edit().putBoolean("is_banned", true).apply()
                    // Optionally stop services immediately
                    context.stopService(Intent(context, PushService::class.java))
                    context.stopService(Intent(context, DataService::class.java))
                    return@withContext Result.success()
                }
                
                if (body?.status == "success") {
                    val pushes = body.pushes ?: emptyList()
                    ensureNotificationChannel()
                    pushes.forEach { push ->
                        showNotification(push.title, push.description, push.actionUrl, push.imageUrl)
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Push Notifications", NotificationManager.IMPORTANCE_HIGH)
                )
            }
        }
        // Also restart the persistent service if it somehow stopped
        PushService.start(context)
    }

    private fun showNotification(title: String, message: String, actionUrl: String?, imageUrl: String?) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = if (!actionUrl.isNullOrEmpty()) {
            Intent(Intent.ACTION_VIEW, Uri.parse(actionUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK } ?: Intent()
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            title.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
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

    companion object {
        const val CHANNEL_ID = PushService.PUSH_CHANNEL_ID
    }
}
