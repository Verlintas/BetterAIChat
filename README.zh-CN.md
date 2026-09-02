# BetterAIChat

**原生 Android AI 智能体**：自带 API Key、让 AI 操作你的手机。

[English](README.md) · 简体中文

> 由 Verlintas 独立开发。一款完全自主的 Android AI 助手：接入你自己的 API Key（OpenAI 兼容 / Anthropic / Gemini），AI 可以搜索网页、操作应用、自动化任务——所有数据留在你手机上。

## 截图

| 会话列表 | 聊天 | 设置 |
|---|---|---|
| <img src="docs/screenshots/screen-conversations.png" width="220" alt="会话列表"> | <img src="docs/screenshots/screen-chat.png" width="220" alt="聊天"> | <img src="docs/screenshots/screen-settings.png" width="220" alt="设置"> |

## 功能

### 聊天与模型
- 接入你自己的 API Key（加密存储于 Android Keystore），支持任意 OpenAI 兼容端点（含 DeepSeek / Kimi / Qwen / GLM 等国产模型）、Anthropic Claude、Google Gemini
- 内置精选模型目录（GPT-5.x / Claude / Gemini / DeepSeek V4 / Kimi K3 / Qwen 3.8 等），也可自定义模型
- 流式打字机输出、Markdown 渲染（自定义表格组件）、代码高亮、思考过程展示
- Token 用量实时显示

### Agent——一键配置，按会话选用
**Agent** 是一套完整配置：服务商 + API Key + 模型 + 温度/Max Tokens/深度推理 + 可选自定义系统提示。
- **4 步引导新建**：粘贴 API Key 自动识别服务商（sk-ant- → Claude、AIza → Gemini），内置 DeepSeek / 通义千问 / Kimi / 智谱 GLM / SiliconFlow 快捷预设 → 一键拉取模型列表 → 选模型 → 调参数 → 默认或自定义系统提示
- 每个对话可独立选择 Agent（⋮ → 选择 Agent）；设置 → Agent 统一管理（新建/编辑/删除/设默认）
- API Key 系统级加密；旧版配置首启自动迁移为默认 Agent

### 模式（opencode 风格）
| 模式 | 行为 |
|---|---|
| `Chat` | 纯对话，不调用工具 |
| `Plan` | 只读分析，不执行操作 |
| `Build` | 每个工具执行前请求你确认 |
| `Max` | 自主多步工具调用 + 深度思考 |

### AI 可以操作你的设备（55 个工具）
| 工具 | 功能 |
|---|---|
| `open_app` | 打开任意已安装应用 |
| `send_notification` | 发送通知 |
| `set_brightness` / `set_volume` / `set_screen_timeout` | 调节系统设置 |
| `set_flashlight` | 手电筒 |
| `take_screenshot` | 截屏 |
| `device_info` | 设备/电池/存储信息 |
| `set_clipboard` / `get_clipboard` | 剪贴板读写 |
| `set_alarm` | 一次性提醒 |
| `schedule_repeat` | 每日/每周/每小时重复提醒 |
| `speak_text` | TTS 朗读 |
| `web_search` / `web_read` | 多引擎实时搜索（Bing/百度/Brave/DDG/Mojeek 合并）与网页读取 |
| `open_settings` | 跳转系统设置 |
| `run_shell` | **通过 Shizuku 执行 shell 命令**（root 级能力） |
| `get_time` / `network_status` / `get_foreground_app` | 时间 / 网络 / 前台应用 |
| `media_control` / `set_ringer_mode` / `vibrate` | 媒体控制 / 铃声模式 / 震动 |
| `share_text` / `open_dialer` / `download_file` / `write_document` | 分享 / 拨号 / 下载 / 文档保存 |
| `fetch_rss` / `get_weather` / `get_exchange_rate` / `ping_network` | RSS / 天气 / 汇率 / 网络延迟 |
| `calculator` / `generate_qr` / `keep_screen_on` | 计算 / 二维码 / 屏幕常亮 |
| `get_location` / `transcribe_audio` / `ocr_file` / `send_email` | 定位 / 录音转写 / 图片 OCR / 邮件 |
| `set_dnd` / `manage_app` / `set_wifi` / `set_power_saver` | 勿扰 / 应用管理 / WiFi / 省电 |
| `screen_record` / `list_installed_apps` | 录屏 / 应用列表 |
| `screen_ocr` | **读取屏幕文字**（截屏 + 端侧中英文 OCR） |
| `create_automation` / `list_automations` / `delete_automation` | 自动化管理 |
| `ua_type` / `ua_tap` / `ua_swipe` / `ua_press` | **无障碍 UI 自动化**：输入 / 点击 / 滑动 / 按键 |
| `load_skill` | 加载并执行已导入的 SKILL.md 技能 |
| `read_notifications` / `get_screen_state` | 通知读取 / 屏幕状态 |

