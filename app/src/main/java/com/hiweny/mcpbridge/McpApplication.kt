package com.hiweny.mcpbridge

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/**
 * 应用入口。提前创建通知渠道，确保前台服务通知可用。
 */
class McpApplication : Application() {

    companion object {
        const val CHANNEL_ID = "mcp_service_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
