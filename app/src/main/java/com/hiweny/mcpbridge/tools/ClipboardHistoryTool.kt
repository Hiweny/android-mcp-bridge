package com.hiweny.mcpbridge.tools

import android.content.ClipboardManager
import android.content.Context
import com.hiweny.mcpbridge.mcp.McpTool
import com.hiweny.mcpbridge.mcp.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tool that returns the clipboard history recorded by this app.
 *
 * Android does not expose a native clipboard-history API, so this tool maintains a lightweight
 * history in [android.content.SharedPreferences]. A [ClipboardManager.OnPrimaryClipChangedListener]
 * is registered (exactly once, guarded by a @Volatile flag) to capture every primary-clip change
 * and append it to the persisted history. Each record contains content, timestamp and label.
 * At most [MAX_HISTORY] entries are kept (oldest are dropped).
 *
 * Parameters:
 *  - limit (integer, default 20) — maximum number of entries to return
 */
class ClipboardHistoryTool(private val context: Context) : McpTool {

    override val name: String = "clipboard_history"

    override val description: String =
        "Returns the recent clipboard history recorded by this app. Each entry contains " +
            "content, timestamp and label. Most recent entries are returned first."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("limit", JSONObject().apply {
                put("type", "integer")
                put("description", "Maximum number of history entries to return.")
                put("default", 20)
                put("minimum", 1)
                put("maximum", 100)
            })
        })
        put("required", JSONArray())
    }

    @Volatile
    private var listenerRegistered = false

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val clipboard by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        try {
            recordCurrentClip()
        } catch (e: Exception) {
            // A failure to record one entry must not crash the listener thread.
        }
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        try {
            // Ensure the listener is registered exactly once.
            ensureListenerRegistered()

            val limit = params.optInt("limit", 20).coerceIn(1, MAX_HISTORY)
            val history = readHistory()
            val total = history.length()

            // Return most recent entries first.
            val count = minOf(limit, total)
            val sliced = JSONArray()
            for (i in 0 until count) {
                val entry = history.optJSONObject(total - 1 - i)
                if (entry != null) sliced.put(entry)
            }

            val result = JSONObject().apply {
                put("count", sliced.length())
                put("total", total)
                put("limit", limit)
                put("history", sliced)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("Failed to read clipboard history: ${e.message}")
        }
    }

    /**
     * Registers the clip-changed listener at most once.
     * Synchronized so concurrent execute() calls cannot double-register.
     */
    @Synchronized
    private fun ensureListenerRegistered() {
        if (listenerRegistered) return
        try {
            clipboard.addPrimaryClipChangedListener(clipListener)
            listenerRegistered = true
        } catch (e: Exception) {
            // Listener could not be registered; reads still work but history won't accumulate.
        }
    }

    /**
     * Reads the current primary clip and appends it to the persisted history.
     * Skips empty content and exact duplicates of the most recent entry.
     */
    private fun recordCurrentClip() {
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return

        val item = clip.getItemAt(0)
        val content = item.text?.toString() ?: return
        if (content.isEmpty()) return

        val label = clip.description?.label?.toString() ?: ""

        val history = readHistory()

        // Skip exact duplicate of the most recent entry.
        if (history.length() > 0) {
            val last = history.optJSONObject(history.length() - 1)
            if (last != null && last.optString("content") == content) {
                return
            }
        }

        val entry = JSONObject().apply {
            put("content", content)
            put("timestamp", System.currentTimeMillis())
            put("label", label)
        }

        history.put(entry)

        // Trim to MAX_HISTORY, dropping the oldest entries.
        while (history.length() > MAX_HISTORY) {
            history.remove(0)
        }

        prefs.edit().putString(KEY_HISTORY, history.toString()).apply()
    }

    private fun readHistory(): JSONArray {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return JSONArray()
        return try {
            JSONArray(raw)
        } catch (e: Exception) {
            JSONArray()
        }
    }

    companion object {
        private const val PREFS_NAME = "mcp_clipboard_history"
        private const val KEY_HISTORY = "history_json"
        private const val MAX_HISTORY = 100
    }
}
