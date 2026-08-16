# BetterAIChat

一个 Android 应用：填入你自己的 AI API Key，即可像 opencode 一样与主流大模型对话，并通过 **Function Calling + 设备工具（Skills）** 让 AI 在手机上执行简单操作。

## 功能

- **多 Provider**：OpenAI 兼容（OpenAI / DeepSeek / Moonshot / 通义千问 / Ollama 等）、Anthropic Claude、Google Gemini
- **流式对话**：SSE 流式输出、Markdown 渲染、停止生成
- **模型选择**：内置模型目录 + 手动输入任意模型 ID + 每会话独立切换
- **模式系统（对齐 opencode）**：
  - `Chat` 纯对话，不调用工具
  - `Plan` 只读分析，禁止执行类工具
  - `Build` 默认模式，工具执行前需用户确认
  - `Max` 自主连续调用工具 + 深度推理（按模型能力注入 `reasoning_effort` / `thinking`）
- **设备工具**：打开应用、发送通知、调整亮度/音量、截屏（MediaProjection）、查询设备信息
- **本地安全**：API Key 用 Android Keystore（AES-GCM）加密存储，会话数据存 Room，无任何云端同步
- 多会话管理：新建/切换/删除/重命名

## 构建

```bash
# 需要 JDK 17 + Android SDK（compileSdk 36）
./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 使用

1. 打开应用 → 设置
2. 选择服务商，填入 API Key（和自定义 Base URL，支持本地 Ollama）
3. 选择模型与默认模式，保存
4. 新对话，选择模式（Build/Max 可让 AI 操作设备；Max 模式自动执行工具，注意授权）

## 权限说明

| 权限 | 用途 |
| --- | --- |
| 通知 | AI 发送通知提醒 |
| 修改系统设置 | AI 调整屏幕亮度 |
| 截屏（MediaProjection） | AI 截取屏幕并保存图片 |
| 后台服务 | 截屏执行 |

所有权限均在设置页手动授予，AI 工具在未授权时只会返回错误提示。

## 技术栈

Kotlin · Jetpack Compose · Material 3 · OkHttp（SSE）· kotlinx.serialization · Room · Android Keystore

模块：`:app`（UI）· `:core`（模型/引擎/存储）· `:providers`（适配器）· `:skills`（设备工具）
