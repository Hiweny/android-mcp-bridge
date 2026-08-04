package com.hiweny.mcpbridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hiweny.mcpbridge.MainActivity
import com.hiweny.mcpbridge.R
import com.hiweny.mcpbridge.mcp.ExternalMcpManager
import com.hiweny.mcpbridge.mcp.ToolRegistry
import com.hiweny.mcpbridge.tools.DefaultTools

/**
 * MCP 前台服务：托管 [McpHttpServer]，保持进程存活并展示常驻通知。
 */
class McpForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.hiweny.mcpbridge.action.START"
        const val ACTION_STOP = "com.hiweny.mcpbridge.action.STOP"
        const val EXTRA_PORT = "extra_port"
        private const val CHANNEL_ID = "mcp_service_channel"
        private const val NOTIFICATION_ID = 8024
    }

    private var httpServer: McpHttpServer? = null
    private var externalMcpManager: ExternalMcpManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val port = intent.getIntExtra(EXTRA_PORT, 8024)
                startForegroundCompat(port)
                startServer(port)
            }
            ACTION_STOP -> {
                stopServer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startServer(port: Int) {
        if (httpServer != null) return
        val registry = ToolRegistry().also { DefaultTools.registerAll(it, this) }
        externalMcpManager = ExternalMcpManager()
        httpServer = McpHttpServer(port, registry, externalMcpManager).also { server ->
            server.start()
        }
    }

    private fun stopServer() {
        httpServer?.stop()
        httpServer = null
        externalMcpManager = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
    }

    private fun startForegroundCompat(port: Int) {
        createNotificationChannel()
        startForegroundWithType(buildNotification(port))
    }

    private fun startForegroundWithType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(port: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MCP Bridge 正在运行")
            .setContentText("服务端口：$port")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MCP 服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "MCP Bridge 前台服务运行状态"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
