package com.hiweny.mcpbridge.tools

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hiweny.mcpbridge.mcp.McpTool
import com.hiweny.mcpbridge.mcp.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool that shows a system notification.
 *
 * Parameters:
 *   - title   (required) notification title
 *   - message (required) notification body text
 *
 * Uses the "mcp_bridge" notification channel (created for Android O+).
 */
class NotificationTool(private val context: Context) : McpTool {

    override val name: String = "show_notification"

    override val description: String =
        "Shows a system notification with the given title and message."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("title", JSONObject().apply {
                put("type", "string")
                put("description", "The notification title.")
            })
            put("message", JSONObject().apply {
                put("type", "string")
                put("description", "The notification body text.")
            })
        })
        put("required", JSONArray().apply {
            put("title").put("message")
        })
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val title = params.optString("title", "")
            val message = params.optString("message", "")
            if (title.isEmpty()) {
                return@withContext ToolResult.err("Missing required parameter: title")
            }
            if (message.isEmpty()) {
                return@withContext ToolResult.err("Missing required parameter: message")
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Create the channel for Android O+ (API 26+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "MCP Bridge",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications triggered by the MCP Bridge server"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(notificationId, notification)

            val result = JSONObject().apply {
                put("success", true)
                put("notificationId", notificationId)
                put("title", title)
                put("channelId", CHANNEL_ID)
                put("message", "Notification shown successfully")
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to show notification: ${e.message}")
        }
    }

    companion object {
        private const val CHANNEL_ID = "mcp_bridge"
    }
}
