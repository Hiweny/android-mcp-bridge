package com.hiweny.mcpbridge.service

import com.hiweny.mcpbridge.mcp.JsonRpc
import com.hiweny.mcpbridge.mcp.ToolRegistry
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * 基于 NanoHTTPD 的 MCP JSON-RPC over HTTP 服务端。
 *
 * 线程模型：NanoHTTPD 在自有线程池中调用 [serve]，工具的 suspend [execute]
 * 通过 runBlocking 桥接，因此不会阻塞主线程。
 *
 * 支持的 JSON-RPC 方法：initialize / ping / tools/list / tools/call /
 * resources/list / prompts/list。
 */
class McpHttpServer(
    port: Int,
    private val toolRegistry: ToolRegistry,
    private val serverName: String = "MCP Bridge",
    private val serverVersion: String = "1.0.0"
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        // 仅接受 POST
        if (session.method != Method.POST) {
            return newFixedLengthResponse(
                Response.Status.METHOD_NOT_ALLOWED,
                MIME_PLAINTEXT,
                "Method not allowed: use POST"
            )
        }

        val body = readBody(session)
        val response = try {
            handleRpc(JSONObject(body))
        } catch (e: Exception) {
            JsonRpc.error(0, JsonRpc.PARSE_ERROR, "Parse error: ${e.message}")
        }

        val resp = newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            response.toString()
        )
        // 允许局域网内 AI 客户端跨源调用
        resp.addHeader("Access-Control-Allow-Origin", "*")
        resp.addHeader("Access-Control-Allow-Headers", "Content-Type, Accept")
        resp.addHeader("Access-Control-Allow-Methods", "POST, OPTIONS")
        return resp
    }

    private fun readBody(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        session.parseBody(files)
        // NanoHTTPD 将原始 body 存入 "postData"
        return files["postData"] ?: ""
    }

    private fun handleRpc(request: JSONObject): JSONObject {
        val method = request.optString("method")
        // id 可能是数字、字符串或 null
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
