package com.hiweny.mcpbridge.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.hiweny.mcpbridge.McpViewModel
import com.hiweny.mcpbridge.ui.screens.HomeScreen
import com.hiweny.mcpbridge.ui.screens.LogsScreen
import com.hiweny.mcpbridge.ui.screens.SettingsScreen
import com.hiweny.mcpbridge.ui.screens.ToolsScreen

/**
 * 导航路由定义。
 */
sealed class Route(val route: String) {
    object Home : Route("home")
    object Tools : Route("tools")
    object Logs : Route("logs")
    object Settings : Route("settings")
}

/**
 * 应用导航图。
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
                port = port.toIntOrNull() ?: 8024,
                ipAddress = ipAddress,
                toolCount = toolCount,
                onStartClick = onStartServer,
                onStopClick = onStopServer,
                onNavigateTools = { navController.navigate(Route.Tools.route) },
                onNavigateSettings = { navController.navigate(Route.Settings.route) },
                onNavigateLogs = { navController.navigate(Route.Logs.route) }
            )
        }

        composable(Route.Tools.route) {
            val tools by viewModel.tools.collectAsState()
            ToolsScreen(
                tools = tools,
                onTestTool = viewModel::testTool
            )
        }

        composable(Route.Logs.route) {
            val logs by viewModel.logs.collectAsState()
            LogsScreen(logs = logs)
        }

        composable(Route.Settings.route) {
            val port by viewModel.port.collectAsState()
            val autoStart by viewModel.autoStart.collectAsState()
            val keepScreenOn by viewModel.keepScreenOn.collectAsState()
            val externalServers by viewModel.externalServers.collectAsState()

            SettingsScreen(
                port = port,
                onPortChange = viewModel::setPort,
                autoStart = autoStart,
                onAutoStartChange = viewModel::setAutoStart,
                keepScreenOn = keepScreenOn,
                onKeepScreenOnChange = viewModel::setKeepScreenOn,
                externalServers = externalServers,
                onAddExternalServer = viewModel::addExternalServer,
                onRemoveExternalServer = viewModel::removeExternalServer
            )
        }
    }
}
