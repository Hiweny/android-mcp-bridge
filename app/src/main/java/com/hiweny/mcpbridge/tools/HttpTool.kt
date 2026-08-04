package com.hiweny.mcpbridge.tools

import com.hiweny.mcpbridge.mcp.McpTool
import com.hiweny.mcpbridge.mcp.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tool that performs an HTTP request.
 *
 * Parameters:
 *   - url     (required) the request URL
 *   - method  (optional, default "GET") HTTP method
 *   - headers (optional) JSON object of request headers
 *   - body    (optional) request body string (used for POST/PUT/PATCH)
 *
 * Connect timeout: 10s, Read timeout: 15s.
 * Response body is truncated to 5000 characters.
 *
 * This tool does not require an Android Context.
 */
class HttpTool : McpTool {

    override val name: String = "http_request"

    override val description: String =
        "Performs an HTTP GET/POST/PUT/PATCH/DELETE request and returns the status code and " +
            "response body (truncated to 5000 characters)."

    override val inputSchema: JSONObject = JSONObject().apply {
        put("type", "object")
        put("properties", JSONObject().apply {
            put("url", JSONObject().apply {
                put("type", "string")
                put("description", "The request URL.")
                put("format", "uri")
            })
            put("method", JSONObject().apply {
                put("type", "string")
                put("description", "HTTP method.")
                put("default", "GET")
                put("enum", JSONArray().apply {
                    put("GET").put("POST").put("PUT").put("PATCH").put("DELETE").put("HEAD")
                })
            })
            put("headers", JSONObject().apply {
                put("type", "object")
                put("description", "JSON object mapping header names to values.")
            })
            put("body", JSONObject().apply {
                put("type", "string")
                put("description", "Request body (used for POST/PUT/PATCH).")
            })
        })
        put("required", JSONArray().put("url"))
    }

    override suspend fun execute(params: JSONObject): ToolResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val urlStr = params.optString("url", "")
            if (urlStr.isEmpty()) {
                return@withContext ToolResult.err("Missing required parameter: url")
            }

            val httpMethod = params.optString("method", "GET").uppercase()
            val headers = params.optJSONObject("headers")
            val body = params.optString("body", "")
            val hasBody = body.isNotEmpty() &&
                (httpMethod == "POST" || httpMethod == "PUT" || httpMethod == "PATCH")

            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = httpMethod
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                useCaches = false
                if (hasBody) {
                    doOutput = true
                }
            }

            // Apply custom headers
            if (headers != null) {
                val keys = headers.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    connection.setRequestProperty(key, headers.optString(key))
                }
            }

            // Write request body
            if (hasBody) {
                connection.outputStream.use { out ->
                    out.write(body.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            }

            val statusCode = connection.responseCode
            val inputStream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val rawResponse = inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

            val truncated = rawResponse.length > MAX_RESPONSE_CHARS
            val response = if (truncated) rawResponse.substring(0, MAX_RESPONSE_CHARS) else rawResponse

            val result = JSONObject().apply {
                put("statusCode", statusCode)
                put("body", response)
                put("truncated", truncated)
                put("originalLength", rawResponse.length)
                put("url", urlStr)
                put("method", httpMethod)
            }
            ToolResult.ok(result.toString())
        } catch (e: Exception) {
            ToolResult.err("HTTP request failed: ${e.message}")
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val MAX_RESPONSE_CHARS = 5000
    }
}
