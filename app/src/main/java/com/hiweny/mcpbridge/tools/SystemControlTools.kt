package com.hiweny.mcpbridge.tools

import android.app.ActivityManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import com.hiweny.mcpbridge.mcp.McpTool
import com.hiweny.mcpbridge.mcp.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool that returns the current volume levels for the main audio streams.
 *
 * Returns volume, maximum and minimum for the music, ring, alarm, notification and
 * system streams. Uses AudioManager.
 *
 * No required parameters.
 */
class VolumeTool(private val context: Context) : McpTool {

    override val name: String = "get_volume"

    override val description: String =
        "Returns the current volume levels for the music, ring, alarm, notification and " +
            "system audio streams."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject())
        put("required", JSONArray())
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val result = JSONObject().apply {
                put("music", volumeJson(am, AudioManager.STREAM_MUSIC, "music"))
                put("ring", volumeJson(am, AudioManager.STREAM_RING, "ring"))
                put("alarm", volumeJson(am, AudioManager.STREAM_ALARM, "alarm"))
                put("notification", volumeJson(am, AudioManager.STREAM_NOTIFICATION, "notification"))
                put("system", volumeJson(am, AudioManager.STREAM_SYSTEM, "system"))
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to get volume: ${e.message}")
        }
    }

    private fun volumeJson(am: AudioManager, streamType: Int, streamName: String): JSONObject {
        return JSONObject().apply {
            put("stream", streamName)
            put("volume", am.getStreamVolume(streamType))
            put("maxVolume", am.getStreamMaxVolume(streamType))
            put(
                "minVolume",
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    am.getStreamMinVolume(streamType)
                } else {
                    0
                }
            )
        }
    }
}

/**
 * Tool that sets the volume for a given audio stream.
 *
 * Parameters:
 *   - stream (required) one of music, ring, alarm, notification, system
 *   - volume (required) target volume level (0 to the stream maximum)
 *
 * Uses AudioManager.setStreamVolume. Only values within the valid range are accepted.
 */
class SetVolumeTool(private val context: Context) : McpTool {

    override val name: String = "set_volume"

    override val description: String =
        "Sets the volume for a given audio stream. Valid streams: music, ring, alarm, " +
            "notification, system."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("stream", JSONObject().apply {
                put("type", "string")
                put("description", "Audio stream to adjust.")
                put("enum", JSONArray().apply {
                    put("music")
                    put("ring")
                    put("alarm")
                    put("notification")
                    put("system")
                })
            })
            put("volume", JSONObject().apply {
                put("type", "integer")
                put("description", "Target volume level (0 to max).")
                put("minimum", 0)
            })
        })
        put("required", JSONArray().apply {
            put("stream")
            put("volume")
        })
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val streamName = params.optString("stream", "").lowercase()
            val streamType = when (streamName) {
                "music" -> AudioManager.STREAM_MUSIC
                "ring" -> AudioManager.STREAM_RING
                "alarm" -> AudioManager.STREAM_ALARM
                "notification" -> AudioManager.STREAM_NOTIFICATION
                "system" -> AudioManager.STREAM_SYSTEM
                else -> return@withContext ToolResult.err(
                    "Invalid stream '$streamName'. Valid values: music, ring, alarm, notification, system"
                )
            }

            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = am.getStreamMaxVolume(streamType)

            if (!params.has("volume")) {
                return@withContext ToolResult.err("Missing required parameter: volume")
            }
            val volume = params.optInt("volume", -1)
            if (volume < 0) {
                return@withContext ToolResult.err("Parameter 'volume' must be a non-negative integer")
            }
            if (volume > maxVolume) {
                return@withContext ToolResult.err(
                    "Volume $volume exceeds maximum $maxVolume for stream '$streamName'"
                )
            }

            am.setStreamVolume(streamType, volume, 0)

            val result = JSONObject().apply {
                put("stream", streamName)
                put("volume", am.getStreamVolume(streamType))
                put("maxVolume", maxVolume)
                put("set", true)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to set volume: ${e.message}")
        }
    }
}

/**
 * Tool that returns the current screen brightness and auto-brightness mode.
 *
 * Returns the brightness value (0-255) and whether auto-brightness (adaptive brightness)
 * is enabled. Uses Settings.System.
 *
 * No required parameters.
 */
class BrightnessTool(private val context: Context) : McpTool {

    override val name: String = "get_brightness"

    override val description: String =
        "Returns the current screen brightness (0-255) and whether auto-brightness is enabled."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject())
        put("required", JSONArray())
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val brightness = try {
                Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
            } catch (e: Exception) {
                -1
            }
            val autoBrightness = try {
                Settings.System.getInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE
                ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            } catch (e: Exception) {
                false
            }

            val result = JSONObject().apply {
                put("brightness", brightness)
                put("autoBrightness", autoBrightness)
                put("brightnessMode", if (autoBrightness) "automatic" else "manual")
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to get brightness: ${e.message}")
        }
    }
}

