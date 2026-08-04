package com.hiweny.mcpbridge.web

import android.content.Context
import android.util.Log
import android.webkit.WebView

/**
 * 负责向 WebView 注入 GM_ polyfill 和 MCP Bridge 脚本。
 *
 * 注入顺序：
 * 1. GM_ polyfill（在页面 JS 之前注入，确保脚本运行时 GM_ 已定义）
 * 2. MCP Bridge 脚本（页面加载完成后注入）
 */
object ScriptInjector {

    private const val TAG = "ScriptInjector"

    /**
     * GM_ API polyfill。
     * 将 GM_xmlhttpRequest 转发到 AndroidBridge.gmXmlHttpRequest()，
     * GM_getValue/GM_setValue 使用 localStorage 持久化。
     */
    const val GM_POLYFILL = """
(function() {
    'use strict';

    // ── 回调注册表 ──
    window.__gmCallbacks = {};
    window.__gmCallbackId = 0;

    // ── GM_xmlhttpRequest polyfill ──
    window.GM_xmlhttpRequest = function(options) {
        var callbackId = '__gm_cb_' + (++window.__gmCallbackId);
        window.__gmCallbacks[callbackId] = {
            onload: options.onload || function() {},
            onerror: options.onerror || function() {},
            ontimeout: options.ontimeout || function() {}
        };

        var method = options.method || 'GET';
        var url = options.url;
        var headers = options.headers ? JSON.stringify(options.headers) : '{}';
        var data = options.data || '';

        // 调用 Android 桥接
        try {
            AndroidBridge.gmXmlHttpRequest(method, url, headers, data, callbackId);
        } catch(e) {
            console.error('[MCP Bridge] AndroidBridge call failed:', e);
            var cb = window.__gmCallbacks[callbackId];
            if (cb && cb.onerror) cb.onerror({message: 'AndroidBridge error: ' + e.message});
        }

        // 返回一个 dummy abort 函数
        return { abort: function() {} };
    };

    // ── GM_getValue polyfill (localStorage) ──
    window.GM_getValue = function(key, defaultValue) {
        try {
            var val = localStorage.getItem('gm_' + key);
            return val !== null ? val : defaultValue;
        } catch(e) {
            return defaultValue;
        }
    };

    // ── GM_setValue polyfill (localStorage) ──
    window.GM_setValue = function(key, value) {
        try {
            localStorage.setItem('gm_' + key, String(value));
        } catch(e) {}
    };

    // ── GM_addStyle polyfill ──
    window.GM_addStyle = function(css) {
        var style = document.createElement('style');
        style.textContent = css;
        (document.head || document.documentElement).appendChild(style);
        return style;
    };

    // ── GM_info polyfill ──
    window.GM_info = {
        scriptHandler: 'AndroidMcpBridge',
        version: '1.0.0',
        script: { name: 'MCP Bridge', version: '4.2.1-mobile' }
    };

    // ── 清理已完成的回调 ──
    window.__gmCleanupCallback = function(callbackId) {
        delete window.__gmCallbacks[callbackId];
    };

    console.log('[MCP Bridge] GM_ polyfill installed');
})();
"""

    /**
     * 从 assets 读取 MCP Bridge 脚本，去掉 Tampermonkey 元数据头。
     */
    fun loadBridgeScript(context: Context): String {
        return try {
            val full = context.assets.open("mcp-bridge-script.js").bufferedReader().use { it.readText() }
            // 去掉 // ==UserScript== ... // ==/UserScript== 块
            val startMarker = "// ==UserScript=="
            val endMarker = "// ==/UserScript=="
            val startIdx = full.indexOf(startMarker)
            if (startIdx >= 0) {
                val endIdx = full.indexOf(endMarker, startIdx)
                if (endIdx >= 0) {
                    val before = full.substring(0, startIdx).trim()
                    val after = full.substring(endIdx + endMarker.length).trim()
                    (if (before.isNotEmpty()) before + "\n" else "") + after
                } else {
                    full
                }
            } else {
                full
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bridge script", e)
            ""
        }
    }

    /**
     * 在页面开始加载时注入 GM_ polyfill（确保在页面脚本之前执行）。
     */
    fun injectPolyfill(webView: WebView) {
        webView.evaluateJavascript(GM_POLYFILL, null)
        Log.d(TAG, "Polyfill injected")
    }

    /**
     * 在页面加载完成后注入完整 MCP Bridge 脚本。
     */
    fun injectBridgeScript(webView: WebView, context: Context) {
        val script = loadBridgeScript(context)
        if (script.isNotEmpty()) {
            // 用 IIFE 包裹，避免污染全局
            val wrapped = """
                (function() {
                    'use strict';
                    try {
                    $script
                    } catch(e) {
                        console.error('[MCP Bridge] Script error:', e);
                    }
                })();
            """.trimIndent()
            webView.evaluateJavascript(wrapped, null)
            Log.d(TAG, "Bridge script injected (${script.length} chars)")
        } else {
            Log.e(TAG, "Bridge script is empty!")
        }
    }
}
