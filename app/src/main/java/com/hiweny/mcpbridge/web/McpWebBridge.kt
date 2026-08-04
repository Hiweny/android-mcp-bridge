package com.hiweny.mcpbridge.web

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * JavaScript ↔ Android 桥接层。
 *
 * 提供以下 JS 可调用的方法：
 * - [gmXmlHttpRequest]  替代 GM_xmlhttpRequest，在 Android 原生线程发起 HTTP 请求
 * - [gmGetValue]        替代 GM_getValue，使用 localStorage 持久化
 * - [gmSetValue]        替代 GM_setValue
 * - [getServerUrl]      返回当前 MCP 服务器地址（localhost:8024）
 *
 * 调用流程：
 * 1. JS 调用 AndroidBridge.gmXmlHttpRequest(method, url, headers, data, callbackId)
 * 2. Android 在后台线程发起 HTTP 请求
 * 3. 完成后通过 webView.evaluateJavascript() 回调 JS 的 onload/onerror
 */
class McpWebBridge(
    private val webView: WebView,
    private val serverPort: Int
) {
    companion object {
        private const val TAG = "McpWebBridge"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newCachedThreadPool()

    /** MCP 服务器基础 URL */
    @JavascriptInterface
    fun getServerUrl(): String = "http://localhost:$serverPort"

    @JavascriptInterface
    fun getMcpUrl(): String = "http://localhost:$serverPort/mcp"

    /**
     * 替代 GM_xmlhttpRequest。
     * JS 侧用法：
     *   AndroidBridge.gmXmlHttpRequest('GET', url, '{}', '', 'cb_123')
     * 完成后回调：
     *   window.__gmCallback('cb_123', 'onload', {status:200, responseText:'...'})
     *   window.__gmCallback('cb_123', 'onerror', {message:'...'})
     */
    @JavascriptInterface
    fun gmXmlHttpRequest(
        method: String,
        url: String,
        headersJson: String,
        data: String,
        callbackId: String
    ) {
        executor.execute {
            try {
                Log.d(TAG, "Request: $method $url")

                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 10000
                    readTimeout = 15000
                    instanceFollowRedirects = true
                    doInput = true
                }

                // 设置请求头
                if (headersJson.isNotEmpty() && headersJson != "{}") {
                    try {
                        val headers = JSONObject(headersJson)
                        headers.keys().forEach { key ->
                            conn.setRequestProperty(key, headers.getString(key))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse headers: $headersJson", e)
                    }
                }

                // 写入请求体
                if (data.isNotEmpty() && (method == "POST" || method == "PUT" || method == "PATCH")) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    val out: OutputStream = conn.outputStream
                    out.write(data.toByteArray(Charsets.UTF_8))
                    out.flush()
                    out.close()
                }

                val statusCode = conn.responseCode
                val responseBody = try {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) sb.append(line)
                    reader.close()
                    sb.toString()
                } catch (e: Exception) {
                    // 读取错误流
                    try {
                        val reader = BufferedReader(InputStreamReader(conn.errorStream, Charsets.UTF_8))
                        val sb = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) sb.append(line)
                        reader.close()
                        sb.toString()
                    } catch (e2: Exception) { "" }
                }

                conn.disconnect()

                Log.d(TAG, "Response: $statusCode (${responseBody.length} chars)")

                // 回调 JS
                val escapedBody = JSONObject.quote(responseBody)
                val callbackJs = """
                    (function() {
                        var cb = window.__gmCallbacks && window.__gmCallbacks['$callbackId'];
                        if (cb && cb.onload) {
                            try { cb.onload({status: $statusCode, responseText: $escapedBody, finalUrl: '$url'}); }
                            catch(e) { console.error('GM onload error:', e); }
                        }
                    })();
                """.trimIndent()

                handler.post {
                    webView.evaluateJavascript(callbackJs, null)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Request failed: $method $url", e)
                val errorMsg = e.message ?: "Unknown error"
                val escapedMsg = JSONObject.quote(errorMsg)
                val callbackJs = """
                    (function() {
                        var cb = window.__gmCallbacks && window.__gmCallbacks['$callbackId'];
                        if (cb && cb.onerror) {
                            try { cb.onerror({message: $escapedMsg, error: $escapedMsg}); }
                            catch(e) { console.error('GM onerror error:', e); }
                        }
                    })();
                """.trimIndent()

                handler.post {
                    webView.evaluateJavascript(callbackJs, null)
                }
            }
        }
    }

    /**
     * 替代 GM_getValue。使用 Android SharedPreferences 持久化。
     */
    @JavascriptInterface
    fun gmGetValue(key: String, defaultValue: String): String {
        // 使用 localStorage 由 JS 侧管理，这里只返回默认值
        // 实际的持久化在 JS polyfill 中用 localStorage 实现
        return defaultValue
    }

    @JavascriptInterface
    fun gmSetValue(key: String, value: String) {
        // JS polyfill 会用 localStorage 存储
    }

    @JavascriptInterface
    fun log(msg: String) {
        Log.d(TAG, "JS: $msg")
    }
}
