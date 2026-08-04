package com.hiweny.mcpbridge.service

import com.hiweny.mcpbridge.mcp.JsonRpc
import com.hiweny.mcpbridge.mcp.ToolRegistry
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * 基于 NanoHTTPD 的 MCP JSON-RPC over HTTP 服务端。
 *
 * 支持的端点：
 * - GET  /health  → 健康检查，返回 {"status":"ok"}
 * - POST /mcp     → JSON-RPC 2.0 请求（initialize / tools/list / tools/call 等）
 * - POST /        → 同 /mcp（兼容不带路径的请求）
 * - OPTIONS *     → CORS 预检
 */
class McpHttpServer(
    port: Int,
    private val toolRegistry: ToolRegistry,
    private val serverName: String = "MCP Bridge",
    private val serverVersion: String = "1.0.0"
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"

        // CORS 预检
        if (session.method == Method.OPTIONS) {
            return corsResponse(newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, ""))
        }

        // 健康检查
        if (session.method == Method.GET && (uri == "/health" || uri == "/api/health")) {
            val health = JSONObject().apply {
                put("status", "ok")
                put("server", serverName)
                put("version", serverVersion)
                put("toolCount", toolRegistry.getAll().size)
                put("external_servers", org.json.JSONArray())
            }
            return corsResponse(newFixedLengthResponse(
                Response.Status.OK, "application/json", health.toString()
            ))
        }

        // JSON-RPC 端点：POST /mcp 或 POST /
        if (session.method == Method.POST) {
            val body = readBody(session)
            val rpcResponse = try {
                handleRpc(JSONObject(body))
            } catch (e: Exception) {
                JsonRpc.error(0, JsonRpc.PARSE_ERROR, "Parse error: ${e.message}")
            }
            return corsResponse(newFixedLengthResponse(
                Response.Status.OK, "application/json", rpcResponse.toString()
            ))
        }

        // 其他请求
        return corsResponse(newFixedLengthResponse(
            Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found: $uri"
        ))
    }

    private fun corsResponse(resp: Response): Response {
        resp.addHeader("Access-Control-Allow-Origin", "*")
        resp.addHeader("Access-Control-Allow-Headers", "Content-Type, Accept, Authorization")
        resp.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE")
        return resp
    }

    private fun readBody(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        session.parseBody(files)
        return files["postData"] ?: ""
    }

    private fun handleRpc(request: JSONObject): JSONObject {
        val method = request.optString("method")
        val id = request.opt("id")
        val params = request.optJSONObject("params") ?: JSONObject()

        return when (method) {
            "initialize" ->
                JsonRpc.response(id, JsonRpc.initializeResult(serverName, serverVersion))

            "ping" ->
                JsonRpc.response(id, JSONObject())

            "tools/list" ->
                JsonRpc.response(id, JsonRpc.toolsListResult(toolRegistry.listAsJson()))

            "tools/call" -> handleToolCall(id, params)

            "resources/list" ->
                JsonRpc.response(id, JsonRpc.resourcesListResult())

            "prompts/list" ->
                JsonRpc.response(id, JsonRpc.promptsListResult())

            else ->
                JsonRpc.error(id, JsonRpc.METHOD_NOT_FOUND, "Method not found: $method")
        }
    }

    private fun handleToolCall(id: Any, params: JSONObject): JSONObject {
        val name = params.optString("name")
        val arguments = params.optJSONObject("arguments") ?: JSONObject()
        val tool = toolRegistry.get(name)
        return if (tool == null) {
            JsonRpc.error(id, JsonRpc.METHOD_NOT_FOUND, "Tool not found: $name")
        } else {
            val result = runBlocking { tool.execute(arguments) }
            JsonRpc.response(id, JsonRpc.toolsCallResult(result))
        }
    }
}
