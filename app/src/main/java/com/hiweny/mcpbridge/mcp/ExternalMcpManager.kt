package com.hiweny.mcpbridge.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * 外部 MCP 服务器管理器。
 *
 * 管理 APK 作为客户端连接到的远程 MCP 服务器，
 * 将工具调用请求转发到远程服务器并返回结果。
 *
 * 远程工具名称以 `ext_<serverName>_` 为前缀，避免与本地工具冲突。
 */
class ExternalMcpManager {

    data class ExternalServer(
        val name: String,
        val url: String,
        var connected: Boolean = false,
        var toolCount: Int = 0,
        val tools: MutableList<JSONObject> = mutableListOf()
    )

    private val servers = ConcurrentHashMap<String, ExternalServer>()

    /** 已注册的外部服务器列表 */
    fun getServers(): List<ExternalServer> = servers.values.toList()

    /** 添加外部服务器并尝试连接 */
    suspend fun addServer(name: String, url: String): Boolean = withContext(Dispatchers.IO) {
        if (servers.containsKey(name)) return@withContext false
        val server = ExternalServer(name = name, url = url)
        servers[name] = server
        // 尝试初始化连接并获取工具列表
        val connected = tryConnect(server)
        server.connected = connected
        connected
    }

    /** 移除外部服务器 */
    fun removeServer(name: String) {
        servers.remove(name)
    }

    /** 尝试重新连接所有服务器 */
    suspend fun reconnectAll(): Int = withContext(Dispatchers.IO) {
        var count = 0
        servers.values.forEach { server ->
            server.connected = tryConnect(server)
            if (server.connected) count++
        }
        count
    }

    /** 尝试连接服务器并获取工具列表 */
    private suspend fun tryConnect(server: ExternalServer): Boolean = withContext(Dispatchers.IO) {
        try {
            // 发送 initialize 请求
            val initResponse = sendRpc(server.url, JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "initialize")
                put("params", JSONObject().apply {
                    put("protocolVersion", "2024-11-05")
                    put("capabilities", JSONObject())
                    put("clientInfo", JSONObject().apply {
                        put("name", "MCP Bridge Android")
                        put("version", "1.0.0")
                    })
                })
            })

            if (initResponse == null) return@withContext false

            // 获取工具列表
            val toolsResponse = sendRpc(server.url, JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 2)
                put("method", "tools/list")
                put("params", JSONObject())
            })

            if (toolsResponse != null) {
                server.tools.clear()
                val toolsArray = toolsResponse.optJSONObject("result")?.optJSONArray("tools")
                if (toolsArray != null) {
                    for (i in 0 until toolsArray.length()) {
                        val tool = toolsArray.getJSONObject(i)
                        // 为远程工具添加前缀
                        val originalName = tool.optString("name")
                        val prefixedName = "ext_${server.name}_$originalName"
                        val prefixedTool = JSONObject(tool.toString())
                        prefixedTool.put("name", prefixedName)
                        prefixedTool.put("description",
                            "[${server.name}] ${tool.optString("description", "")}")
                        server.tools.add(prefixedTool)
                    }
                    server.toolCount = server.tools.size
                }
                true
            } else {
                // initialize 成功但 tools/list 失败，仍然认为已连接
                server.toolCount = 0
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取所有外部服务器的工具列表（带前缀）。
     */
    fun getAllExternalTools(): JSONArray {
        val arr = JSONArray()
        servers.values.filter { it.connected }.forEach { server ->
            server.tools.forEach { tool ->
                arr.put(tool)
            }
        }
        return arr
    }

    /**
     * 调用外部工具。
     * @param prefixedToolName 带前缀的工具名 (ext_<serverName>_<originalName>)
     */
    suspend fun callExternalTool(prefixedToolName: String, arguments: JSONObject): JSONObject? {
        // 解析前缀获取服务器名和原始工具名
        if (!prefixedToolName.startsWith("ext_")) return null

        val withoutPrefix = prefixedToolName.removePrefix("ext_")
        val serverName = withoutPrefix.substringBefore("_", "")
        val originalToolName = withoutPrefix.substringAfter("_", "")

        if (serverName.isEmpty() || originalToolName.isEmpty()) return null

        val server = servers[serverName] ?: return null
        if (!server.connected) return null

        return withContext(Dispatchers.IO) {
            sendRpc(server.url, JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", System.currentTimeMillis().toInt())
                put("method", "tools/call")
                put("params", JSONObject().apply {
                    put("name", originalToolName)
                    put("arguments", arguments)
                })
            })
        }
    }

    /** 检查工具名是否属于外部服务器 */
    fun isExternalTool(toolName: String): Boolean {
        return toolName.startsWith("ext_") && servers.values.any { server ->
            toolName.startsWith("ext_${server.name}_")
        }
    }

    /** 发送 JSON-RPC 请求到远程服务器 */
    private fun sendRpc(url: String, request: JSONObject): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val out = connection.outputStream
            out.write(request.toString().toByteArray(Charsets.UTF_8))
            out.flush()
            out.close()

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

            if (response.isNotEmpty()) {
                JSONObject(response)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
