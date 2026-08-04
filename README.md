# Android MCP Bridge

> DeepSeek 网页版 MCP 工具调用桥接方案 — 油猴脚本（手机适配版）+ Android MCP 服务器 APK

## 项目简介

本项目包含两个部分：

1. **油猴脚本（手机适配版）** — 基于 [calendar0917/DeepseekWeb-enhance](https://github.com/calendar0917/DeepseekWeb-enhance) 的 ds-mcp-bridge 脚本，针对手机端显示进行了适配优化
2. **Android MCP 服务器 APK** — 一个轻量级 Android 应用，在手机上运行 MCP 服务器，为油猴脚本提供工具调用能力

## 下载

- **APK 下载**: 前往 [Releases 页面](https://github.com/Hiweny/android-mcp-bridge/releases) 下载最新构建的 APK
- **油猴脚本**: [ds-mcp-bridge-mobile.user.js](./ds-mcp-bridge-mobile.user.js)（右键 → Raw → 篡改猴自动安装）

## 油猴脚本 — 手机适配版

### 原始脚本

- **原项目**: [calendar0917/DeepseekWeb-enhance](https://github.com/calendar0917/DeepseekWeb-enhance)
- **原脚本**: [ds-mcp-bridge.user.js](https://github.com/calendar0917/DeepseekWeb-enhance/blob/main/ds-mcp-bridge.user.js)
- **原始版本**: v4.2.0
- **本仓库版本**: v4.2.1-mobile

### 手机适配改动

| 改动项 | 说明 |
|---|---|
| 面板宽度 | 固定 460px → 响应式 `min(460px, calc(100vw - 16px))`，手机上不溢出 |
| FAB 按钮位置 | 底部右侧 → 右上角覆盖分享按钮，不遮挡任何功能按钮 |
| FAB 按钮颜色 | 红色/绿色 → 深蓝色 `#1a365d`，低调不突兀 |
| 面板弹出方向 | 手机上面板从 FAB 下方向下展开，接近全屏宽度 |
| 触摸友好 | 按钮/输入框/开关增大触摸区域，符合移动端交互规范 |
| 移动端检测 | 自动识别 UA + 视口宽度，桌面端保持原行为不变 |

### 安装方法

1. 手机安装 [Edge 浏览器](https://play.google.com/store/apps/details?id=com.microsoft.emmx) + [篡改猴 (Tampermonkey)](https://www.tampermonkey.net/) 扩展
2. 访问脚本 raw 链接安装：[ds-mcp-bridge-mobile.user.js](./ds-mcp-bridge-mobile.user.js)
3. 打开 [chat.deepseek.com](https://chat.deepseek.com/)，右上角出现深蓝色齿轮按钮即安装成功

## Android MCP 服务器 APK

### 功能特性

- **MCP JSON-RPC 2.0 服务器** — 基于 NanoHTTPD，支持 `initialize`、`tools/list`、`tools/call` 等标准 MCP 方法
- **10 个内置工具** — 覆盖时间、设备信息、剪贴板、HTTP 请求、通知、震动、文件操作等
- **DeepSeek WebView** — 应用内集成 DeepSeek 对话页面，可直接与 AI 交互
- **深色 Material 3 界面** — 现代化深蓝主题，支持动画过渡
- **前台服务保活** — 确保服务持续运行，不被系统杀掉
- **工具测试面板** — 内置工具测试功能，可直接在应用内测试任意工具

### 内置工具清单

| 工具名 | 说明 |
|---|---|
| `get_time` | 获取当前时间，支持自定义格式和时区 |
| `get_device_info` | 获取设备型号、系统版本、屏幕分辨率、内存等信息 |
| `clipboard_read` | 读取剪贴板内容 |
| `clipboard_write` | 写入文本到剪贴板 |
| `http_request` | 发送 HTTP GET/POST 请求，支持自定义 Headers |
| `show_notification` | 显示系统通知 |
| `get_battery_info` | 获取电池电量、充电状态、温度等信息 |
| `read_file` | 读取文本文件内容（限 1MB） |
| `list_directory` | 列出目录内容 |
| `vibrate` | 震动设备，支持自定义时长和模式 |

### 架构概览

```
DeepSeek 网页 (Edge 浏览器 + 油猴脚本)
    ↓ GM_xmlhttpRequest (绕过 CORS)
    ↓ JSON-RPC 2.0 over HTTP
Android MCP Server APK (localhost:2730)
    ↓ 工具执行
    ↓ 结果返回
    ↓ 脚本自动注入对话
DeepSeek 继续回复
```

### 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material Design 3
- **HTTP 服务器**: NanoHTTPD 2.3.1
- **协议**: JSON-RPC 2.0 / MCP
- **导航**: Navigation Compose
- **构建**: GitHub Actions (Gradle 8.5 + AGP 8.2.2)
- **最低系统**: Android 8.0 (API 26)

### 使用方法

1. 从 [Releases](https://github.com/Hiweny/android-mcp-bridge/releases) 下载 APK 并安装
2. 打开应用，点击「启动服务」
3. 确认连接地址（默认 `http://192.168.x.x:2730`）
4. 打开 DeepSeek 网页版，点击右上角深蓝色齿轮按钮
5. 在「状态」页确认连接成功，查看可用工具列表
6. 直接用自然语言对话，DeepSeek 会自动调用工具

### 工作原理

油猴脚本通过拦截 DeepSeek 的 SSE 流式回复，检测 AI 输出的 `` ```mcp:工具名`` 格式指令，自动调用本地 MCP 服务器执行工具，将结果以 `<tool_result>` 形式自动发回对话。DeepSeek 看到的只是用户连续发送的消息，全程不知道工具的存在。

### 多轮工具调用机制

1. 用户发送消息 → DeepSeek 回复中包含工具调用指令
2. 脚本检测到指令 → 自动调用本地 MCP 服务器
3. 工具执行完成 → 脚本将结果包装为 `<tool_result>` 自动填入输入框并发送
4. DeepSeek 收到结果后继续回复 → 若仍需调用工具则循环执行
5. 直到 AI 不再调用工具，给出最终回答

## 项目结构

```
android-mcp-bridge/
├── .github/workflows/build-apk.yml   # GitHub Actions 构建工作流
├── app/
│   ├── build.gradle.kts              # 应用模块构建配置
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/hiweny/mcpbridge/
│       │   ├── MainActivity.kt       # 主界面 + ViewModel
│       │   ├── McpApplication.kt     # Application 入口
│       │   ├── mcp/                  # MCP 协议核心
│       │   │   ├── McpTool.kt        # 工具接口
│       │   │   ├── ToolRegistry.kt   # 工具注册表
│       │   │   └── JsonRpc.kt        # JSON-RPC 2.0 协议
│       │   ├── service/              # 前台服务
│       │   │   ├── McpHttpServer.kt  # HTTP 服务器
│       │   │   └── McpForegroundService.kt
│       │   ├── tools/                # 内置工具
│       │   │   ├── TimeTool.kt
│       │   │   ├── DeviceInfoTool.kt
│       │   │   ├── ClipboardTool.kt
│       │   │   ├── HttpTool.kt
│       │   │   ├── NotificationTool.kt
│       │   │   ├── BatteryTool.kt
│       │   │   ├── FileTool.kt
│       │   │   ├── VibrationTool.kt
│       │   │   └── DefaultTools.kt   # 工具注册工厂
│       │   └── ui/                   # Compose UI
│       │       ├── Navigation.kt
│       │       ├── theme/            # 主题 (Color/Theme/Type)
│       │       ├── components/       # 组件 (StatusCard/ToolCard)
│       │       └── screens/          # 页面 (Home/Tools/WebView/Settings)
│       └── res/                      # 资源文件
├── ds-mcp-bridge-mobile.user.js      # 油猴脚本
├── build.gradle.kts                  # 根构建配置
└── settings.gradle.kts
```

## 封号风险评估

本方案**封号风险极低**，与反代理方案有本质区别：

| 维度 | 本方案（油猴脚本） | 反代理方案 |
|---|---|---|
| 运行位置 | 真实浏览器内 | 服务器端冒充客户端 |
| 账号 | 单账号正常登录 | 账号池轮转 |
| 请求特征 | 正常浏览器请求 | 高并发程序化请求 |
| 工具执行 | 本地 localhost，DeepSeek 不可见 | 与工具无关 |
| 封号风险 | **极低** | 高 |

## 致谢

- [calendar0917/DeepseekWeb-enhance](https://github.com/calendar0917/DeepseekWeb-enhance) — 原始油猴脚本和 MCP 服务器
- [WongJingGitt/mcp_bridge](https://github.com/WongJingGitt/mcp_bridge) — SSE 解析和工具注入思路参考

## License

GPL-3.0
