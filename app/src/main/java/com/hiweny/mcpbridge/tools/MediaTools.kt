package com.hiweny.mcpbridge.tools

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import com.hiweny.mcpbridge.mcp.McpTool
import com.hiweny.mcpbridge.mcp.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool that captures a screenshot.
 *
 * Capturing the real screen requires a MediaProjection that can only be obtained after the
 * user grants permission through an Activity (MediaProjectionManager.createScreenCaptureIntent()).
 * This tool runs in a background service context where no such Activity flow is available, so it
 * returns the screen dimensions together with an authorization hint. On API >= 29 PixelCopy may
 * alternatively be used to copy a specific Window.
 */
class ScreenshotTool(private val context: Context) : McpTool {

    override val name: String = "take_screenshot"

    override val description: String =
        "Captures a screenshot. Real screen capture requires MediaProjection user " +
            "authorization (granted via an Activity). Returns screen dimensions and " +
            "authorization instructions when running without a granted projection."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject())
        put("required", JSONArray())
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            val apiLevel = Build.VERSION.SDK_INT
            val message = buildString {
                append("截屏需要用户授权 MediaProjection。")
                append("当前工具运行在后台 Service 上下文，无法直接弹出授权界面。")
                append("请先在 Activity 中调用 MediaProjectionManager.createScreenCaptureIntent() 发起授权，")
                append("取得 resultCode 与 Intent 后用 getMediaProjection(resultCode, intent) 获得 MediaProjection 实例，")
                append("再复用该实例进行截屏。")
                if (apiLevel >= Build.VERSION_CODES.Q) {
                    append(" 另：API >= 29 时也可尝试使用 PixelCopy.request() 从指定 Window 截取画面。")
                }
            }

            val result = JSONObject().apply {
                put("captured", false)
                put("screenWidth", metrics.widthPixels)
                put("screenHeight", metrics.heightPixels)
                put("densityDpi", metrics.densityDpi)
                put("apiLevel", apiLevel)
                put("message", message)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to capture screenshot: ${e.message}")
        }
    }
}

/**
 * Tool that lists media files from the MediaStore.
 *
 * Parameters:
 *   - media_type (optional, default "image", enum: image/video/audio)
 *   - limit      (optional, default 50, max 500)
 *
 * Uses MediaStore. On API >= 29 queries MediaStore.VOLUME_EXTERNAL. Returns name, path,
 * size, date added and duration (for video/audio).
 */
class ListMediaFilesTool(private val context: Context) : McpTool {

    override val name: String = "list_media_files"

    override val description: String =
        "Lists media files (image/video/audio) from MediaStore. Returns name, path, size, " +
            "date added and duration (for video/audio)."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("media_type", JSONObject().apply {
                put("type", "string")
                put("description", "Type of media to list.")
                put("enum", JSONArray().put("image").put("video").put("audio"))
                put("default", "image")
            })
            put("limit", JSONObject().apply {
                put("type", "integer")
                put("description", "Maximum number of items to return.")
                put("default", 50)
                put("minimum", 1)
            })
        })
        put("required", JSONArray())
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val mediaType = params.optString("media_type", "image").trim().lowercase()
            if (mediaType !in listOf("image", "video", "audio")) {
                return@withContext ToolResult.err(
                    "Invalid media_type: $mediaType. Must be one of: image, video, audio"
                )
            }
            val limit = params.optInt("limit", 50).coerceIn(1, MAX_LIMIT)

            val collection: Uri
            val durationColumn: String?
            when (mediaType) {
                "image" -> {
                    collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    durationColumn = null
                }
                "video" -> {
                    collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    } else {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    }
                    durationColumn = MediaStore.Video.Media.DURATION
                }
                else -> { // audio
                    collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    } else {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                    durationColumn = MediaStore.Audio.Media.DURATION
                }
            }

            val projection = if (durationColumn != null) {
                arrayOf(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_ADDED,
                    durationColumn
                )
            } else {
                arrayOf(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_ADDED
                )
            }
            val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

            val items = JSONArray()
            var count = 0
            context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val durIdx = if (durationColumn != null) {
                    cursor.getColumnIndexOrThrow(durationColumn)
                } else {
                    -1
                }

                while (cursor.moveToNext() && count < limit) {
                    val name = cursor.getString(nameIdx) ?: ""
                    val path = cursor.getString(dataIdx) ?: ""
                    val size = cursor.getLong(sizeIdx)
                    val dateAdded = cursor.getLong(dateIdx)
                    val duration = if (durIdx >= 0) cursor.getLong(durIdx) else 0L

                    items.put(JSONObject().apply {
                        put("name", name)
                        put("path", path)
                        put("size", size)
                        put("dateAdded", dateAdded)
                        if (durationColumn != null) {
                            put("durationMs", duration)
                        }
                    })
                    count++
                }
            }

            val result = JSONObject().apply {
                put("mediaType", mediaType)
                put("limit", limit)
                put("count", count)
                put("items", items)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to list media files: ${e.message}")
        }
    }

    companion object {
        private const val MAX_LIMIT = 500
    }
}
