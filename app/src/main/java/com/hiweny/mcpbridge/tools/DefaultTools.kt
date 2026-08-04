package com.hiweny.mcpbridge.tools

import android.content.Context
import com.hiweny.mcpbridge.mcp.ToolRegistry

/**
 * 注册全部内置 MCP 工具。
 * 供 ViewModel（UI 测试）与前台服务（对外提供工具）共用，避免两处工具列表不同步。
 */
object DefaultTools {

    fun registerAll(registry: ToolRegistry, context: Context) {
        // ── 原有工具 ──
        registry.register(TimeTool(context))
        registry.register(BatteryTool(context))
        registry.register(DeviceInfoTool(context))
        registry.register(VibrationTool(context))
        registry.register(ClipboardReadTool(context))
        registry.register(ClipboardWriteTool(context))
        registry.register(FileReadTool(context))
        registry.register(FileListTool(context))
        registry.register(NotificationTool(context))
        registry.register(HttpTool())

        // ── 文件系统扩展 ──
        registry.register(FileWriteTool(context))
        registry.register(FileDeleteTool(context))
        registry.register(FileCopyTool(context))
        registry.register(FileSearchTool(context))
        registry.register(FileInfoTool(context))

        // ── 网络信息 ──
        registry.register(NetworkInfoTool(context))
        registry.register(WifiInfoTool(context))
        registry.register(PingTool(context))
        registry.register(StorageInfoTool(context))

        // ── 系统控制 ──
        registry.register(VolumeTool(context))
        registry.register(SetVolumeTool(context))
        registry.register(BrightnessTool(context))
        registry.register(FlashlightTool(context))
        registry.register(RunningProcessesTool(context))

        // ── 应用管理 ──
        registry.register(ListAppsTool(context))
        registry.register(ListRunningAppsTool(context))
        registry.register(OpenAppTool(context))
        registry.register(GetAppInfoTool(context))
        registry.register(KillAppTool(context))

        // ── 传感器 ──
        registry.register(ListSensorsTool(context))
        registry.register(ReadSensorTool(context))

        // ── 媒体 ──
        registry.register(ScreenshotTool(context))
        registry.register(ListMediaFilesTool(context))

        // ── 位置 ──
        registry.register(GetLocationTool(context))

        // ── 剪贴板历史 ──
        registry.register(ClipboardHistoryTool(context))

        // ── 系统信息扩展 ──
        registry.register(ConnectivityInfoTool(context))
        registry.register(ScreenInfoTool(context))
        registry.register(MemoryInfoTool(context))
        registry.register(PackageInfoTool(context))
    }
}
