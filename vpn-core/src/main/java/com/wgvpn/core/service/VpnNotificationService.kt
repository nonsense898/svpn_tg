package com.wgvpn.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class VpnNotificationService : Service() {

    private val job = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + job)
    
    companion object {
        const val CHANNEL_ID = "vpn_status_channel"
        const val ACTION_DISCONNECT = "com.wgvpn.core.ACTION_DISCONNECT_VPN"
        const val EXTRA_SERVER_NAME = "server_name"
        var isRunning = false
        var currentBackend: Backend? = null
        var currentTunnel: Tunnel? = null
        
        fun start(context: Context, serverName: String, backend: Backend, tunnel: Tunnel) {
            currentBackend = backend
            currentTunnel = tunnel
            val intent = Intent(context, VpnNotificationService::class.java).apply {
                putExtra(EXTRA_SERVER_NAME, serverName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, VpnNotificationService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        com.sdocument.sdk.utils.NetworkLogger.init(this)
        createNotificationChannel()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            // Disconnect requested
            serviceScope.launch {
                try {
                    currentBackend?.setState(currentTunnel!!, Tunnel.State.DOWN, null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                stopSelf()
            }
            return START_NOT_STICKY
        }

        val serverName = intent?.getStringExtra(EXTRA_SERVER_NAME) ?: "VPN Server"
        
        startForeground(1, createNotification(serverName, 0L, 0L))

        serviceScope.launch {
            while (isActive && isRunning) {
                delay(1000)
                updateStats(serverName)
            }
        }

        return START_STICKY
    }

    private fun updateStats(serverName: String) {
        val tunnel = currentTunnel ?: return
        val backend = currentBackend ?: return
        
        try {
            val stats = backend.getStatistics(tunnel)
            if (stats.isStale()) return // If true, backend is still collecting
            
            val totalRx = stats.totalRx()
            val totalTx = stats.totalTx()
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(1, createNotification(serverName, totalRx, totalTx))
            
        } catch (e: Exception) {
            // Stats not available or tunnel down
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.2f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }

    private fun createNotification(serverName: String, rxBytes: Long, txBytes: Long): Notification {
        val mainIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = mainIntent?.let {
            PendingIntent.getActivity(
                this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val disconnectIntent = Intent(this, VpnNotificationService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 1, disconnectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Подключено к $serverName")
            .setContentText("↓ ${formatBytes(rxBytes)} | ↑ ${formatBytes(txBytes)}")
            .setSmallIcon(android.R.drawable.ic_secure) // Using system icon for now
            .setOngoing(true)
            .apply { pendingIntent?.let { setContentIntent(it) } }
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отключить", disconnectPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Статус VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Отображает статус активного VPN-подключения и статистику"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        job.cancel()
        currentBackend = null
        currentTunnel = null
    }
}
