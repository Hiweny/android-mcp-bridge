package com.hiweny.mcpbridge.tools

import android.content.Context
import com.hiweny.mcpbridge.mcp.McpTool
import com.hiweny.mcpbridge.mcp.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Tool that returns the current date and time.
 *
 * Parameters:
 *   - format   (optional, default "yyyy-MM-dd HH:mm:ss") a java SimpleDateFormat pattern
 *   - timezone (optional) a TimeZone ID such as "UTC", "Asia/Shanghai", "GMT+8"
 */
class TimeTool(private val context: Context) : McpTool {

    override val name: String = "get_time"

    override val description: String =
        "Returns the current date and time. Supports a custom format pattern and an optional timezone."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("format", JSONObject().apply {
                put("type", "string")
                put("description", "Date/time format pattern (java SimpleDateFormat). Defaults to 'yyyy-MM-dd HH:mm:ss'.")
                put("default", "yyyy-MM-dd HH:mm:ss")
            })
            put("timezone", JSONObject().apply {
                put("type", "string")
                put("description", "Timezone ID (e.g. 'UTC', 'Asia/Shanghai', 'GMT+8'). Defaults to the device timezone.")
            })
        })
        put("required", JSONArray())
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val format = params.optString("format", "yyyy-MM-dd HH:mm:ss")
            val timezone = params.optString("timezone", "")

            val sdf = SimpleDateFormat(format, Locale.getDefault())
            if (timezone.isNotEmpty()) {
                val tz = TimeZone.getTimeZone(timezone)
                sdf.timeZone = tz
            }

            val now = Date()
            val result = JSONObject().apply {
                put("timestamp", now.time)
                put("formatted", sdf.format(now))
                put("timezone", sdf.timeZone.id)
                put("timezoneOffsetMinutes", sdf.timeZone.getOffset(now.time) / 60000)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to get time: ${e.message}")
        }
    }
}
