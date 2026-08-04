package com.hiweny.mcpbridge.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.hiweny.mcpbridge.mcp.McpTool
import com.hiweny.mcpbridge.mcp.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool that reads the current contents of the system clipboard.
 *
 * No required parameters.
 */
class ClipboardReadTool(private val context: Context) : McpTool {

    override val name: String = "clipboard_read"

    override val description: String =
        "Reads the current text content from the system clipboard."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject())
        put("required", JSONArray())
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData? = clipboard.primaryClip

            if (clip == null || clip.itemCount == 0) {
                val empty = JSONObject().apply {
                    put("content", "")
                    put("type", "empty")
                    put("length", 0)
                }
                return@withContext ToolResult.ok(empty.toString())
            }

            val item = clip.getItemAt(0)
            val text = item.text?.toString() ?: ""
            val type = when {
                item.text != null -> "text"
                item.uri != null -> "uri"
                item.intent != null -> "intent"
                else -> "unknown"
            }

            val result = JSONObject().apply {
                put("content", text)
                put("type", type)
                put("length", text.length)
                put("label", clip.description?.label?.toString() ?: "")
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to read clipboard: ${e.message}")
        }
    }
}

/**
 * Tool that writes text to the system clipboard.
 *
 * Parameters:
 *   - text (required) the text to place on the clipboard
 */
class ClipboardWriteTool(private val context: Context) : McpTool {

    override val name: String = "clipboard_write"

    override val description: String =
        "Writes the given text to the system clipboard."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("text", JSONObject().apply {
                put("type", "string")
                put("description", "The text content to write to the clipboard.")
            })
        })
        put("required", JSONArray().put("text"))
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            if (!params.has("text")) {
                return@withContext ToolResult.err("Missing required parameter: text")
            }
            val text = params.optString("text", "")
            if (text.isEmpty()) {
                return@withContext ToolResult.err("Parameter 'text' must not be empty")
            }

            val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("MCP Bridge", text)
            clipboard.setPrimaryClip(clip)

            val result = JSONObject().apply {
                put("success", true)
                put("length", text.length)
                put("message", "Text written to clipboard")
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to write clipboard: ${e.message}")
        }
    }
}
