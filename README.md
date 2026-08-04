# Android MCP Bridge

> DeepSeek 网页版 MCP 工具调用桥接脚本（手机端适配版）+ Android MCP 服务器 APK 方案

## 项目简介

本项目包含两个部分：

1. **油猴脚本（手机适配版）** — 基于 [calendar0917/DeepseekWeb-enhance](https://github.com/calendar0917/DeepseekWeb-enhance) 的 ds-mcp-bridge 脚本，针对手机端显示进行了适配优化
2. **Android MCP 服务器 APK** — 一个轻量级 Android 应用，在手机上运行 MCP 服务器，为油猴脚本提供工具调用能力（开发中）

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
| FAB 按钮位置 | 底部右侧（挡住发送键）→ 手机上移至右上角（分享箭头下方），不遮挡任何功能按钮 |
| 面板弹出方向 | 手机上面板从 FAB 下方向下展开，接近全屏宽度 |
| 触摸友好 | 按钮/输入框/开关增大触摸区域，符合移动端交互规范 |
| 移动端检测 | 自动识别 UA + 视口宽度，桌面端保持原行为不变 |

### 安装方法

#### 方式一：从本仓库安装（推荐）

1. 手机安装 [Edge 浏览器](https://play.google.com/store/apps/details?id=com.microsoft.emmx) + [篡改猴 (Tampermonkey)](https://www.tampermonkey.net/) 扩展
2. 访问脚本 raw 链接安装：[ds-mcp-bridge-mobile.user.js](./ds-mcp-bridge-mobile.user.js)
3. 打开 [chat.deepseek.com](https://chat.deepseek.com/)，右上角出现绿色齿轮按钮即安装成功

#### 方式二：手动粘贴

1. 复制本仓库中 `ds-mcp-bridge-mobile.user.js` 的全部内容
2. 在篡改猴中新建脚本，粘贴并保存

### 使用方法

1. 启动 Android MCP 服务器 APK（或任意 MCP 服务器，监听 `localhost:8024/mcp`）
2. 打开 DeepSeek 网页版，点击右上角绿色齿轮按钮
3. 在「状态」页确认连接成功，查看可用工具列表
4. 直接用自然语言对话，DeepSeek 会自动调用工具

## Android MCP 服务器 APK（开发中）

### 架构概览

```
DeepSeek 网页 (Edge 浏览器 + 油猴脚本)
    ↓ GM_xmlhttpRequest (绕过 CORS)
    ↓ JSON-RPC 2.0 over HTTP
Android MCP Server APK (localhost:8024)
    ↓ 工具执行
    ↓ 结果返回
    ↓ 脚本自动注入对话
DeepSeek 继续回复
```

### 计划工具清单

| 类别 | 工具 | 说明 |
|---|---|---|
| 系统信息 | get_time, get_battery, get_network, get_location, get_device_info | 时间、电量、网络状态、GPS定位、设备信息 |
| 手机操作 | open_app, open_url, set_clipboard, get_clipboard, send_notification, vibrate | 打开应用/链接、读写剪贴板、通知、震动 |
| 文件 | read_file, write_file, list_directory | App 私有目录 + 下载目录 |
| 网络 | http_request, crawl_webpage, web_search | HTTP 请求、网页抓取、搜索 |
| 媒体 | take_photo, record_audio, play_tts, screenshot | 拍照、录音、语音朗读、截屏 |
| 扩展 | remote_mcp_proxy | 代理远程 MCP 服务器，聚合外部工具 |

### 技术选型

- **MCP 服务器**: NanoHTTPD (轻量 HTTP 服务器) + JSON-RPC 2.0
- **工具实现**: Kotlin + Android 系统 API
- **UI**: Jetpack Compose (状态面板 + 工具列表 + 配置页)
- **构建**: GitHub Actions (Gradle + assembleRelease + 签名)
- **保活**: 前台服务 (Foreground Service) + 常驻通知

### 工作原理

油猴脚本通过拦截 DeepSeek 的 SSE 流式回复，检测 AI 输出的 `` ```mcp:工具名`` 格式指令，自动调用本地 MCP 服务器执行工具，将结果以 `<tool_result>` 形式自动发回对话。DeepSeek 看到的只是用户连续发送的消息，全程不知道工具的存在。

### 多轮工具调用机制

1. 用户发送消息 → DeepSeek 回复中包含工具调用指令
2. 脚本检测到指令 → 自动调用本地 MCP 服务器
3. 工具执行完成 → 脚本将结果包装为 `<tool_result>` 自动填入输入框并发送
4. DeepSeek 收到结果后继续回复 → 若仍需调用工具则循环执行
5. 直到 AI 不再调用工具，给出最终回答

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