/**
 * Tool that turns the device flashlight (torch) on or off.
 *
 * Parameters:
 *   - on (required) true to turn the flashlight on, false to turn it off
 *
 * Uses CameraManager. The device is first checked for an available flash unit via
 * CameraCharacteristics.FLASH_INFO_AVAILABLE. setTorchMode is invoked on the main
 * thread as required by the camera framework.
 */
class FlashlightTool(private val context: Context) : McpTool {

    override val name: String = "toggle_flashlight"

    override val description: String =
        "Turns the device flashlight (torch) on or off. Checks for flash availability first."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("on", JSONObject().apply {
                put("type", "boolean")
                put("description", "true to turn the flashlight on, false to turn it off.")
            })
        })
        put("required", JSONArray().put("on"))
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            if (!params.has("on")) {
                return@withContext ToolResult.err("Missing required parameter: on")
            }
            val on = try {
                params.getBoolean("on")
            } catch (e: Exception) {
                return@withContext ToolResult.err("Parameter 'on' must be a boolean")
            }

            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            // Find the first camera that exposes a flash unit.
            val flashCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                (characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) as? Boolean) == true
            } ?: return@withContext ToolResult.err(
                "No camera with a flash unit is available on this device"
            )

            // setTorchMode must be called on the main thread.
            val torchError: String? = withContext(Dispatchers.Main) {
                try {
                    cameraManager.setTorchMode(flashCameraId, on)
                    null
                } catch (e: Exception) {
                    e.message ?: "Failed to toggle flashlight"
                }
            }

            if (torchError != null) {
                return@withContext ToolResult.err(torchError)
            }

            val result = JSONObject().apply {
                put("on", on)
                put("cameraId", flashCameraId)
                put("toggled", true)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to toggle flashlight: ${e.message}")
        }
    }
}

/**
 * Tool that returns the list of running app processes.
 *
 * Returns package name, PID, UID, importance (label and value) and memory usage
 * (total PSS / private dirty / shared dirty) for each running process.
 * Uses ActivityManager.getRunningAppProcesses and getProcessMemoryInfo.
 *
 * No required parameters.
 *
 * Note: On Android 5.0+ (API 21+) this API only returns the caller's own processes for
 * privacy reasons, unless the app holds the PACKAGE_USAGE_STATS permission.
 */
class RunningProcessesTool(private val context: Context) : McpTool {

    override val name: String = "get_running_processes"

    override val description: String =
        "Returns the list of running app processes (package name, PID, UID, importance and " +
            "memory usage). On Android 5.0+ this typically only returns the app's own processes."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject())
        put("required", JSONArray())
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            val processes = am.runningAppProcesses ?: emptyList()

            // Fetch memory info in one batch call; the returned array aligns by index with
            // the order of the supplied pids.
            val pids = processes.map { it.pid }.toIntArray()
            val memoryInfos = if (pids.isNotEmpty()) {
                try {
                    am.getProcessMemoryInfo(pids)
                } catch (e: Exception) {
                    null
                }
            } else null

            val arr = JSONArray()
            val limited = processes.take(MAX_PROCESSES)
            limited.forEachIndexed { index, proc ->
                val memInfo = memoryInfos?.getOrNull(index)
                arr.put(JSONObject().apply {
                    put("processName", proc.processName)
                    put("pid", proc.pid)
                    put("uid", proc.uid)
                    put("importance", importanceLabel(proc.importance))
                    put("importanceValue", proc.importance)
                    put("lru", proc.lru)
                    if (proc.pkgList != null && proc.pkgList.isNotEmpty()) {
                        put("packages", JSONArray().apply {
                            proc.pkgList.forEach { put(it) }
                        })
                    }
                    if (memInfo != null) {
                        put("totalPssKb", memInfo.totalPss)
                        put("totalPrivateDirtyKb", memInfo.totalPrivateDirty)
                        put("totalSharedDirtyKb", memInfo.totalSharedDirty)
                    }
                })
            }

            val result = JSONObject().apply {
                put("count", processes.size)
                put("returned", limited.size)
                put("truncated", processes.size > MAX_PROCESSES)
                put("processes", arr)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to get running processes: ${e.message}")
        }
    }

    private fun importanceLabel(importance: Int): String = when (importance) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "foreground"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "foreground_service"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "visible"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "perceptible"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "cached"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "service"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING -> "top_sleeping"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE -> "gone"
        else -> "unknown"
    }

    companion object {
        private const val MAX_PROCESSES = 50
    }
}
