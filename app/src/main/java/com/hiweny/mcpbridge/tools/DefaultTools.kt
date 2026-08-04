package com.hiweny.mcpbridge.tools

import android.content.Context
import com.hiweny.mcpbridge.mcp.ToolRegistry

/**
 * 注册全部内置 MCP 工具。供 ViewModel（UI 测试）与前台服务（对外提供工具）共用，
 * 避免两处工具列表不同步。
 */
object DefaultTools {

    fun registerAll(registry: ToolRegistry, context: Context) {
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
    }
}
