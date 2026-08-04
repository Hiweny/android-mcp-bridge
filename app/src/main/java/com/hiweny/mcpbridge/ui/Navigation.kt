package com.hiweny.mcpbridge.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.hiweny.mcpbridge.McpViewModel
import com.hiweny.mcpbridge.ui.screens.HomeScreen
import com.hiweny.mcpbridge.ui.screens.SettingsScreen
import com.hiweny.mcpbridge.ui.screens.ToolsScreen
import com.hiweny.mcpbridge.ui.screens.WebViewScreen

/**
 * 导航路由定义。
 */
sealed class Route(val route: String) {
    object Home : Route("home")
    object Tools : Route("tools")
    object WebView : Route("webview")
    object Settings : Route("settings")
}

/**
 * 应用导航图。
 *
 * @param navController 导航控制器
 * @param viewModel 全局状态 ViewModel
 * @param onStartServer 启动服务回调
 * @param onStopServer 停止服务回调
 */
@Composable
fun McpNavHost(
    navController: NavHostController,
    viewModel: McpViewModel,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Route.Home.route
    ) {
        composable(Route.Home.route) {
            val isRunning by viewModel.isRunning.collectAsState()
            val port by viewModel.port.collectAsState()
            val ipAddress by viewModel.ipAddress.collectAsState()
            val toolCount by viewModel.toolCount.collectAsState()

            HomeScreen(
                isRunning = isRunning,
                port = port.toIntOrNull() ?: 2730,
                ipAddress = ipAddress,
                toolCount = toolCount,
                onStartClick = onStartServer,
                onStopClick = onStopServer,
                onNavigateTools = { navController.navigate(Route.Tools.route) },
                onNavigateSettings = { navController.navigate(Route.Settings.route) },
                onNavigateWebView = { navController.navigate(Route.WebView.route) }
            )
        }

        composable(Route.Tools.route) {
            val tools by viewModel.tools.collectAsState()
            ToolsScreen(
                tools = tools,
                onTestTool = viewModel::testTool
            )
        }

        composable(Route.WebView.route) {
            val port by viewModel.port.collectAsState()
            WebViewScreen(serverPort = port.toIntOrNull() ?: 8024)
        }

        composable(Route.Settings.route) {
            val port by viewModel.port.collectAsState()
            val autoStart by viewModel.autoStart.collectAsState()
            val keepScreenOn by viewModel.keepScreenOn.collectAsState()

            SettingsScreen(
                port = port,
                onPortChange = viewModel::setPort,
                autoStart = autoStart,
                onAutoStartChange = viewModel::setAutoStart,
                keepScreenOn = keepScreenOn,
                onKeepScreenOnChange = viewModel::setKeepScreenOn
            )
        }
    }
}
