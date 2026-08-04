package com.hiweny.mcpbridge.mcp

import org.json.JSONObject

/**
 * Interface for all MCP tools.
 * Each tool has a name, description, input schema, and an execute function.
 */
interface McpTool {
    val name: String
    val description: String

    /**
     * JSON Schema describing the tool's input parameters.
     * Example: {"type":"object","properties":{"format":{"type":"string","description":"Time format"}},"required":[]}
     */
    val inputSchema: JSONObject

    /**
     * Execute the tool with the given parameters.
     * Returns a [ToolResult] containing the output text.
     */
    suspend fun execute(params: JSONObject): ToolResult
}

/**
 * Result of a tool execution.
     */
data class ToolResult(
    val success: Boolean,
    val output: String,
    val error: String? = null
) {
    companion object {
        fun ok(text: String) = ToolResult(true, text, null)
        fun err(msg: String) = ToolResult(false, "", msg)
    }
}
