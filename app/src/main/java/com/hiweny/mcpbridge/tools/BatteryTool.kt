package com.hiweny.mcpbridge.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.hiweny.mcpbridge.mcp.McpTool
import com.hiweny.mcpbridge.mcp.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool that returns the current battery information.
 *
 * Reads the sticky ACTION_BATTERY_CHANGED broadcast via
 * context.registerReceiver(null, IntentFilter(...)).
 *
 * No required parameters.
 */
class BatteryTool(private val context: Context) : McpTool {

    override val name: String = "get_battery_info"

    override val description: String =
        "Returns battery level, charging status, temperature, technology, voltage and health."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject())
        put("required", JSONArray())
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = context.registerReceiver(null, filter)

            if (intent == null) {
                return@withContext ToolResult.err("Unable to retrieve battery information")
            }

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else -1

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val chargingStatus = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
                BatteryManager.BATTERY_STATUS_UNKNOWN -> "unknown"
                else -> "unknown"
            }

            // Determine power source
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val powerSource = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "ac"
                BatteryManager.BATTERY_PLUGGED_USB -> "usb"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                else -> "unplugged"
            }

            val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val temperatureC = if (temp >= 0) temp / 10.0 else -1.0

            val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "unknown"

            val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            val healthStatus = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "unspecified_failure"
                BatteryManager.BATTERY_HEALTH_COLD -> "cold"
                else -> "unknown"
            }

            val result = JSONObject().apply {
                put("level", batteryPct)
                put("isCharging", isCharging)
                put("chargingStatus", chargingStatus)
                put("powerSource", powerSource)
                put("temperatureCelsius", temperatureC)
                put("voltageMilliVolts", voltageMv)
                put("technology", technology)
                put("health", healthStatus)
                put("rawLevel", level)
                put("scale", scale)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to get battery info: ${e.message}")
        }
    }
}