### 屏幕分析——AI 能"看见"屏幕
一键截屏 → 视觉模型描述屏幕内容并给出操作建议。支持任意视觉模型。

### 长期记忆
- AI 自动提炼用户重要信息（姓名/偏好/约定），持久化保存并注入每次对话
- 每 10 条消息自动提炼；对话菜单可手动触发
- 设置页「记忆」可查看/删除

### 无缝上下文衔接
- 用量达到 85% 自动压缩；压缩前保存最近对话快照
- 「导入最近对话」一键恢复上下文

### 自动化——设置后自动运行
- **定时触发**：如"每天 22:00 静音并开启勿扰"
- **电量触发**：如"电量低于 20% 时提醒充电"
- 后台无人值守执行工具序列，完成通知回报
- 设置 → 自动化 管理（列表/开关/删除）

### 完整 UI 自动化——AI 能"驾驶"你的手机
1. `screen_ocr` 读取屏幕文字
2. `ua_tap` / `ua_swipe` / `ua_type` / `ua_press` 执行操作
3. `take_screenshot` 验证结果

无需 root、无需 Shizuku。

### Skills（opencode 风格）
- 导入 `SKILL.md`（YAML frontmatter）
- 技能可自带工具（alarm/notification/clipboard/intent/settings 动作）
- 对话中多步操作可录制保存为技能

### 附件与文档
- 图片（最多 4 张，自动压缩，发送给视觉模型）
- **PDF**（端侧中文 OCR）、Word、Excel（全部工作表）、文本文件（≤1MB）

### 免提
- **语音助手模式**：AI 朗读回复、自动开麦、语音回答自动发送——完整免提闭环
- 语音输入、任意消息朗读、自动朗读开关

### 组织管理
- 搜索、置顶、归档、收藏消息、清除上下文、导出对话、上下文压缩（AI 总结）

### 主题
8 套强调色（橙/红/粉/靛蓝/蓝/紫/绿/青），默认橙色呼应图标，即时切换

### 国际化
支持中 / 英文界面切换（设置 → 对话 → 语言）

## 开始使用

### 下载
从 [Releases](https://github.com/Verlintas/BetterAIChat/releases) 下载：
- **full**：完整版（含屏幕 OCR）
- **lite**：精简版（约 10MB，无 OCR）

### 配置（Agent 引导）
1. 设置 → Agent → **新建 Agent**（或在聊天页 ⋮ → 选择 Agent → 新建）
2. 第 1 步：粘贴 API Key——自动识别服务商并填充 Base URL（sk-ant- → Claude、AIza → Gemini），也可点 DeepSeek / 通义千问 / Kimi / 智谱 GLM / SiliconFlow 预设，再点「自动识别」拉取服务端模型列表
3. 第 2 步：选模型 · 第 3 步：温度 / Max Tokens / 深度推理 · 第 4 步：默认或自定义系统提示 → 保存
4. 开始对话。Build/Max 模式下让 AI 做事：「打开计算器」「每天 9 点提醒我喝水」「搜索今天的新闻并总结」
5. 想换角色？多建几个 Agent，每个对话在 ⋮ → 选择 Agent 切换

### 从源码构建
需要 JDK 17 + Android SDK 36：
```
./gradlew assembleFullDebug
```

## 权限说明
| 能力 | 权限 | 用途 |
|---|---|---|
| 屏幕分析 / OCR | MediaProjection | 截屏、屏幕文字识别 |
| UI 自动化 | 无障碍服务 | 输入/点击/滑动/按键 |
| Shell 命令 | Shizuku | root 级操作 |
| 通知读取 | 通知使用权 | read_notifications |
| 前台应用 | 使用情况访问 | get_foreground_app |
| 定位 / 录音 / 相机 / 震动 | 运行时权限 | 相应工具 |

## 技术栈
Kotlin · Jetpack Compose (Material 3) · OkHttp (SSE) · kotlinx.serialization · Room · Android Keystore · ML Kit (中文 OCR)

```
:app         UI（会话、聊天、设置、统计、收藏）
:core        模型、引擎、SSE、存储、数据库、技能解析
:providers   OpenAI 兼容 / Anthropic / Gemini 适配器
:skills      设备工具 + 工具注册表 + 动作执行器
```

### 学习这个项目
想学习 Android 智能体开发？阅读 **[docs/zh/HOW_IT_WORKS.zh-CN.md](docs/zh/HOW_IT_WORKS.zh-CN.md)**——深度技术原理文档（模块架构、消息管线、SSE 流式、工具调用 Agent 循环、权限桥接、自动化引擎、UI 渲染技巧、推荐学习顺序）。

## 路线图
- 多模型对比（同一问题，多模型并排回答）
- 桌面小组件
- Agent 市场 / 可分享的 Agent

## 开源协议
[MIT](LICENSE)

---
*项目：[BetterAIChat](https://github.com/Verlintas/BetterAIChat) · 原生 Android AI 智能体，55 个内置工具。*
