package com.hiweny.mcpbridge.mcp

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON-RPC 2.0 protocol models and helpers.
 */
object JsonRpc {

    const val VERSION = "2.0"

    // ── Standard error codes ──
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603

    fun response(id: Any, result: JSONObject): JSONObject {
        return JSONObject().apply {
            put("jsonrpc", VERSION)
            put("id", id)
            put("result", result)
        }
    }

    fun error(id: Any, code: Int, message: String, data: Any? = null): JSONObject {
        return JSONObject().apply {
            put("jsonrpc", VERSION)
            put("id", id)
            val err = JSONObject().apply {
                put("code", code)
                put("message", message)
                if (data != null) put("data", data)
            }
            put("error", err)
        }
    }

    /**
     * Build a tools/list result.
     */
    fun toolsListResult(tools: JSONArray): JSONObject {
        return JSONObject().apply {
            put("tools", tools)
        }
    }

    /**
     * Build a tools/call result from a [ToolResult].
     */
    fun toolsCallResult(toolResult: ToolResult): JSONObject {
        return JSONObject().apply {
            val content = JSONArray()
            if (toolResult.success) {
                content.put(JSONObject().apply {
                    put("type", "text")
                    put("text", toolResult.output)
                })
            } else {
                content.put(JSONObject().apply {
                    put("type", "text")
                    put("text", "Error: ${toolResult.error ?: "Unknown error"}")
                })
                put("isError", true)
            }
            put("content", content)
        }
    }

    /**
     * Build an initialize result.
     */
    fun initializeResult(serverName: String, serverVersion: String): JSONObject {
        return JSONObject().apply {
            put("protocolVersion", "2024-11-05")
            put("capabilities", JSONObject().apply {
                put("tools", JSONObject())
                put("resources", JSONObject())
                put("prompts", JSONObject())
            })
            put("serverInfo", JSONObject().apply {
                put("name", serverName)
                put("version", serverVersion)
            })
        }
    }

    /**
     * Build a resources/list result (empty for now).
     */
    fun resourcesListResult(): JSONObject {
        return JSONObject().apply {
            put("resources", JSONArray())
        }
    }

    /**
     * Build a prompts/list result (empty for now).
     */
    fun promptsListResult(): JSONObject {
        return JSONObject().apply {
            put("prompts", JSONArray())
        }
    }
}
