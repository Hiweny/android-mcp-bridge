package com.hiweny.mcpbridge.tools

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.hiweny.mcpbridge.mcp.McpTool
import com.hiweny.mcpbridge.mcp.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool that vibrates the device.
 *
 * Parameters:
 *   - duration (optional, default 500ms, max 5000ms) vibration duration in milliseconds
 *   - pattern  (optional) JSON array of longs describing a waveform pattern
 *               (off, on, off, on, ...). When provided, this takes precedence over duration.
 *
 * Uses Vibrator on API < 31 and VibratorManager on API 31+.
 */
class VibrationTool(private val context: Context) : McpTool {

    override val name: String = "vibrate"

    override val description: String =
        "Vibrates the device for a given duration or according to a waveform pattern."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("duration", JSONObject().apply {
                put("type", "integer")
                put("description", "Vibration duration in milliseconds (max 5000). Used when no pattern is given.")
                put("default", 500)
                put("minimum", 0)
                put("maximum", MAX_DURATION_MS)
            })
            put("pattern", JSONObject().apply {
                put("type", "array")
                put("description", "Vibration waveform pattern as an array of millisecond durations (off, on, off, on, ...).")
                put("items", JSONObject().apply {
                    put("type", "integer")
                    put("minimum", 0)
                })
            })
        })
        put("required", JSONArray())
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (!vibrator.hasVibrator()) {
                return@withContext ToolResult.err("Device does not have a vibrator")
            }

            val pattern = params.optJSONArray("pattern")

            if (pattern != null && pattern.length() > 0) {
                // Validate and build the waveform pattern
                val patternArray = LongArray(pattern.length())
                for (i in 0 until pattern.length()) {
                    val value = pattern.optLong(i, 0L)
                    if (value < 0) {
                        return@withContext ToolResult.err(
                            "Invalid pattern value at index $i: durations must be non-negative"
                        )
                    }
                    patternArray[i] = value
                }
                vibrator.vibrate(VibrationEffect.createWaveform(patternArray, -1))
                val result = JSONObject().apply {
                    put("success", true)
                    put("mode", "pattern")
                    put("patternLength", patternArray.size)
                    put("message", "Vibration pattern triggered")
                }
                ToolResult.ok(result.toString())
            } else {
                var duration = params.optLong("duration", DEFAULT_DURATION_MS)
                if (duration < 0) duration = DEFAULT_DURATION_MS
                if (duration > MAX_DURATION_MS) duration = MAX_DURATION_MS
                vibrator.vibrate(
                    VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                )
                val result = JSONObject().apply {
                    put("success", true)
                    put("mode", "oneshot")
                    put("durationMs", duration)
                    put("message", "Vibration triggered")
                }
                ToolResult.ok(result.toString())
            }
        } catch (e: Exception) {
            ToolResult.err("Failed to vibrate: ${e.message}")
        }
    }

    companion object {
        private const val DEFAULT_DURATION_MS = 500L
        private const val MAX_DURATION_MS = 5000L
    }
}
