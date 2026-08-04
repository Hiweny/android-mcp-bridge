package com.hiweny.mcpbridge.tools

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.hiweny.mcpbridge.mcp.McpTool
import com.hiweny.mcpbridge.mcp.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Tool that returns hardware / software information about the running device.
 *
 * No required parameters.
 */
class DeviceInfoTool(private val context: Context) : McpTool {

    override val name: String = "get_device_info"

    override val description: String =
        "Returns device model, manufacturer, Android version, SDK level, screen resolution, " +
            "screen density, locale and total RAM."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject())
        put("required", JSONArray())
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            // Screen metrics via WindowManager / DisplayMetrics
            val metrics = DisplayMetrics()
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            // Total RAM via ActivityManager.MemoryInfo
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val totalRam = memoryInfo.totalMem

            val locale = try {
                context.resources.configuration.locales[0].toString()
            } catch (e: Exception) {
                Locale.getDefault().toString()
            }

            val result = JSONObject().apply {
                put("manufacturer", Build.MANUFACTURER ?: "unknown")
                put("brand", Build.BRAND ?: "unknown")
                put("model", Build.MODEL ?: "unknown")
                put("device", Build.DEVICE ?: "unknown")
                put("product", Build.PRODUCT ?: "unknown")
                put("androidVersion", Build.VERSION.RELEASE ?: "unknown")
                put("sdkLevel", Build.VERSION.SDK_INT)
                put("securityPatch", Build.VERSION.SECURITY_PATCH ?: "unknown")
                put("screenWidthPixels", metrics.widthPixels)
                put("screenHeightPixels", metrics.heightPixels)
                put("screenDensityDpi", metrics.densityDpi)
                put("density", metrics.density.toDouble())
                put("xdpi", metrics.xdpi.toDouble())
                put("ydpi", metrics.ydpi.toDouble())
                put("locale", locale)
                put("totalRamBytes", totalRam)
                put("totalRamMb", totalRam / (1024L * 1024L))
                put("availableRamBytes", memoryInfo.availMem)
                put("lowMemory", memoryInfo.lowMemory)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to get device info: ${e.message}")
        }
    }
}
