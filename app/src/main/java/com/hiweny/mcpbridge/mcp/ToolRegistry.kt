package com.hiweny.mcpbridge.mcp

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for MCP tools. Thread-safe.
 */
class ToolRegistry {
    private val tools = ConcurrentHashMap<String, McpTool>()

    fun register(tool: McpTool) {
        tools[tool.name] = tool
    }

    fun unregister(name: String) {
        tools.remove(name)
    }

    fun get(name: String): McpTool? = tools[name]

    fun getAll(): List<McpTool> = tools.values.toList()

    fun clear() {
        tools.clear()
    }

    /**
     * Returns tool definitions as a JSONArray for the MCP tools/list response.
     */
    fun listAsJson(): JSONArray {
        val arr = JSONArray()
        tools.values.forEach { tool ->
            arr.put(JSONObject().apply {
                put("name", tool.name)
                put("description", tool.description)
                put("inputSchema", tool.inputSchema)
            })
        }
        return arr
    }
}
