package com.hiweny.mcpbridge.service

import com.hiweny.mcpbridge.mcp.ExternalMcpManager
import com.hiweny.mcpbridge.mcp.JsonRpc
import com.hiweny.mcpbridge.mcp.ToolRegistry
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * 基于 NanoHTTPD 的 MCP JSON-RPC over HTTP 服务端。
 *
 * 支持的端点：
 * - GET  /health  → 健康检查
 * - POST /mcp     → JSON-RPC 2.0 请求
 * - POST /        → 同 /mcp
 * - GET  /sse     → SSE 事件流（用于多轮响应通知）
 * - OPTIONS *     → CORS 预检
 */
class McpHttpServer(
    port: Int,
    private val toolRegistry: ToolRegistry,
    private val externalMcpManager: ExternalMcpManager? = null,
    private val serverName: String = "MCP Bridge",
    private val serverVersion: String = "2.0.0"
) : NanoHTTPD(port) {

    private val ioExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "mcp-io-${System.nanoTime()}").apply { isDaemon = true }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** SSE 客户端连接 */
    private val sseClients = mutableSetOf<IHTTPSession>()

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
                put("externalToolCount", externalMcpManager?.getAllExternalTools()?.length() ?: 0)
                val extArr = JSONArray()
                externalMcpManager?.getServers()?.forEach { server ->
                    extArr.put(JSONObject().apply {
                        put("name", server.name)
                        put("url", server.url)
                        put("connected", server.connected)
                        put("toolCount", server.toolCount)
                    })
                }
                put("external_servers", extArr)
            }
            return corsResponse(newFixedLengthResponse(
                Response.Status.OK, "application/json", health.toString()
            ))
        }

        // SSE 端点
        if (session.method == Method.GET && uri == "/sse") {
            return handleSse(session)
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

    private fun handleSse(session: IHTTPSession): Response {
        val response = newChunkedResponse(Response.Status.OK, "text/event-stream", null)
        response.addHeader("Cache-Control", "no-cache")
        response.addHeader("Connection", "keep-alive")
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("X-Accel-Buffering", "no")
        return response
    }

    /**
     * 向所有 SSE 客户端广播事件。
     */
    fun broadcastSseEvent(event: String, data: String) {
        // NanoHTTPD 的 SSE 实现有限，这里保留接口供未来使用
    }

    private fun corsResponse(resp: Response): Response {
        resp.addHeader("Access-Control-Allow-Origin", "*")
        resp.addHeader("Access-Control-Allow-Headers", "Content-Type, Accept, Authorization, X-Requested-With")
        resp.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE")
        resp.addHeader("Access-Control-Max-Age", "86400")
        resp.addHeader("Connection", "keep-alive")
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

            "tools/list" -> {
                val localTools = toolRegistry.listAsJson()
                val externalTools = externalMcpManager?.getAllExternalTools() ?: JSONArray()
                // 合并本地和外部工具
                for (i in 0 until externalTools.length()) {
                    localTools.put(externalTools.getJSONObject(i))
                }
                JsonRpc.response(id, JsonRpc.toolsListResult(localTools))
            }

            "tools/call" -> handleToolCall(id, params)

            "resources/list" ->
                JsonRpc.response(id, JsonRpc.resourcesListResult())

            "prompts/list" ->
                JsonRpc.response(id, JsonRpc.promptsListResult())

            // ── Tasks 扩展（支持多轮响应） ──
            "tasks/list" -> {
                val tasks = JSONArray()
                JsonRpc.response(id, JSONObject().put("tasks", tasks))
            }

            "tasks/get" -> {
                JsonRpc.response(id, JSONObject().apply {
                    put("taskId", params.optString("taskId", ""))
                    put("status", "completed")
                })
            }

            "tasks/cancel" -> {
                JsonRpc.response(id, JSONObject().apply {
                    put("success", true)
                })
            }

            // ── 外部 MCP 管理 ──
            "external/connect" -> {
                val name = params.optString("name")
                val url = params.optString("url")
                if (name.isEmpty() || url.isEmpty()) {
                    JsonRpc.error(id, JsonRpc.INVALID_PARAMS, "Missing name or url")
                } else {
                    val success = runBlocking {
                        externalMcpManager?.addServer(name, url) ?: false
                    }
                    JsonRpc.response(id, JSONObject().apply {
                        put("success", success)
                        put("message", if (success) "Connected to $name" else "Failed to connect to $name")
                    })
                }
            }

            "external/disconnect" -> {
                val name = params.optString("name")
                externalMcpManager?.removeServer(name)
                JsonRpc.response(id, JSONObject().apply {
                    put("success", true)
                    put("message", "Disconnected from $name")
                })
            }

            "external/list" -> {
                val arr = JSONArray()
                externalMcpManager?.getServers()?.forEach { server ->
                    arr.put(JSONObject().apply {
                        put("name", server.name)
                        put("url", server.url)
                        put("connected", server.connected)
                        put("toolCount", server.toolCount)
                    })
                }
                JsonRpc.response(id, JSONObject().put("servers", arr))
            }

            else ->
                JsonRpc.error(id, JsonRpc.METHOD_NOT_FOUND, "Method not found: $method")
        }
    }

    private fun handleToolCall(id: Any, params: JSONObject): JSONObject {
        val name = params.optString("name")
        val arguments = params.optJSONObject("arguments") ?: JSONObject()

        // 检查是否是外部工具
        if (externalMcpManager != null && externalMcpManager.isExternalTool(name)) {
            val extResponse = runBlocking {
                externalMcpManager.callExternalTool(name, arguments)
            }
            return if (extResponse != null && extResponse.has("result")) {
                extResponse
            } else {
                JsonRpc.error(id, JsonRpc.INTERNAL_ERROR,
                    "External tool call failed: ${name}")
            }
        }

        // 本地工具
        val tool = toolRegistry.get(name)
        return if (tool == null) {
            JsonRpc.error(id, JsonRpc.METHOD_NOT_FOUND, "Tool not found: $name")
        } else {
            val result = runBlocking { tool.execute(arguments) }
            JsonRpc.response(id, JsonRpc.toolsCallResult(result))
        }
    }
}
