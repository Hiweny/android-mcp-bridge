package com.hiweny.mcpbridge

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hiweny.mcpbridge.mcp.McpTool
import com.hiweny.mcpbridge.mcp.ToolRegistry
import com.hiweny.mcpbridge.mcp.ToolResult
import com.hiweny.mcpbridge.service.McpForegroundService
import com.hiweny.mcpbridge.tools.DefaultTools
import com.hiweny.mcpbridge.ui.McpNavHost
import com.hiweny.mcpbridge.ui.Route
import com.hiweny.mcpbridge.ui.screens.ToolInfo
import com.hiweny.mcpbridge.ui.theme.McpBridgeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * 全局状态 ViewModel，使用 StateFlow 暴露服务器与设置状态。
 * 持有 [ToolRegistry] 以便工具列表与测试执行直接对接真实工具实现。
 */
class McpViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    /** MCP 工具注册表，注册全部内置工具 */
    private val toolRegistry: ToolRegistry = ToolRegistry().also {
        DefaultTools.registerAll(it, appContext)
    }

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _port = MutableStateFlow("8024")
    val port: StateFlow<String> = _port.asStateFlow()

    private val _ipAddress = MutableStateFlow("192.168.1.100")
    val ipAddress: StateFlow<String> = _ipAddress.asStateFlow()

    private val _toolCount = MutableStateFlow(0)
    val toolCount: StateFlow<Int> = _toolCount.asStateFlow()

    private val _autoStart = MutableStateFlow(false)
    val autoStart: StateFlow<Boolean> = _autoStart.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(false)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _tools = MutableStateFlow<List<ToolInfo>>(emptyList())
    val tools: StateFlow<List<ToolInfo>> = _tools.asStateFlow()

    init {
        refreshTools()
        updateIpAddress()
    }

    /** 获取设备局域网 IP 地址 */
    private fun updateIpAddress() {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        _ipAddress.value = addr.hostAddress ?: "127.0.0.1"
                        return
                    }
                }
            }
        } catch (e: Exception) {
            // 保持默认值
        }
    }

    /** 从注册表同步工具列表到 UI 状态 */
    private fun refreshTools() {
        val list = toolRegistry.getAll().map { tool ->
            ToolInfo(
                name = tool.name,
                description = tool.description,
                inputSchema = tool.inputSchema
            )
        }
        _tools.value = list
        _toolCount.value = list.size
    }

    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }

    fun setPort(port: String) {
        _port.value = port
    }

    fun setAutoStart(value: Boolean) {
        _autoStart.value = value
    }

    fun setKeepScreenOn(value: Boolean) {
        _keepScreenOn.value = value
    }

    fun setIpAddress(ip: String) {
        _ipAddress.value = ip
    }

    fun setToolCount(count: Int) {
        _toolCount.value = count
    }

    /**
     * 测试调用工具并返回结果字符串。
     * 注意：ToolsScreen 已在 IO 线程调用本方法，此处用 runBlocking 桥接 suspend execute。
     */
    fun testTool(name: String, args: JSONObject): String {
        val tool: McpTool? = toolRegistry.get(name)
        if (tool == null) {
            return JSONObject().apply {
                put("error", "未找到工具: $name")
            }.toString(2)
        }
        val result: ToolResult = runBlocking { tool.execute(args) }
        val json = JSONObject().apply {
            put("tool", name)
            put("success", result.success)
            if (result.success) {
                put("output", result.output)
            } else {
                put("error", result.error ?: "未知错误")
            }
        }
        return try {
            // 若输出本身是 JSON，则美化展示
            val parsed = JSONObject(result.output)
            json.put("output_pretty", parsed.toString(2))
            json.toString(2)
        } catch (e: Exception) {
            json.toString(2)
        }
    }
}

class MainActivity : ComponentActivity() {

    private val viewModel: McpViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> /* 结果忽略，已尽力请求 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            McpBridgeTheme {
                MainScreen()
            }
        }
    }

    /** Android 13+ 请求通知权限（前台服务通知需要） */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /** 启动前台服务 */
    private fun startServer() {
        val port = viewModel.port.value.toIntOrNull() ?: 8024
        val intent = Intent(this, McpForegroundService::class.java).apply {
            action = McpForegroundService.ACTION_START
            putExtra(McpForegroundService.EXTRA_PORT, port)
        }
        ContextCompat.startForegroundService(this, intent)
        viewModel.setRunning(true)
    }

    /** 停止前台服务 */
    private fun stopServer() {
        val intent = Intent(this, McpForegroundService::class.java).apply {
            action = McpForegroundService.ACTION_STOP
        }
        startService(intent)
        viewModel.setRunning(false)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreen() {
        val navController = rememberNavController()
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = currentBackStackEntry?.destination?.route

        // 屏幕常亮处理
        val keepScreenOn by viewModel.keepScreenOn.collectAsState()
        val context = LocalContext.current
        LaunchedEffect(keepScreenOn) {
            val activity = context as? Activity
            if (keepScreenOn) {
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = titleForRoute(currentRoute),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    bottomNavItems().forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route.route,
                            onClick = {
                                navController.navigate(item.route.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                McpNavHost(
                    navController = navController,
                    viewModel = viewModel,
                    onStartServer = { startServer() },
                    onStopServer = { stopServer() }
                )
            }
        }
    }

    private fun titleForRoute(route: String?): String = when (route) {
        Route.Home.route -> "MCP Bridge"
        Route.Tools.route -> "工具"
        Route.WebView.route -> "DeepSeek"
        Route.Settings.route -> "设置"
        else -> "MCP Bridge"
    }

    private fun bottomNavItems(): List<BottomNavItem> = listOf(
        BottomNavItem(Route.Home, "首页", Icons.Filled.Home),
        BottomNavItem(Route.Tools, "工具", Icons.Filled.Build),
        BottomNavItem(Route.WebView, "对话", Icons.Filled.Public),
        BottomNavItem(Route.Settings, "设置", Icons.Filled.Settings)
    )
}

private data class BottomNavItem(
    val route: Route,
    val label: String,
    val icon: ImageVector
)
