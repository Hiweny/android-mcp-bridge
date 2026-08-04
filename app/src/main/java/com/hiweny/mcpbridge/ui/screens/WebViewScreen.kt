package com.hiweny.mcpbridge.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hiweny.mcpbridge.web.McpWebBridge
import com.hiweny.mcpbridge.web.ScriptInjector

@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
@Composable
fun WebViewScreen(
    url: String = "https://chat.deepseek.com",
    serverPort: Int = 8024,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var injectionStatus by remember { mutableStateOf("等待页面加载...") }

    BackHandler(enabled = true) {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶部加载进度条
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        allowFileAccess = true
                        allowContentAccess = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        mediaPlaybackRequiresUserGesture = false
                        // 允许混合内容（DeepSeek 页面可能有 HTTP 资源）
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    }

                    // 添加 AndroidBridge JavascriptInterface
                    addJavascriptInterface(
                        McpWebBridge(this, serverPort),
                        "AndroidBridge"
                    )

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            // 在页面脚本执行前注入 polyfill
                            ScriptInjector.injectPolyfill(this@apply)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // 页面加载完成后注入完整 MCP Bridge 脚本
                            ScriptInjector.injectBridgeScript(this@apply, ctx)
                            injectionStatus = "MCP 脚本已注入"

                            // 延迟再次注入，确保 SPA 路由切换后脚本仍然生效
                            this@apply.postDelayed({
                                ScriptInjector.injectPolyfill(this@apply)
                                ScriptInjector.injectBridgeScript(this@apply, ctx)
                            }, 2000)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                            // 在 WebView 内部打开所有链接
                            return false
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }

                        override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                            Log.d("WebViewConsole", message?.message() ?: "")
                            return true
                        }
                    }

                    loadUrl(url)
                    webViewRef = this
                }
            },
            update = { webView ->
                // 不在 update 中重新加载，避免循环
            }
        )
    }
}
