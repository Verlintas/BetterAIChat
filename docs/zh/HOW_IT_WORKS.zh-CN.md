# BetterAIChat 技术原理——完整开发者手册

本文档详尽解释 **BetterAIChat** 的内部实现：模块架构、请求管线的每一层、流式协议、Agent 循环、工具系统、权限桥接、自动化引擎、存储、UI 渲染、安全设计，以及来自真实 bug 的工程经验。它既是学习指南，也是**交接手册**——新人可以照着第 20 章把项目跑起来，照第 24 章做第一个改动。

> 范围：约 63 个内置设备工具、opencode 风格技能、Agent（按会话的一键配置）、Shizuku + 无障碍 + MediaProjection 集成，以及后台自动化引擎。

---

## 目录

1. [项目总览与文件地图](#1-项目总览与文件地图)
2. [模块架构与依赖倒置](#2-模块架构与依赖倒置)
3. [领域模型](#3-领域模型)
4. [服务商适配器与模型目录](#4-服务商适配器与模型目录)
5. [一条消息的完整旅程](#5-一条消息的完整旅程)
6. [SSE 流式深入](#6-sse-流式深入)
7. [Agent 循环（ChatEngine）](#7-agent-循环chatengine)
8. [模式与安全闸门](#8-模式与安全闸门)
9. [确认流程](#9-确认流程)
10. [工具系统](#10-工具系统)
11. [权限桥接——AI 如何「触碰」你的手机](#11-权限桥接ai-如何触碰你的手机)
12. [技能（opencode 风格）](#12-技能opencode-风格)
13. [自动化引擎](#13-自动化引擎)
14. [存储与状态管理](#14-存储与状态管理)
15. [UI 层](#15-ui-层)
16. [安全设计](#16-安全设计)
17. [错误处理矩阵](#17-错误处理矩阵)
18. [真实 bug 的工程经验](#18-真实-bug-的工程经验)
19. [推荐学习顺序](#19-推荐学习顺序)
20. [上手——构建、运行、测试、调试](#20-上手构建运行测试调试)
21. [Agent——按会话的完整配置](#21-agent按会话的完整配置)
22. [联网搜索管线](#22-联网搜索管线)
23. [长期记忆](#23-长期记忆)
24. [扩展指南——如何添加功能](#24-扩展指南如何添加功能)
25. [排障与已知平台限制](#25-排障与已知平台限制)
26. [术语表与交接清单](#26-术语表与交接清单)
27. [设计哲学与开发方法](#27-设计哲学与开发方法)

> **新维护者阅读指南**：先浏览第 1–3 章，然后跳到 **第 20 章（上手）**把应用跑起来，**第 24 章（扩展）**做第一个改动，**第 26 章（交接清单）**了解仓库之外的一切。第 4–19 章是深入内容——触及相应领域时再读。

---

## 1. 项目总览与文件地图

```
BetterAIChat/
├── settings.gradle.kts                     # include(":app", ":core", ":providers", ":skills")
├── gradle/libs.versions.toml               # version catalog（依赖的单一事实来源）
│
├── app/                                    # Android 应用（UI + 系统集成）
│   ├── src/main/java/com/betteraichat/
│   │   ├── BetterAIChatApp.kt              # Application 类 + AppContainer（DI 装配）
│   │   ├── MainActivity.kt                 # 单 Activity 的 Compose 入口
│   │   ├── ui/
│   │   │   ├── navigation/AppNav.kt        # 导航图
│   │   │   ├── conversations/             # 会话列表页
│   │   │   ├── chat/
│   │   │   │   ├── ChatScreen.kt           # 聊天 UI、输入栏、对话框、滚动逻辑
│   │   │   │   ├── ChatViewModel.kt        # 状态机 + 发送/停止/压缩/编辑
│   │   │   │   └── MessageViews.kt         # 气泡/工具卡片/代码卡片 composable
│   │   │   ├── settings/SettingsScreen.kt  # 全部设置分区
│   │   │   └── agents/                        # Agent 引导向导 + 选择弹窗
│   │   └── tools/
│   │       ├── ScreenshotManager.kt        # MediaProjection 截屏服务 + 桥接
│   │       ├── BacAccessibilityService.kt  # AccessibilityService（ua_* 手势）
│   │       ├── BacNotificationListener.kt  # NotificationListenerService + 缓存
│   │       ├── AutomationScheduler.kt      # AlarmManager/电池相关的自动化
│   │       ├── SpeechInputHelper.kt        # 语音输入（SpeechRecognizer）
│   │       ├── SpeechPlayer.kt             # TTS
│   │       ├── AttachmentProcessor.kt      # 图片/文档/PDF 预处理
│   │       ├── ScreenOcr.kt (full 变体)    # ML Kit 中文 OCR
│   │       └── ShizukuManager.kt           # Shizuku 权限状态
│   └── src/full/java/  src/lite/java/      # 风味（flavor）专属源码（OCR 仅在 full 中）
│
├── core/                                  # 纯逻辑（无 Android UI）
│   ├── src/main/java/com/betteraichat/core/
│   │   ├── engine/ChatEngine.kt            # Agent 循环
│   │   ├── chat/ChatRepository.kt          # 数据库访问层
│   │   ├── db/AppDatabase.kt               # Room 实体 + DAO + 迁移（v10）
│   │   ├── model/ChatModels.kt             # ChatMessage、ToolCall、ProviderConfig…
│   │   ├── mode/AppMode.kt                 # Chat/Plan/Build/Max
│   │   ├── catalog/ModelCatalog.kt         # 各 provider 的内置模型注册表
│   │   ├── provider/ChatProvider.kt        # provider 接口
│   │   ├── sse/SseParser.kt                # SSE 行解析器
│   │   ├── skills/SkillRepository.kt       # SKILL.md 解析（YAML frontmatter）
│   │   └── storage/                        # SettingsRepository、KeyStoreCrypto
│   └── ...
│
├── providers/                             # 各厂商 API 适配器
│   └── src/main/java/com/betteraichat/providers/
│       ├── ProviderFactory.kt              # providerId → ChatProvider
│       ├── openai/OpenAiProvider.kt        # OpenAI 兼容接口（含 deepseek/qwen/kimi…）
│       ├── anthropic/AnthropicProvider.kt
│       └── gemini/GeminiProvider.kt
│
└── skills/                                # 设备工具
    └── src/main/java/com/betteraichat/skills/
        ├── ToolModels.kt                   # DeviceTool 接口、ToolContext、桥接
        ├── ToolRegistry.kt                 # 内置 + 技能定义的工具
        ├── DeviceToolRunner.kt             # name+args → DeviceTool.execute
        ├── SkillActionExecutor.kt          # 执行技能定义的动作类型
        └── tools/                          # 63 个工具实现
```

存在两个构建风味（flavor）：**full**（全功能，约 55 MB）和 **lite**（约 10 MB，不含端侧 OCR）。风味专属代码位于 `app/src/full/` 和 `app/src/lite/`。

---

## 2. 模块架构与依赖倒置

### 2.1 依赖图

```
:app ────────▶ :core
   │           :providers
   └─────────▶ :skills ────▶ :core
```

- `:app` 依赖所有模块。
- `:skills` 只依赖 `:core`。
- `:providers` 只依赖 `:core`。
- `:core` 不依赖任何内部模块（只依赖 Android SDK 和第三方库）。

### 2.2 为什么这种分层很重要

`:skills` 模块包含*必须*与 Android framework 服务交互的代码（截屏、无障碍手势、通知）。最直接的做法是在工具里直接导入 `android.app.Activity` 或应用的 `ScreenshotManager`。但这会在 `:skills` → `:app` 之间产生硬依赖，导致模块无法拆分、无法测试。

取而代之的是，`:skills` 为任何应用特有的功能定义**接口**，由 `:app` 来实现它们：

```kotlin
// :skills — ToolModels.kt
fun interface ScreenshotProvider { suspend fun capture(): String }
fun interface OcrProvider { suspend fun ocrScreenshot(): String }
interface AccessibilityBridge {
    fun connected(): Boolean
    fun windowTitle(): String?
    suspend fun typeText(text: String): String
    suspend fun pressKey(key: String): String
    suspend fun tap(x: Int, y: Int): String
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): String
}

data class ToolContext(
    val appContext: Context,
    val screenshotProvider: ScreenshotProvider,
    val ocrProvider: OcrProvider? = null,
    val accessibility: AccessibilityBridge? = null
)
```

`:app` 构造真实实现并把它们注入 `ToolContext`（参见 `BetterAIChatApp.kt`）。`ScreenshotManager` 实现了 `ScreenshotProvider`；`BacAccessibilityService` 实现了 `AccessibilityBridge`；`ScreenOcr` 实现了 `OcrProvider`。

这就是**依赖倒置**（dependency inversion）：抽象属于消费方（工具），实现属于生产方（应用）。带来的好处：

- `:skills` 可以通过伪造 `ToolContext` 进行单元测试。
- 不会产生循环 Gradle 依赖（Gradle 遇到环会直接构建失败）。
- lite 构建可以提供一个返回错误信息的 `OcrProvider` 桩实现，而 `:skills` 零改动。

### 2.3 AppContainer —— 手写的依赖注入

`BetterAIChatApp.kt` 包含 `AppContainer`，一个构造完整对象图的普通类：

```kotlin
class AppContainer(context: Application) {
    val db = AppDatabase.get(context)
    val settings = SettingsRepository(context)
    val repository = ChatRepository(db)
    val skillRepository = SkillRepository(context.applicationContext)

    private val screenshotManager = ScreenshotManager(context.applicationContext)
    private val ocrBridge = ScreenOcr(screenshotManager)              // full 变体
    private val accessibilityBridge = object : AccessibilityBridge { … }

    private val toolContext = ToolContext(context.applicationContext, screenshotManager, ocrBridge, accessibilityBridge)

    val automationScheduler = AutomationScheduler(context.applicationContext, db) { runner }
    private val automationBridge = object : AutomationBridge { … }

    val tools: List<DeviceTool> = listOf( /* 63 个工具，见 BetterAIChatApp.kt */ )
    val registry = ToolRegistry(tools)
    val runner = DeviceToolRunner(registry, toolContext)
    val engine = ChatEngine(providerFactory, registry, runner)
}
```

注意调度器那里**基于 lambda 的延迟依赖**：`AutomationScheduler(context, db) { runner }` —— 调度器需要 `runner`，而 `runner` 需要 registry，registry 又需要包含 `CreateAutomationTool(automationBridge)` 的工具列表……这就形成了循环构造。lambda 把 `runner` 的查找延迟到调度器真正执行自动化时才进行，从而打破了环。这是处理带环对象图时值得记住的巧妙技巧。

其他组件可以从任何地方解析它：

```kotlin
val container = (applicationContext as BetterAIChatApp).container
```

---

## 3. 领域模型

所有核心类型都位于 `:core/model/ChatModels.kt`。理解这些类型就能读懂整个代码库。

### 3.1 Provider 与角色

```kotlin
enum class ProviderId(val displayName: String) {
    OPENAI_COMPAT("OpenAI 兼容"), ANTHROPIC("Anthropic Claude"), GEMINI("Google Gemini")
}

enum class ChatRole(val wire: String) {
    SYSTEM("system"), USER("user"), ASSISTANT("assistant"), TOOL("tool")
}
```

`ChatRole` 携带 API 请求中使用的 `wire` 字符串——这让领域层保持干净，同时由 providers 负责转换为线上格式。

### 3.2 ToolCall 及其状态机

```kotlin
@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
    val result: String? = null,
    val status: ToolCallStatus = ToolCallStatus.PENDING
)

enum class ToolCallStatus { PENDING, RUNNING, DONE, FAILED, REJECTED, DENIED }
```

一个 `ToolCall` 会经历以下状态：

```
PENDING ──▶ RUNNING ──▶ DONE
   │           │
   │           └──────▶ FAILED        （执行抛出异常）
   ├──────▶ REJECTED                  （用户拒绝或请求被丢弃）
   └──────▶ DENIED                    （模式门禁拒绝：例如 Chat 模式）
```

UI 在 `MessageViews.kt` 中把每个状态渲染为彩色徽标（`StatusBadge`）：

| 状态 | 徽标 | 颜色 |
|---|---|---|
| PENDING | 等待 | gray |
| RUNNING | 执行中… | primary |
| DONE | 已完成 | green |
| FAILED | 失败 | red |
| REJECTED | 已拒绝 | red |
| DENIED | 已禁止 | red |

### 3.3 ChatMessage —— 线上/领域消息

```kotlin
data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,        // 用于 TOOL 消息：这条消息回答的是哪个调用
    val toolName: String? = null,
    val model: String? = null,             // 展示元数据
    val mode: AppMode? = null,             // 展示元数据
    val attachments: List<Attachment> = emptyList(),
    val thinkingText: String? = null,
    val thinkingSignature: String? = null
)
```

两个重要细节：

- `toolCallId` 把 `TOOL` 角色的消息关联回助手的 `tool_calls` 条目——这是 OpenAI/Anthropic 校验请求合法性的必需项（参见 §7.4）。
- `thinkingText`/`thinkingSignature` 携带推理模型（o1/deepseek-reasoner/Claude Opus）的推理内容，并在厂商要求时随 `reasoning_signature` 一并回传。

### 3.4 ProviderConfig

```kotlin
data class ProviderConfig(
    val provider: ProviderId,
    val baseUrl: String,
    val apiKey: String,          // 加密存储 —— 参见 KeyStoreCrypto
    val model: String,
    val temperature: Double,
    val maxTokens: Int,
    val reasoning: Boolean,     // 推理模型的“深度思考”模式
    val systemPrompt: String = ""  // Agent 自定义提示词，在 ChatEngine 中拼接到模式提示之前
)
```

API 密钥使用**Android Keystore 加密**（`:core/storage/KeyStoreCrypto.kt`）——密钥材料永不离开硬件级 keystore；应用只存储密文。

### 3.5 Attachment

```kotlin
@Serializable
data class Attachment(
    val kind: String,           // "image" | "doc"
    val name: String,
    val mimeType: String,
    val dataBase64: String = "",      // 图片以 base64 内联发送
    val textContent: String? = null   // PDF/docx/xlsx 提取出的文本
)
```

图片会被降采样/压缩后内联发送（视觉模型接受 base64 data URL）。文档（PDF、Word、Excel）在**设备端**解析——包括针对 PDF 的中文 OCR——并作为提取出的文本发送。

---

## 4. Provider 适配器与模型目录

### 4.1 ChatProvider 接口

```kotlin
// :core/provider/ChatProvider.kt
interface ChatProvider {
    fun chatStream(
        messages: List<ChatMessage>,
        config: ProviderConfig,
        tools: List<ToolSpec>
    ): Flow<StreamEvent>
}
```

每个厂商（OpenAI 兼容、Anthropic、Gemini）都实现这一个方法：输入历史消息 + 配置 + 工具规格，返回一个冷的 `Flow<StreamEvent>`。`ProviderFactory` 负责把 `ProviderId` 映射到具体实现。

`StreamEvent`：

```kotlin
sealed interface StreamEvent {
    data class Delta(val text: String) : StreamEvent
    data class ThinkingDelta(val text: String) : StreamEvent
    data class ThinkingSignature(val signature: String) : StreamEvent
    data class ToolCallsDone(val calls: List<ToolCall>) : StreamEvent
    data class Usage(val promptTokens: Long, val completionTokens: Long) : StreamEvent
    data class Error(val message: String) : StreamEvent
    data object Done : StreamEvent
}
```

这个抽象刻意保持厂商中立：OpenAI 发出 `delta.tool_calls`，Anthropic 发出带 `input_json_delta` 的 `delta.content[]`，Gemini 以自己的格式发出 `functionCall`——但三者最终都归结为 `Delta` + `ToolCallsDone`。

### 4.2 ModelCatalog —— 精选的模型元数据

```kotlin
data class ModelEntry(
    val id: String, val label: String,
    val temperature: Double = 0.7, val maxTokens: Int = 4096,
    val supportsReasoning: Boolean = false,
    val contextWindow: Int = 200_000
)
```

该目录（三个 provider 共约 50 个模型）为 UI 提供合理的默认值（temperature、max tokens、用于用量计量的上下文窗口、推理支持），而无需把这些硬编码在页面里。`contextWindow` 还驱动聊天顶部栏中的 token 用量指示器（`usageInput / contextWindow %`）。

### 4.3 线上格式转换

OpenAI 兼容请求体：

```kotlin
val body = OpenAiRequest(
    model = config.model,
    messages = messages.mapNotNull { it.toWire() },
    temperature = if (reasoning) null else config.temperature,   // 推理模型禁止 temperature
    maxTokens = config.maxTokens,
    reasoningEffort = if (reasoning) "high" else null,
    streamOptions = OpenAiStreamOptions(),
    tools = tools.map { OpenAiTool(OpenAiToolFunction(it.name, it.description, it.parameters)) }
        .takeIf { it.isNotEmpty() }
)
```

微妙之处：

- **推理模型会拒绝 `temperature`**——推理开启时它被置为 `null`。
- **工具 schema** 就是 `DeviceTool.parameters` 的 JSON Schema，原样透传——LLM 据此生成参数。
- `messages.mapNotNull { it.toWire() }` 让 `toWire()` 能丢弃无法表示的消息（例如内容为空的 assistant 消息）。

---

## 5. 单条消息的旅程

### 5.1 完整时序图

```
 用户                    ChatScreen            ChatViewModel             ChatEngine              Provider             Room
  │  tap ⏎ (发送)            │                        │                       │                     │                   │
  │─────────────────────────▶│  onSend()              │                       │                     │                   │
  │                          │───────────────────────▶│  send()               │                     │                   │
  │                          │                        │  ├─ isRunning? → return
  │                          │                        │  ├─ state.isRunning = true
  │                          │                        │  └─ launch { sendWithContent() }
  │                          │                        │        ├─ insertMessage(USER) ────────────────────────────────▶ INSERT
  │                          │                        │        ├─ sendTick++（UI 滚动到底部）
  │                          │                        │        └─ runGeneration(cid)
  │                          │                        │              │  runJob?.isActive → return   （互斥锁）
  │                          │                        │              │  state.isRunning = true
  │                          │                        │              │  streaming = placeholder(id=-1)
  │                          │                        │              ├─▶ engine.run(history, config, mode)
  │                          │                        │              │        └─ provider.chatStream(...) ──▶ HTTP POST
  │                          │                        │              │            │
  │                          │ ◀── Delta ─────────────┼──── Delta ───┼────────────┼──── SSE 解析
  │                          │  （100ms ticker 刷新） │              │            │
  │ ◀── recompose ───────────│◀── state.messages ──────┘              │            │
  │                          │                                        │            │
  │                          │                          └── AssistantFinished ───▶ INSERT (assistant)
  │                          │                                        │
  │                          │                              tool_calls? ── 是 ──▶ 对每个调用：
  │                          │                                        │        gate(mode) → 确认对话框
  │  ◀── AlertDialog ────────│◀── confirmRequest ────────────────────┼──── tryEmit(call)
  │  tap 允许 ──────────────▶│  respondConfirm(true) ─────────────────┼──── deferred.complete(true)
  │                          │                                        │        ToolRunner.run()  ──▶ DeviceTool.execute()
  │                          │                                        │        insertMessage(TOOL 结果)
  │                          │                                        │        再次循环（MAX 最多 50 轮，其余模式 12 轮）
  │                          │                                        └── Completed
  │ ◀── 最终 UI ─────────────│◀── refresh()
```

### 5.2 ViewModel 状态机

`ChatUiState` 是一个不可变数据类，持有于 `MutableStateFlow` 中：

```kotlin
data class ChatUiState(
    val conversationId: Long, val title: String,
    val provider: ProviderId, val model: String, val mode: AppMode,
    val messages: List<UiMessage>,        // dbMessages + streaming
    val input: String,
    val isRunning: Boolean,
    val sendTick: Int,                    // 自增 = “请滚动到底部”
    val confirmRequest: ConfirmRequest?,  // 待确认的工具调用
    val notification: String?,            // 临时 snackbar 消息
    val error: String?,                   // 常驻错误横幅
    val pendingAttachments: List<PendingAttachment>,
    val processing: Boolean,
    val agentId: Long?, val agentName: String,  // 会话绑定的 Agent
    val attachmentError: String?,
    // …（用量数据从最后一条带 usage 的消息推导，不单独存储）
)
```

关键纪律：**所有变更都通过 `_state.update { it.copy(...) }` 完成**——不做直接字段写入，不做部分更新。这带来：

- 线程安全（StateFlow 的更新是原子的）。
- UI 的单一事实来源。
- 易于调试（记录状态迁移日志）。

### 5.3 合并数据库消息与实时流

```kotlin
private var dbMessages: List<UiMessage> = emptyList()
private var streaming: UiMessage? = null

private fun refresh() {
    _state.update { it.copy(messages = dbMessages + listOfNotNull(streaming)) }
}
```

- `dbMessages` 由 Room Flow 保持最新：`repository.observeMessages(id).collect { list -> dbMessages = mapToUi(list); refresh() }` —— **任何数据库变更（包括 AI 自身的插入）都会自动重新渲染聊天界面。**
- `streaming` 是正在流式输出的 assistant 消息，`id = -1`（哨兵值，表示“尚不在数据库中”）。
- 流式期间，一个 100 ms 的 ticker 任务（`streamTickerJob`）会调用 `refresh()`，使增量内容得以显示，尽管 `state.messages` 只在 ticker 运行时才发生变化——这就是事件速率（每秒大量 delta）与 UI 刷新速率（10 Hz）之间的*解耦*。

### 5.4 流式累积器

每个 `Delta` 都会追加到流式消息上。这里有一个微妙且真实踩过的性能陷阱：在每次 delta 时向不可变的 `String` 追加内容复杂度是 **O(n²)**——一条 4 万字符的回复以 2000 个 delta 送达，会在主线程上累计复制约 4000 万字符。修复模式（引擎中使用）是 `StringBuilder` 累积器，按定时器或完成时刷新。ViewModel 保留了简单的追加方式，但依靠 100 ms ticker 来限制重组频率。

---

## 6. SSE 流式深入

### 6.1 实际应用中的 SSE 协议

基于 HTTP 的 Server-Sent Events 看起来像这样：

```
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache

data: {"choices":[{"delta":{"role":"assistant"}}]}

data: {"choices":[{"delta":{"content":"你"}}]}

data: {"choices":[{"delta":{"content":"好"}}]}

data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

data: [DONE]
```

解析器遵循的规则：

1. 以 `data:` 开头的行累积负载。
2. **空行**终止一个事件 → 分发已累积的数据。
3. `[DONE]` 是哨兵事件 → 停止消费。

### 6.2 解析器逐行拆解

```kotlin
object SseParser {
    fun parse(body: ResponseBody, onEvent: suspend (event: String, data: String) -> Boolean): Flow<Unit> =
        flow<Unit> {
            val source = body.source()
            val dataBuffer = StringBuilder()
            var eventName = ""
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                when {
                    line.isBlank() -> {
                        if (dataBuffer.isNotEmpty()) {
                            val shouldContinue = onEvent(eventName, dataBuffer.toString())
                            dataBuffer.clear()
                            eventName = ""
                            if (!shouldContinue) break          // ← 通过返回值取消
                        }
                    }
                    line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> {
                        val payload = line.removePrefix("data:").trimStart()
                        if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                        dataBuffer.append(payload)
                    }
                }
            }
            if (dataBuffer.isNotEmpty()) onEvent(eventName, dataBuffer.toString())
        }.flowOn(Dispatchers.IO)
}
```

值得学习的点：

- **天然无背压**：`flowOn(Dispatchers.IO)` 把阻塞式读取移出主线程；collector 在它所在的地方收集。
- **不用异常实现取消**：回调返回 `false` 而不是抛异常，循环随之跳出——干净利落，无需处理 `CancellationException`。
- 某些服务器会使用 `event:` 行（例如 Anthropic 使用 `event: content_block_delta`）；解析器保留事件名，消费方可以据此分支处理。

### 6.3 分块解码与工具调用累积

在 `OpenAiProvider.chatStream()` 中：

```kotlin
val toolAcc = mutableMapOf<Int, ToolAcc>()          // index → 累积中的工具调用
SseParser.parse(body) { _, data ->
    if (data == "[DONE]") {
        emit(StreamEvent.ToolCallsDone(toolAcc.toToolCalls()))   // 冲刷未完成的调用
        emit(StreamEvent.Done)
        return@parse false
    }
    val chunk = runCatching { json.decodeFromString(OpenAiChunk.serializer(), data) }
        .getOrNull() ?: return@parse true            // 忽略畸形 chunk，继续
    chunk.usage?.let { /* 发出 Usage */ }
    val choice = chunk.choices.firstOrNull() ?: return@parse true
    choice.delta?.content?.let { emit(StreamEvent.Delta(it)) }
    choice.delta?.reasoning?.let { emit(StreamEvent.ThinkingDelta(it)) }
    choice.delta?.toolCalls?.forEach { tc ->
        // tc.index + tc.function.name + tc.function.arguments（可能跨多个 chunk 拆分）
        toolAcc.mergeFragment(tc)
    }
    true
}
```

工具调用的参数**会跨 chunk 拆分到达**（经常是 JSON 片段，如先是 `{"app":`，然后是 `"计算器"}`），因此 provider 必须按 `index` 累积，直到 `finish_reason: "tool_calls"` 或 `[DONE]`。最后一次性发出 `ToolCallsDone`，其中参数已完全拼接。

### 6.4 Anthropic 特有细节

Anthropic 的流式是事件导向的：

```
event: message_start
event: content_block_start   (content_block {type:"tool_use", id, name})
event: content_block_delta   (input_json_delta: {partial_json:"{\"app\":"})
event: content_block_delta   (input_json_delta: {partial_json:"计算器\"}"})
event: content_block_stop
event: message_stop
```

适配器（`AnthropicProvider.kt`）监听 `content_block_delta` 并拼接 `partial_json` 片段；文本增量以 `text_delta` 到达。SSE 的 `event:` 字段正是 `SseParser` 保留事件名的原因。

### 6.5 重试与超时纪律

```kotlin
private suspend fun executeWithRetry(call: okhttp3.Call): okhttp3.Response {
    val retryableCodes = setOf(429, 502, 503, 504)
    repeat(2) { attempt ->
        try {
            val response = call.clone().execute()        // ← clone！一个 Call 只能执行一次
            if (response.isSuccessful || response.code !in retryableCodes) return response
            response.close()
            if (attempt == 0) delay(500)                 // 退避
        } catch (e: java.io.IOException) {
            if (attempt == 0) delay(500) else throw e
        }
    }
    return call.clone().execute()
}
```

为什么 `clone()` 很重要：`OkHttp.Call` 是一次性的。执行两次会抛出 `IllegalStateException("Already Executed")`。旧代码复用了同一个 `Call`，所以重试*从未生效*——这是 v0.20.1 修复的一个真实 bug。

超时：`connectTimeout(30s)` + `readTimeout(120s)`。读超时用于防御“服务器停止发送但保持连接打开”的情况——没有它，应用会永远卡在一个用户只能强制停止的状态。

---


## 7. Agent 循环（ChatEngine）

### 7.1 循环主体

```kotlin
fun run(messages, config, mode): Flow<EngineEvent> = flow {
    val provider = providerFactory(config.provider)     // 在 try 中保护
    var history = messages
    var toolRounds = 0
    val maxRounds = if (mode == AppMode.MAX) 50 else 12
    while (true) {
        if (toolRounds >= maxRounds) { emit(Failed("工具调用轮次已达上限")); return@flow }
        var text = StringBuilder(); var thinking = StringBuilder()
        var toolCalls = emptyList<ToolCall>()
        try {
            val tools = toolCatalog.specsFor(mode)
            val sys = ChatMessage(role = SYSTEM, content = systemPromptFor(mode))
            provider.chatStream(listOf(sys) + history, effectiveConfig, tools).collect { ev -> … }
        } catch (e: Exception) {
            if (e is CancellationException) throw e        // 用户停止操作向上传播
            emit(EngineEvent.Failed(e.message ?: e.javaClass.simpleName))
            return@flow
        }
        val assistantMsg = ChatMessage(role = ASSISTANT, content = text, toolCalls = toolCalls, …)
        emit(EngineEvent.AssistantFinished(assistantMsg))
        if (toolCalls.isEmpty()) break                    // ← 正常退出
        toolRounds++
        history = history + assistantMsg
        for (call in toolCalls) {
            val spec = toolCatalog.find(call.name)
            val decision = gate(mode, spec, call)
            val (status, resultText) = when (decision) {
                Denied  → DENIED to "工具被拒绝执行：reason"
                NeedsConfirm → … 确认对话框 … 然后执行
                Allow   → executeTool(call, spec)
            }
            emit(EngineEvent.ToolCallFinished(finished))
            history = history + ChatMessage(role = TOOL, content = resultText, toolCallId = call.id, …)
        }
    }
    emit(EngineEvent.Completed)
}.flowOn(Dispatchers.IO)
```

### 7.2 为什么用 `Flow` 而不是回调

引擎是一个**冷流（cold flow）**：在被 collect 之前什么都不会执行。这给 ViewModel 带来了：

- 结构化并发（`viewModelScope.launch { engine.run(...).collect { … } }` —— 取消 job 即取消该 flow）。
- 自然的事件流（增量 delta / 完成 finished / 失败 failed）。
- 可组合性（map、filter、timeout……）。

### 7.3 系统提示词 —— 按模式进行 prompt engineering

```kotlin
private fun systemPromptFor(mode: AppMode): String = when (mode) {
    AppMode.CHAT -> "你是一个友好的 AI 助手，请用简洁、准确的语言回答用户的问题。你不需要也不允许调用任何工具。"
    AppMode.PLAN -> "你现在处于 Plan（计划）模式。你只能进行分析、制定方案和查看只读信息，绝对禁止执行任何修改设备的操作。…"
    AppMode.BUILD -> "你现在处于 Build（构建）模式。你可以调用设备工具来帮助用户完成任务，但每个工具执行前系统会请求用户确认，因此请大胆、合理地提出工具调用，明确说明每一步的目的。"
    AppMode.MAX -> "你现在处于 Max（最大）模式。你可以自主、连续地调用设备工具来完成用户任务，无需每次询问用户确认。…"
}
```

提示词与安全门（§8）是**两个相互独立的层**：提示词告诉模型应该倾向于什么；安全门则不论模型怎么做都强制执行。这就是纵深防御（defense in depth）。

### 7.4 工具结果有效性 —— “中毒对话”缺陷

OpenAI 要求：assistant 消息中的每条 `tool_calls` 都必须有与之对应的、`tool_call_id` 相同的 `tool` 角色消息，否则 API 会返回 HTTP 400。Anthropic（`tool_result`）和 Gemini（`functionResponse`）也有同样的规则。

危险的场景：当引擎正在等待确认或执行工具时，用户按下了**停止**。引擎在发出 `ToolCallFinished` 之前就被取消了，但那条 assistant 消息（带有 PENDING 状态的工具调用）已经被持久化。该对话中的下一条消息将永远返回 400 —— 这就成了“中毒对话（poisoned conversation）”。

修复方案（v0.20.1），位于 `ChatViewModel.stop()`：

```kotlin
private suspend fun failPendingToolCalls() {
    val entity = pendingAssistantEntity ?: return
    val calls = decodeToolCalls(entity.toolCallsJson)
    val pending = calls.filter { it.status == PENDING }
    if (pending.isEmpty()) return
    // 1. 更新 assistant 实体：PENDING → REJECTED，result = "已取消"
    repository.updateMessage(entity.copy(toolCallsJson = encode(cancelled)))
    // 2. 为每个被取消的调用插入一条 TOOL 消息，使 API 看到完整配对
    pending.forEach { call ->
        repository.insertMessage(domainToMessage(
            ChatMessage(role = TOOL, content = "已取消", toolCallId = call.id, toolName = call.name),
            entity.conversationId
        ))
    }
    pendingAssistantEntity = null
}
```

这是一个绝佳的例子，展示了**正确性不变量（correctness invariant）**（对话历史必须是有效的工具调用转录）是如何通过失效模式分析发现、而不是通过测试发现的。

---

## 8. 模式与安全门

### 8.1 四种模式

| 模式 | 工具 | 确认 | 使用场景 |
|---|---|---|---|
| `Chat` | 无 | — | 纯对话 |
| `Plan` | 仅只读 | 无 | 分析、建议、规划 |
| `Build` | 全部 | 每个工具 | 用户监督的任务 |
| `Max` | 全部 | 无 | 自主的多步骤任务 |

### 8.2 安全门的实现

```kotlin
private fun gate(mode: AppMode, spec: ToolSpec?, call: ToolCall): GateResult {
    if (mode == AppMode.CHAT) return GateResult.Denied("Chat 模式不执行设备工具")
    val s = spec ?: return GateResult.Denied("未知工具：${call.name}")
    if (mode == AppMode.PLAN && !s.readOnly) return GateResult.Denied("Plan 模式仅允许只读工具")
    if (mode == AppMode.BUILD) return GateResult.NeedsConfirm
    return GateResult.Allow
}
```

每个工具都声明了 `readOnly`；安全门是唯一信任该声明的场所。注意 `spec == null` → 拒绝（DENIED）：即使模型幻觉出一个不存在的工具名，引擎也只会拒绝而不会崩溃。

### 8.3 按模式选择工具 —— `ToolRegistry.specsFor`

```kotlin
override fun specsFor(mode: AppMode): List<ToolSpec> = when (mode) {
    AppMode.CHAT -> emptyList()                                    // 完全不公布任何工具
    AppMode.PLAN -> builtinTools.filter { it.readOnly }.map { it.spec() }
    AppMode.BUILD, AppMode.MAX -> builtinTools.map { it.spec() } + dynamicTools.values.map { it.spec() }
}
```

`Chat` 模式在请求中**根本不会公布工具** —— 模型看不到就无法调用。这样既更省钱（请求更小），也更安全（模型永远不会尝试调用）。

---

## 9. 确认流程

### 9.1 引擎与 UI 的解耦

引擎运行在 `Dispatchers.IO` 上；对话框存在于 Compose 中。二者通过以下方式通信：

1. `MutableSharedFlow<ToolCall>`，带 `extraBufferCapacity = 8` —— 引擎 → UI 方向。
2. 每个调用一个 `CompletableDeferred<Boolean>` —— UI → 引擎方向。

```kotlin
// 引擎侧（NeedsConfirm 分支）
val deferred = CompletableDeferred<Boolean>()
pendingConfirms[call.id] = deferred
val allow = if (!confirmRequestsFlow.tryEmit(call)) {       // 没有订阅者 / 缓冲区已满？
    pendingConfirms.remove(call.id)
    false                                                   // 安全回退：拒绝
} else {
    try { withTimeout(300_000) { deferred.await() } }
    catch (e: CancellationException) { throw e }            // 用户停止了整个 job
    catch (e: Exception) { false }                          // 超时 → 拒绝
    finally { pendingConfirms.remove(call.id) }
}
```

```kotlin
// ViewModel
init {
    viewModelScope.launch {
        engine.confirmRequests.collect { call ->
            if (runJob?.isActive == true) {
                _state.update { it.copy(confirmRequest = ConfirmRequest(call, it.mode)) }
            }
        }
    }
}
fun respondConfirm(allow: Boolean) {
    state.confirmRequest?.let { req ->
        engine.respond(req.call.id, allow)                  // → deferred.complete(allow)
        _state.update { it.copy(confirmRequest = null) }
    }
}
```

对话框提供**允许（allow）/ 拒绝（reject）/ 停止整个任务（stop entire task）**三个选项。停止会取消 `runJob`，进而取消 flow，再通过 `CancellationException` 传播（即 `throw e` 路径）取消 `deferred.await()`；随后 `failPendingToolCalls()`（见 §7.4）保证历史记录仍然有效。

### 9.2 `tryEmit` 失败处理

如果 `MutableSharedFlow.tryEmit` 没有订阅者或缓冲区已满（例如同时有 8 个以上的确认请求），它会返回 `false`。旧代码会静默忽略该结果 —— 用户永远看不到对话框，调用会在 5 分钟后超时。修复方案是回退为立即拒绝并附带明确的消息，这样任何被丢弃的请求都不会卡死循环。

---

## 10. 工具系统

### 10.1 DeviceTool 契约

```kotlin
interface DeviceTool {
    val name: String
    val description: String       // ← 提供给 LLM 的提示词素材
    val readOnly: Boolean
    val parameters: JsonObject    // ← 用于参数生成的 JSON Schema
    suspend fun execute(context: ToolContext, arguments: JsonObject): String
}
```

由于 `description` 和 `parameters` 是模型了解该工具的唯一信息来源，把它们写好本身就是 prompt engineering：

- 描述**何时**使用该工具，以及**它返回什么**。
- 声明必选参数与可选参数。
- 返回人类可读的字符串 —— 它们会被插入对话历史，并在下一轮重新发送给模型。

### 10.2 一个代表性的工具

```kotlin
// GetTimeTool.kt
class GetTimeTool : DeviceTool {
    override val name = "get_time"
    override val description = "查询当前日期和时间：年/月/日/星期/时/分/秒，以及时区。只读工具。"
    override val readOnly = true
    override val parameters = schemaOf()

    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        // …returns "日期: 2026年8月24日 星期一\n时间: 14:05:33\n时区: Asia/Shanghai（UTC+8）\nUnix 时间戳: 1787544333"
    }
}
```

### 10.3 参数解析模式

```kotlin
val percent = arguments["percent"]?.jsonPrimitive?.content?.toIntOrNull()
    ?.coerceIn(0, 100) ?: return "percent 参数无效"
```

每个工具在执行前都会校验并净化参数 —— 非法参数返回错误字符串而不是抛出异常。由于错误会被反馈给模型，模型可以在下一轮**自我纠正**（这是一个真实出现的涌现行为：LLM 读到“percent 参数无效”后会用合法值重试）。

### 10.4 Shizuku 执行 —— 安全地运行 root 级命令

`ShizukuExec` 把 Shizuku binder 封装成一个小助手：

```kotlin
val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
val proc = service.newProcess(arrayOf("sh", "-c", command), null, null)
// 异步读取 stdout/stderr
val exited = proc.waitForTimeout(timeoutSeconds.toLong(), "SECONDS")
if (!exited) { proc.destroy(); close streams; return "命令超时" }
```

值得照抄的细节：

- **先设超时再读取**：`waitForTimeout` 约束整个命令；超时后关闭流，这样被阻塞的 `readText()` future 不会泄漏 IO 线程。
- **输出上限**：超过 8000 字符的结果会被截断并附带说明。
- **尽量使用白名单**：`manage_app` 在把包名插入 `pm …` 命令之前，会用 `Regex("[a-zA-Z0-9._]+")` 校验包名 —— 只有 `run_shell`（明确设计为执行任意命令）不受限制。

### 10.5 计算器 —— 用安全的求值器代替 eval

```kotlin
class Parser(private val input: String) {
    fun parse(): String { /* 递归下降：expression → term → factor → number */ }
}
```

`calculator` 实现了一个**递归下降解析器（recursive-descent parser）**（约 80 行），支持 `+ - * / % ^ ( )` 和小数 —— 刻意不使用 `eval()` 或 JavaScript 引擎，因此不可能执行任意代码。它也是“语法 → 代码”的一个很好的教学范例。

---

## 11. 权限桥接 —— AI 如何“触碰”你的手机

### 11.1 完整的能力矩阵

| 能力 | 桥接方式 | 用户授权 | 示例工具 |
|---|---|---|---|
| 截屏与屏幕分析 | `MediaProjection` + 前台服务 | 一次性授权（Android 15：选择 BetterAIChat 作为要捕获的应用） | `take_screenshot`, `screen_ocr` |
| UI 自动化 | `AccessibilityService` | 在系统设置中启用 | `ua_type`, `ua_tap`, `ua_swipe`, `ua_press`, `ua_tap_text`, `ua_find_text` |
| root 级 shell | Shizuku | 安装 Shizuku 并授权 | `run_shell`, `manage_app`, `set_wifi`, `set_power_saver` |
| 读取通知 | `NotificationListenerService` | 通知使用权 | `read_notifications` |
| 前台应用 | `UsageStatsManager` | 使用情况访问权限（appops） | `get_foreground_app` |
| 系统设置 | `WRITE_SETTINGS` | 修改系统设置权限 | `set_brightness`, `set_screen_timeout` |
| 发送通知 | `POST_NOTIFICATIONS` | 运行时权限 | `send_notification`, `schedule_repeat` |
| 勿扰模式 DND | `NotificationManager` | 策略访问权限（Android <15） | `set_dnd` |
| 定位 | `LocationManager` | 定位权限 | `get_location` |
| 振动 / 唤醒锁 | `Vibrator` / `PowerManager` | —（普通权限） | `vibrate`, `keep_screen_on` |
| 重启恢复 | `BOOT_COMPLETED` 接收器 | — | 重新注册重复任务与自动化 |
| 录音并转写 | `SpeechRecognizer` | 录音权限 | `transcribe_audio` |
| 录屏 | `MediaProjection` + `MediaRecorder` | 捕获授权 | `screen_record` |

设置界面会显示每项授权的实时状态，并可深链到系统对应页面去授予权限。

### 11.2 MediaProjection 生命周期（最棘手的一个）

**Android 14+ 改变了规则**：一个 MediaProjection token 只能被消费一次（`getMediaProjection`），且其 `createVirtualDisplay` 每个 token 也只能调用一次。因此「创建投影 → 截图 → 拆除」的朴素流程只工作一次；第二次截图就会以「授权已失效」失败——先分析屏幕、再截一张图的 App 会直接变瞎。

所以服务在**授权到达时一次性创建** `VirtualDisplay` + `ImageReader`，并在整个会话期间保持存活：

```kotlin
// 首次授权请求时：创建常驻管线（token 单次消费）
private fun ensurePersistentDisplay() {
    if (reader != null) return
    val (w, h, d) = screenMetrics()
    val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
    vDisplay = projection!!.createVirtualDisplay("BetterAIChatShot", w, h, d,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null)
    reader = r
}

// 每次截图只从常驻 reader 读取最新帧
private suspend fun captureFromPersistent(): String {
    maybeResize()                       // 旋转/分辨率变化：vd.resize + 更换 reader
    val image = reader!!.acquireLatestImage() ?: waitAndRetry()
    // planes -> bitmap（处理 rowStride padding）-> cacheDir/screenshots/*.png
}
```

关键机制：

- **前台服务**（`foregroundServiceType="mediaProjection"`）持有管线，使进程（与授权）在切后台与锁屏后继续存活。
- `acquireLatestImage()` 始终返回最新帧；屏幕静止时投影仍会持续产帧，无需「先触发重绘」之类的操作。
- 当系统收回投影时（用户停止投屏、其他应用请求投影），`registerCallback(onStop)` 会标记投影失效，使下次截图快速失败并给出清晰信息。
- `ScreenshotBridge` 是**同步化**的「请求 ID → `CompletableDeferred<String>`」注册表，并发截图不会串扰结果。
- 若系统彻底杀掉应用进程，Android 要求重新授权——所有投屏类应用皆如此；正常情况下前台服务会保持授权有效。

### 11.3 无障碍手势（线程很关键）

```kotlin
override suspend fun tap(x: Int, y: Int): String = withContext(Dispatchers.Main) {
    val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
    val gesture = GestureDescription.Builder()
        .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
        .build()
    if (dispatchGestureInternal(gesture)) "已点击 (${x}, ${y})" else "ERROR:手势分发失败"
}
```

- **手势必须在主线程分发** —— 引擎运行在 `Dispatchers.IO` 上，因此每个手势方法都用 `withContext(Dispatchers.Main)` 包裹自己。
- 用 5 秒的 `withTimeoutOrNull` 防止回调永不触发（服务重新绑定、系统卡顿）——否则工具调用会挂起整个 agent 循环。
- `onDestroy` 会清空静态 `instance`，防止过期引用被使用。

### 11.4 通知监听缓存

`BacNotificationListener` 在双端队列（deque）中保留最近 30 条通知。该缓存在监听线程（`onNotificationPosted`）写入，在工具执行线程读取，因此访问是 `synchronized` 的，且快照在锁内复制 —— 这是一个经典的共享可变状态修复方案。

---

## 12. Skills（opencode 风格）

### 12.1 SKILL.md 格式

```markdown
---
name: send-reminder
description: Set a reminder for the user
allowed-tools: set_alarm
---

1. Ask for the reminder content and time
2. Call set_alarm with the details
```

`SkillRepository` 解析 YAML frontmatter，并把正文存储为指令。

### 12.2 把技能作为工具加载

`LoadSkillTool` 本身就是一个工具：它列出已加载的技能（名称 + 描述），让模型知道何时该“调用”某个技能。`load_skill` 把技能名称解析为其工具描述，使模型能够用该技能声明的工具执行技能的步骤。

### 12.3 技能自定义工具与动作执行器

技能可以声明**自己的工具**，并带有动作类型。`SkillActionExecutor` 解释 `{param}` 模板并分发到 Android：

| 动作类型 | 实现方式 |
|---|---|
| `alarm` | `AlarmManager` 一次性闹钟 → 通知 |
| `notification` | 发送一条通知 |
| `clipboard` | 读写剪贴板 |
| `intent` | 通过 `Intent` 启动一个 activity |
| `settings` | 打开系统设置页面 |
| `repeat` | 重复闹钟（每日/每周/每小时），可通过通知操作取消 |

技能工具在 `ToolRegistry.registerSkillTools()` 中注册，删除时注销 —— 注册表把它们当作内建列表之外的动态新增项，在 `specsFor` 中为 Build/Max 模式公布。

### 12.4 从对话中录制技能

“保存为技能”功能把 assistant 消息的 `tool_calls` 序列化为一个 SKILL.md：

```kotlin
fun saveAsSkill() {
    val history = repository.getHistory(currentConversationId)
    val toolUses = history.flatMap { it.toolCallsJson?.let { decode(it) } ?: emptyList() }
    if (toolUses.isEmpty()) { notify("当前对话没有工具调用，无法生成技能"); return }
    // builds: name: skill_<ts>\n---\n步骤: call tool with captured arguments
    skillRepository.import("$skillName.md", md)
}
```

---

## 13. 自动化引擎

### 13.1 Schema（数据结构）

```kotlin
@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val triggerType: String,      // "time" | "battery"
    val triggerValue: String,     // "22:00"  |  "low:20" / "high:80"
    val days: String,             // "1,2,3,4,5,6,7" or "all"
    val actionsJson: String,      // [{"tool":"set_volume","args":{"percent":0}},…]
    val enabled: Boolean = true,
    val lastRunAt: Long = 0,
    val createdAt: Long
)
```

### 13.2 时间触发器 —— AlarmManager

```kotlin
private fun scheduleTime(automation: AutomationEntity) {
    val (hour, minute) = automation.triggerValue.split(":").map { it.toInt() }
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pending = alarmIntent(automation.id)
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }
    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
}
```

- `setExactAndAllowWhileIdle` 即使在 Doze（省电深度休眠）模式下也能触发 —— 只要声明了 `USE_EXACT_ALARM` 权限，无需用户额外授权即可工作。
- 接收器（`AutomationAlarmReceiver`，在 manifest 中静态注册）**在执行完成后会重新调度第二天的闹钟** —— 因此即使进程被杀死，自动化任务也不会因此彻底失效。
- 接收器在启动协程之前调用 `goAsync()`，确保进程不会在执行中途被系统回收。

### 13.3 电池触发器

`ACTION_BATTERY_CHANGED` 是一个 **sticky broadcast（粘性广播）** —— `registerReceiver` 会立即投递当前的电池电量，之后每次变化都会继续投递。调度器如下：

```kotlin
val batteryReceiver = object : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val snapshot = synchronized(batteryThresholds) { batteryThresholds.toMap() }
        snapshot.forEach { (id, config) ->
            val (direction, threshold) = config
            val hit = if (direction == "low") level <= threshold else level >= threshold
            if (hit && running.putIfAbsent(id, true) == null) {   // ← 每个自动化任务独立的互斥锁
                scope.launch { try { executeAutomation(id) } finally { running.remove(id) } }
            }
        }
    }
}
```

### 13.4 执行

```kotlin
suspend fun executeAutomation(id: Long) {
    runCatching {
        val automation = db.automationDao().getEnabled().firstOrNull { it.id == id } ?: return
        if (!daysMatch(automation.days)) return
        db.automationDao().setLastRun(id, System.currentTimeMillis())
        runCatching {
            val actions = json.decodeFromString<List<ActionSpec>>(automation.actionsJson)
            val results = actions.mapNotNull { spec ->
                val result = withTimeoutOrNull(60_000) {          // ← 每个动作独立的超时
                    runCatching { runnerProvider().run(spec.tool, spec.args.toString()) }
                        .getOrElse { e -> "执行失败：${e.message}" }
                } ?: "执行超时（60s）"
                "${spec.tool}: $result"
            }
            sendDoneNotification(automation.name, results.joinToString("\n"))
        }
        if (automation.triggerType == "time") {
            db.automationDao().getEnabled().firstOrNull { it.id == id }?.let { schedule(it) }
        }
    }
}
```

### 13.5 从聊天中创建自动化任务

`CreateAutomationTool` 会校验触发器格式（HH:mm 或 low:/high:），然后 `AutomationBridge.create` 将任务插入 Room 并完成调度。用户可以在 设置 → 自动化 中管理它们（列表、开关、删除）。完整的闭环流程已在模拟器上验证：AI 创建"睡前模式" → 闹钟在精确的分钟时刻触发 → `set_volume` 和 `set_dnd` 依次执行 → 发送完成通知。

---

## 14. 存储与状态管理

### 14.1 Room schema（v10）

```
conversations (id, title, provider, model, mode, agentId, pinned, archived, createdAt, updatedAt)
messages (id, conversationId FK, role, content, toolCallsJson, toolCallId, toolName,
          model, mode, status, usageInput, usageOutput, attachmentsJson,
          thinkingText, thinkingSignature, starred, createdAt)
repeat_tasks (id, title, content, interval, time, weekday, everyHours, requestCode, nextTriggerAt, createdAt)
automations  (id, name, triggerType, triggerValue, days, actionsJson, enabled, lastRunAt, createdAt)
memories     (id, conversationId, type [memory|snapshot], content, createdAt, updatedAt)
agents       (id, name, description, provider, baseUrl, apiKey[加密], model,
              temperature, maxTokens, reasoning, systemPrompt, isDefault, createdAt, updatedAt)
```

### 14.2 显式迁移 —— 绝不使用破坏性迁移

```kotlin
val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations ADD COLUMN agentId INTEGER")
        db.execSQL("CREATE TABLE IF NOT EXISTS agents (…)")
    }
}
// …添加到 .addMigrations(MIGRATION_1_2 … MIGRATION_9_10)
```

每个 schema 版本都配有一个手写的迁移脚本，让现有用户原地升级。`fallbackToDestructiveMigration()` 被刻意排除在外 —— 对于一个聊天应用来说，数据丢失是不可接受的。

### 14.3 基于 Flow 的观察

```kotlin
@Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id ASC")
fun observeForConversation(conversationId: Long): Flow<List<MessageEntity>>
```

只要表数据失效，Room 就会重新发射数据 —— 聊天列表、会话列表、统计、重复任务和自动化任务都使用这一模式，配合 `collectAsStateWithLifecycle(initialValue = …)`。这正是"AI 插入一条消息 → UI 自动更新"得以零手动刷新实现的原因。

### 14.4 Entity ↔ domain 映射

`ChatRepository.messageToDomain()` 和 `domainToMessage()` 负责在 Room 实体与 `ChatMessage` 之间转换。JSON 字段（`toolCallsJson`、`attachmentsJson`）使用 `runCatching{…}.getOrDefault(emptyList())` 解码 —— 损坏的字段会降级为空列表，而不是导致整个会话崩溃。

### 14.5 设置与加密

`SettingsRepository` 用带类型的 getter/setter 封装了 `SharedPreferences`。API key 以加密形式存储：

```kotlin
// KeyStoreCrypto: AndroidKeyStore 中的 AES/GCM key
val encrypted = encrypt(apiKey)      // 随机 IV + 密文
// 读取时解密；keystore 不匹配（例如备份恢复后）→ 清空 key，用户重新输入
```

---

## 15. UI 层

### 15.1 界面结构

```
AppNav
 ├── ConversationListScreen        (search, pinned, list via Room Flow)
 └── ChatScreen
      ├── TopAppBar (title, agent+usage subtitle, mode selector, more menu → Select Agent)
      ├── LazyColumn (messages)
      │     └── MessageItem
      │           ├── UserBubble      (right-aligned, primaryContainer)
      │           └── AiBlock
      │                 ├── meta row (model · mode · time)
      │                 ├── markdown bubble (+ blinking cursor while streaming)
      │                 ├── ToolCallCard per call (step badge, args, status, result)
      │                 ├── "执行步骤：已完成 n / m" progress while streaming tools
      │                 ├── HighlightedCodeCard per extracted code block
      │                 ├── link cards
      │                 └── ThinkingCard (collapsible reasoning)
      ├── ConfirmDialog (允许/拒绝/停止整个任务)
      ├── WelcomePanel (mode-aware example chips)
      └── InputBar (attach, mic, OutlinedTextField, send/stop)
```

### 15.2 消息渲染细节

- **用户与 AI 的对齐方式**：用户气泡右对齐（`Arrangement.End`），AI 块居左并带头像行。
- **工具卡片**：默认紧凑——单行显示工具名、彩色 `StatusBadge` 与「详细」按钮；展开后显示等宽参数与执行结果（八行省略 + 展开全部）。
- **代码块**：从 markdown 中提取出来单独渲染，使用深色背景并带复制按钮 —— 这避免了 markdown 渲染器在长代码/fenced 代码上出问题，同时带来接近原生的体验。
- **思考过程**：`ThinkingCard` 将冗长的推理文本折叠起来，提供展开/收起行。
- **Markdown 规范化**（`core/chat/MarkdownNormalizer.kt`）：模型输出的 markdown 常有不被 CommonMark 接受的形式，渲染前统一修复：全角竖线 `｜` → `|`；无外框表格（`a | b` 形式）由自定义表格组件解析；`#`/`-`/`1.`/`>` 后缺失的空格自动补齐；全角星号自动转换；中文词内加粗（`中文**加粗**中文`，CommonMark 会拒绝）通过插入发丝空格使其正常渲染。每项修复都有单元测试。

### 15.3 布局驱动的底部跟随滚动

此前两种方案都失败了：每次增量都 `animateScrollToItem` 会抖动（流式条目高度每帧变化）；「每 80ms 强制滚动」的循环会与 Markdown 的异步布局互相竞争，产生肉眼可见的抖动。当前实现（v0.27.3）是**布局驱动**的：每次布局完成后，若最后一项底部超出视口且跟随处于开启状态，则精确补滚一次。无循环、无延迟、不与布局系统竞争。

```kotlin
// wasAtBottom 只在真实用户拖拽时置 false。用 isScrollInProgress 是个陷阱：
// 程序自身滚动也会触发它，从而静默关闭跟随。
var wasAtBottom by remember { mutableStateOf(true) }
var forceFollow by remember { mutableStateOf(false) }
LaunchedEffect(listState) {
    listState.interactionSource.interactions.collect { interaction ->
        if (interaction is DragInteraction.Start) {
            wasAtBottom = false
            forceFollow = false
        }
    }
}
LaunchedEffect(listState) {
    snapshotFlow { listState.isScrollInProgress }
        .collect { scrolling ->
            if (!scrolling) {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull { it.index == info.totalItemsCount - 1 }
                val pinned = last != null && last.offset >= 0 &&
                    last.offset + last.size <= info.viewportEndOffset + 1
                if (pinned) wasAtBottom = true
            }
        }
}

// 跟随器：每次布局后触发；仅当最后一项确实溢出视口（内容增长）时滚一次，
// 然后保持安静，直到下一次增长。
LaunchedEffect(listState) {
    snapshotFlow { listState.layoutInfo }
        .collect { info ->
            val total = info.totalItemsCount
            if (total == 0) return@collect
            if (!forceFollow && !wasAtBottom) return@collect
            if (listState.isScrollInProgress) return@collect
            val last = info.visibleItemsInfo.lastOrNull { it.index == total - 1 }
            if (last == null || last.offset + last.size > info.viewportEndOffset + 2) {
                runCatching { listState.scrollToItem(total - 1, Int.MAX_VALUE) }
            }
        }
}
```

关键洞见：**不要轮询，要响应**。`snapshotFlow { layoutInfo }` 恰好在布局变化时触发（即流式文本增长时）；「最后一项是否溢出？」的检查把每次增长转化为至多一次滚动。程序滚动不会重新触发跟随路径，用户拖拽通过 `DragInteraction` 识别，而不是含糊的 `isScrollInProgress`。

### 15.4 流式光标

```kotlin
val blinkAlpha = rememberInfiniteTransition(label = "cursor").animateFloat(
    initialValue = 0.7f, targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse)
).value
// content + if (streaming) "▋" rendered with Modifier.alpha(blinkAlpha)
```

一个闪烁的 `▋` 附加在实时文本末尾 —— 这是成本最低的"正在输入"提示。

### 15.5 用量指示器

`totalPromptTokens`（取自最近一条带 usage 信息的消息）与 `ModelCatalog.entryFor(provider, model).contextWindow` 对比，在顶栏渲染为 `· 1.2K tokens（3%）` —— 让用户实时感知剩余上下文。

### 15.6 语音输入与免提循环

- `SpeechInputHelper`（SpeechRecognizer）→ 部分识别结果实时流入输入框。
- `SpeechPlayer`（TextToSpeech）朗读回复。
- **语音助手模式**：AI 回答完成后，朗读回复、自动打开麦克风，用户说出的回答会自动发送 —— 一个完整的免手操作循环，在 ViewModel 中以小型状态机的形式实现。

---

## 16. 安全设计

| 威胁 | 缓解措施 |
|---|---|
| 试图执行任意 shell 的 prompt injection | 只有 `run_shell` 能执行任意命令，且仅在 Shizuku 授权下可用；其余所有能力都被工具语义限制住 |
| 工具参数注入 | 每个工具都做校验：`manage_app` 有包名白名单正则，`calculator` 使用自己的解析器，路径/URL 按工具逐一校验 |
| 恶意 URL | `download_file` 上限 100 MB，并拒绝过大的 `Content-Length`；`web_read`/`fetch_rss` 通过 jsoup 的默认限制约束响应大小 |
| API key 窃取 | key 用 Android Keystore 加密；绝不写日志；只以密文存储 |
| 后台滥用 | 每一项特权能力都要求显式、可见的用户授权；自动化任务只执行用户让 AI 创建的序列 |
| 长内容 DoS | 流式输出受读超时 + 每动作超时 + 引擎最多 8 轮工具调用限制 |
| 并发执行竞态 | 引擎互斥锁（`runJob?.isActive`）、每自动化任务互斥锁、同步的 caches/bridges |

---

## 17. 错误处理矩阵

| 层 | 失败场景 | 处理结果 |
|---|---|---|
| 网络 | 401/403/404/429/超时/连接失败 | 映射为友好的中文提示（`smartError`） |
| 流式输出 | 格式损坏的 chunk | 跳过该 chunk，流继续 |
| 流式输出 | 服务器停滞 | 120 秒读超时 → `Failed` → UI 报错 + 已生成内容部分持久化 |
| 工具 | 参数错误 | 工具返回错误字符串 → 反馈给模型 → 自我纠正 |
| 工具 | 执行抛异常 | `ToolCallStatus.FAILED` 附带消息，显示为红色徽章 |
| 工具 | 挂起 | 引擎/自动化中 60 秒超时 |
| 确认 | 对话框被关闭 | 回退为 REJECTED（tryEmit 检查） |
| 确认 | 用户超时 | 300 秒超时 → REJECTED |
| 停止 | 工具轮次中途停止 | PENDING 调用 → REJECTED + 插入`已取消`工具消息（历史记录保持有效） |
| 数据库 | JSON 列损坏 | `runCatching` → 空默认值，会话继续 |
| 权限缺失 | 例如没有 MediaProjection | 工具返回 `ERROR:…` 并附授权指引 |

---

## 18. 真实 bug 带来的工程教训

这些都是代码审查中发现、并在 v0.20.1 中修复的 bug —— 每个都有一条可迁移的教训：

1. **从不重试的重试。** OkHttp 的 `Call` 是一次性的；重试同一个实例必定抛异常。修复：使用 `call.clone()`。*教训：要验证失败路径确实会执行 —— 失效的重试逻辑比没有重试更糟（它会掩盖错误）。*

2. **被"毒化"的会话。** 在工具轮次中途停止，会让 assistant 的 `tool_calls` 没有对应的 `tool` 响应 → 之后所有请求都返回 400。修复：停止时把 PENDING 调用标记为 REJECTED，并插入`已取消`的结果。*教训：即使在取消路径中，也要维持外部协议的不变量。*

3. **让功能失效的死代码。** 电池接收器写好了却从未注册 —— 电池自动化任务静默地什么都不做。*教训：添加"钩子"时要用 grep 检查调用点；一个无法运行的功能比一个报错更糟糕。*

4. **并发状态损坏。** `isRunning` 在多个入口（发送/编辑/屏幕分析）设置方式不一致，导致两条 agent 流水线在共享的可变状态上竞态。修复：在引擎入口处加一把统一的互斥锁。*教训：并发不变量要在单一位置强制，而不是在每个调用方里各自为政。*

5. **无限挂起。** `readTimeout(0)` + 没有工具超时，意味着停滞的服务器或卡死的手势可以永远阻塞下去。修复：120 秒读超时、60 秒工具超时、5 秒手势超时、60 秒自动化步骤超时。*教训：每个挂起点都需要超时 —— "它应该会响应"不是超时。*

6. **无视线程亲和性。** Accessibility 手势在非主线程上会静默失败。*教训：阅读 API 文档中的线程要求；在边界处用 `withContext(Main)` 包裹。*

7. **通过工具参数的 shell 注入。** 修复：白名单校验。*教训：任何最终进入 shell/URL/路径的字符串都是注入面 —— 在边界处校验。*

8. **资源泄漏不断累积。** Bitmap、流、识别器、连接。修复：`try/finally`、`use`、显式 `recycle()`。*教训：清理必须是结构性的（finally），不能是尽力而为。*

9. **`cancelAll()` 的误伤。** 取消一个重复任务会清掉所有通知。修复：按 id 取消。*教训：副作用要优先使用最窄的作用范围。*

10. **幽灵消息。** `persistStreamingPartial` 中一个死分支在停止时创建了空的 assistant 消息。修复：重写合并逻辑并测试停止路径。*教训：热路径上的死代码就是一颗等触发器触发的 bug。*

11. **执行后的闹钟重调度。** 如果执行挂起，下一次触发就永远不会被调度。修复：在执行之前/无论执行结果如何都先调度（并给每一步加超时）。*教训：可恢复性不能依赖 happy path。*

12. **O(n²) 字符串累积** 发生在长流式输出期间的主线程上。*教训：在循环中追加不可变字符串是平方级复杂度 —— 先累积，再一次发布。*

---

## 19. 推荐学习顺序

如果你想真正从这个代码库中学到东西：

1. **Kotlin 协程与 Flow** —— `ChatEngine.run(): Flow<EngineEvent>`、滚动逻辑中的 `snapshotFlow`、`withTimeout`、接收器中的 `goAsync()`。
2. **依赖倒置** —— `ToolContext` 桥接层（`ScreenshotProvider`、`OcrProvider`、`AccessibilityBridge`、`AutomationBridge`）以及打破循环依赖图的 lambda-lazy `runner` 注入。
3. **响应式 UI** —— 一个不可变的 `StateFlow`、`collectAsStateWithLifecycle`、`derivedStateOf`、用 10 Hz 的 ticker 将事件频率与渲染频率解耦。
4. **Room** —— 实体、DAO、Flow 观察、从 v1 到 v10 的显式迁移。
5. **网络** —— OkHttp + 手写 SSE 解析器、`call.clone()` 重试、读超时纪律、按厂商转换线上格式（OpenAI/Anthropic/Gemini）。
6. **Android 系统集成** —— `AlarmManager.setExactAndAllowWhileIdle`、sticky 电池广播、`MediaProjection` + 前台服务生命周期、`AccessibilityService` 手势分发（主线程 + 超时）、`NotificationListenerService`、Shizuku binder IPC。
7. **Agent 设计** —— 基于模式的闸门（Chat/Plan/Build/Max）、prompt + 强制的纵深防御、通过 `SharedFlow` + `CompletableDeferred` 实现的确认循环，以及工具转录不变量（§7.4）。
8. **安全性** —— 带实时状态 UI 的权限矩阵、边界处的输入校验、沙箱化的求值器、输出截断、并发互斥锁。
9. **UX 工程** —— 布局驱动的钉底滚动（响应布局而非轮询）、闪烁光标、紧凑工具卡片（详细展开）、会话内搜索、代码块提取、随模式变化的欢迎面板。

---

## 20. 上手——构建、运行、测试、调试

### 20.1 环境要求

| 工具 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17 | Gradle 工具链以 JVM 17 为目标 |
| Android SDK | platform 36 + build-tools 36.0.0 | `compileSdk = 36`，`minSdk = 26` |
| Gradle | wrapper（8.14.3） | 一律使用 `./gradlew` |
| 设备 | Android 8.0+（模拟器即可） | 测试 MediaProjection 建议 Android 14/15 |

构建不需要任何 API Key。真正聊天需要为某个 OpenAI 兼容端点（DeepSeek、OpenAI、本地网关等）准备 Key，并通过应用内的 Agent 引导配置。

### 20.2 构建变体

应用有两个产品风味（flavor）：

| 风味 | 内容 | 产物 |
| --- | --- | --- |
| `full` | 全部功能，含 ML Kit 端侧 OCR | `app/build/outputs/apk/full/debug/app-full-debug.apk` |
| `lite` | 无 OCR 模型（小约 10MB） | `app/build/outputs/apk/lite/debug/app-lite-debug.apk` |

```bash
./gradlew assembleFullDebug          # 日常开发构建
./gradlew assembleLiteDebug          # lite 变体
./gradlew assembleFullRelease        # 发布构建（未签名；签名是手动流程，见第 26 章）
```

OCR 是同一接口的风味专属实现：`app/src/full/java/.../ScreenOcr.kt` 与 `app/src/lite/java/.../ScreenOcr.kt`；`:skills` 里的 `OcrProvider` 就是接缝。

### 20.3 安装与运行

```bash
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
adb shell am start -n com.betteraichat/.MainActivity
```

首次使用流程：设置 → Agent → 新建 Agent → 粘贴 API Key → 引导会自动按前缀识别服务商（`sk-ant-` → Claude、`AIza` → Gemini，其余为 OpenAI 兼容），拉取模型列表并保存。手头没有 Key？把 Base URL 指向任意本地 mock（见下节）。

### 20.4 测试

```bash
./gradlew :core:testDebugUnitTest :skills:testDebugUnitTest :providers:testDebugUnitTest
```

当前覆盖聚焦于历史上真实出过问题的纯逻辑：SSE 解析、markdown 规范化（含中文加粗与无外框表格）、计算器（含科学函数）、工具参数合并/解析、单位换算、Agent Key 推断。UI 与 Android 框架代码在真机验证——代码库刻意把逻辑挡在 composable 之外，就是为了可测试。

### 20.5 CI

`.github/workflows/ci.yml` 在每次 push/PR 时运行：三个库模块的单测，然后 `assembleFullDebug` + `assembleLiteDebug`。CI 挂了说明构建或测试回归了——绝不要带着红 CI 合并。

### 20.6 用本地 mock 服务器调试

不消耗真实 API 配额、又能完整跑通管线（流式、工具调用、markdown 边界）的最快方式是主机上的小型 SSE 服务器。最小版本：

```python
#!/usr/bin/env python3
import json, sys
from http.server import BaseHTTPRequestHandler, HTTPServer

class H(BaseHTTPRequestHandler):
    def do_POST(self):
        req = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))) or '{}')
        self.send_response(200)
        self.send_header('Content-Type', 'text/event-stream')
        self.end_headers()
        def ev(d):
            self.wfile.write(b'data: ' + json.dumps(d).encode() + b'\n\n'); self.wfile.flush()
        # 按模型名切换脚本化行为
        if 'tool' in req.get('model', ''):
            ev({"choices": [{"delta": {"tool_calls": [
                {"index": 0, "id": "c1", "function": {"name": "get_time", "arguments": "{}"}}]}}]})
        else:
            ev({"choices": [{"delta": {"content": "hello **world**"}}]})
        ev({"choices": [{"delta": {}, "finish_reason": "stop"}]})
        self.wfile.write(b'data: [DONE]\n\n'); self.wfile.flush()

HTTPServer(('0.0.0.0', 9999), H).serve_forever()
```

然后创建 Base URL 为 `http://10.0.2.2:9999/v1`（模拟器 → 宿主回环）的 Agent，任意 API Key，模型名含 `tool` 即触发脚本化工具调用。切换模型名即切换场景——本应用的每个功能都是这样验证的。

### 20.7 模拟器测试技巧

- `adb shell input text` 无法输入中文；用英文测试串，或通过剪贴板粘贴。
- `uiautomator dump` 在滚动/动画期间偶尔返回**陈旧快照**。存疑时截图（`adb shell screencap -p /sdcard/x.png`）并 OCR——本仓库的作者不止一次被陈旧 dump 误导过。
- 权限请走 UI（设置 → 权限）授予；截屏授权尤其需要系统对话框（「开始录制」）。
- MediaProjection 状态存活于应用进程；`adb shell am force-stop` 会丢掉它——force-stop 后需重新授权。

---

## 21. Agent——按会话的完整配置

### 21.1 数据模型

**Agent** 是存储在 `agents` 表中的完整 AI 配置：

```
AgentEntity(id, name, description, provider, baseUrl, apiKey, model,
            temperature, maxTokens, reasoning, systemPrompt, isDefault,
            createdAt, updatedAt)
```

- `apiKey` **加密存储**（KeyStoreCrypto）。仓库写入 `"enc:" + 密文`、读取时解密（`AgentRepository.encryptIfPlain/decrypt`），数据库中永不出现明文 Key。
- `systemPrompt` 是可选的自定义提示；为空表示「只用模式提示」。
- 有且只有一个 Agent 是 `isDefault`（最早创建的，或显式标记的）。删除默认 Agent 时会把剩余最早的一个提升为默认。

### 21.2 会话如何解析自己的配置

`ChatViewModel.resolveConfig(state)`：

1. `state.agentId`（会话绑定的 Agent）→ 加载该 Agent → `AgentRepository.toConfig()`；
2. 未设置/已删除 → 默认 Agent；
3. 完全没有任何 Agent → 旧版 `SettingsRepository.configFor(provider)` 兜底。

会话保存 `agentId`；聊天菜单里的选择器调用 `updateAgent(id)`，通过 `ChatRepository.updateMeta` 持久化绑定。压缩、记忆提炼、标题生成全部走同一个 `resolveConfig`——会话在所有环节行为一致。

### 21.3 引导向导

`ui/agents/AgentOnboarding.kt`——四步：

1. **Key 与服务商**——粘贴 Key；`inferProviderFromKey()` 按前缀猜测服务商；快捷预设（DeepSeek / 通义千问 / Kimi / 智谱 GLM / SiliconFlow）填充 Base URL；点「检测」运行 `ModelProbe`（`GET {baseUrl}/models`）并列出服务端模型。
2. **模型**——服务端检测结果、内置目录，或手动输入。
3. **参数**——温度、Max Tokens、深度推理开关。
4. **提示词**——默认模式提示或自定义系统提示 → 保存。

### 21.4 自定义提示词注入位置

`ChatEngine.run()` 把它拼在模式提示之前：

```kotlin
val sysContent = if (custom.isBlank()) systemPromptFor(mode)
                 else "$custom\n\n${systemPromptFor(mode)}"
```

模式提示承载安全行为（如「执行前确认」），永不被替换——自定义提示只叠加人设/领域上下文。

### 21.5 旧版迁移

`BetterAIChatApp.ensureDefaultAgentFromLegacySettings()`（应用启动时在后台作用域运行一次）：若 agents 表为空且旧设置里有 API Key，就把旧的 provider/baseUrl/model/温度设置转换为「默认 Agent」，老用户永远不会看到空选择器。

---

## 22. 联网搜索管线

`web_search` 刻意不止于「调用一个引擎」。整条链路位于 `skills/tools/WebSearchTool.kt`。

### 22.1 扇出与合并

- 六个引擎**并发**运行（各自 `async`）：Bing、百度、Brave、DuckDuckGo、Mojeek、360。
- 合并规则：URL 规范化去重、**每域名最多 2 条**（多样性）、**标题相似去重**（同文不同链）。
- **相关性排序**：按查询词命中打分（标题 ×3、摘要 ×1）排序——只靠引擎顺序会把跑题的页面排前面（搜「Android 16 发布时间」曾把开发者工具首页排第一）。
- URL 剥离追踪参数（`utm_*`、`spm`、`from` 等）。

### 22.2 一次调用拿正文（`read_top`）

默认 `read_top = 1`，附带第一条**成功抓取**结果的正文：

- 元素级提取（`article/main` 内的 `p, h1-h6, li, pre, blockquote`），而非 `body.text()`——这正是剥离作者栏的关键；
- `NOISE_RE` 过滤约 20 种样板模式（阅读量/收藏/二维码/红包……）；
- 相邻重复段落丢弃（源页面常把列表内容在 `<p>` 里重复一遍）；
- **失败跳过**：被拒站点（如百度百科 403）自动跳过、尝试下一条，最多 8 次，直到凑齐 `read_top` 篇；
- 单篇预算 = 5000/read_top 字符；引擎侧截断预算（6000）保证正文完整抵达模型。

### 22.3 多查询、缓存与 refresh

- `query` 支持**数组**（≤3）——对比类问题一轮完成；结果跨查询合并。
- 5 分钟内存缓存，键为 `query|max_results|read_top`；时效性追问可传 `refresh=true` 绕过。
- 引擎诊断：全失败与部分失败的提示告诉模型重试是否有意义。

### 22.4 失败经济学（为什么以前搜索会烧光轮次）

- `DeviceToolRunner.healArgs` 执行前**自愈参数**：类型错误自动转换、拼错的参数名纠正（编辑距离 ≤2）、数组保留；结果会标注修正内容。
- `parseToolArguments` 容忍双重编码字符串与带说明文字的 JSON（部分网关会双重编码）。
- `ChatEngine.runWithCircuitBreaker` 在同一轮内连续失败 3 次后拒绝该工具并给出明确指引。
- MAX 模式最多 50 个工具轮次；其余模式 12。

---

## 23. 长期记忆

### 23.1 存储

`memories` 表（v9）按 `type` 区分两类行：

- `memory`——提炼出的事实（「用户的名字是……」），注入每一次请求；
- `snapshot`——压缩上下文前保存的最近 6 轮，可用「导入最近对话」恢复。

### 23.2 提炼

- **自动**：每完成 10 轮（`ChatViewModel` 的 `autoDistillCount`）静默运行一次提炼。
- **手动**：⋮ 菜单 → 提炼重要信息。
- 提炼提示要求模型在给定已有记忆的前提下只输出**新增**要点；结果经过滤（长度 3–200、不含提示回显）后写入。

### 23.3 注入

`injectMemory()` 在每次生成的 history 前插入一条列出记忆的 system 消息——这就是助手能跨会话「记得你」而无需任何服务端状态的原因。

### 23.4 与压缩的配合

用量超过模型上下文窗口的 85% 时应用会提示，并在本轮结束后自动压缩：最近 6 轮先存快照，更早的历史被摘要消息替换，快照仍可导入恢复。

---

## 24. 扩展指南——如何添加功能

### 24.1 添加设备工具（最常见的任务）

1. 在 `skills/src/main/java/com/betteraichat/skills/tools/` 新建类：

```kotlin
class MyTool : DeviceTool {
    override val name = "my_tool"                       // 蛇形命名，全局唯一
    override val description = "一句话，供模型判断何时使用。写清参数格式。"
    override val readOnly = false                       // true => Plan 模式可用
    override val parameters = schemaOf(
        "text" to stringProp("这个参数的含义"),
        "count" to intProp("整数参数，默认 3"),
        required = listOf("text")
    )
    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val text = arguments["text"]?.jsonPrimitive?.content ?: return "text 参数无效"
        // ... 干活 ...
        return "供模型阅读的结果"
    }
}
```

2. 在 `BetterAIChatApp.kt` 的工具列表（`val tools: List<DeviceTool> = listOf(...)`）中注册。
3. 就这些——注册表按模式暴露（Plan 只有 `readOnly`；Build/Max 全部），引擎负责闸门，参数自动自愈。
4. 失败返回 `"ERROR:…"` 并给出可操作文本——模型会读；`DeviceToolRunner` 也会把 `工具参数/工具执行` 前缀的字符串视为失败以参与熔断。
5. 若工具有非平凡逻辑，加单测（`skills/src/test/...`）；纯系统 API 包装不需要。

### 24.2 添加服务商

1. 在 `:providers` 实现 `ChatProvider`（`core/provider/ChatProvider.kt`）——必须发出 `StreamEvent`：`Delta`、`ThinkingDelta`、`ToolCallsDone`、`Usage`、`Done`、`Error`。
2. 在 `ProviderId` 加入 id，在 `ProviderFactory` 注册，并在 `ModelCatalog` 补充 base URL 与精选模型。
3. 若线上格式与 OpenAI 类似，复用 `OpenAiProvider` 的模式（SSE 解析、工具调用累积），不要从零写。

### 24.3 添加技能动作类型

`SkillActionExecutor.kt` 把动作字符串映射为 Android Intent。在 `when` 中加分支，并在第 12 章的表格中记录 YAML 形态。动作必须参数化（`{占位符}`）——绝不把不受信任的文本插进 shell 命令。

### 24.4 UI 字符串与国际化纪律

- 所有用户可见文案位于 `app/src/main/res/values/strings.xml`（中文，默认）与 `values-en/strings.xml`（英文）。**两个文件必须保持同步**——CI 暂未校验，评审时要留意。
- composable 中用 `stringResource(R.string.x)`；ViewModel/服务中用 `appContext.getString(R.string.x)`。绝不硬编码用户可见文本。
- **发给模型的内容**（系统提示、工具描述、提炼指令）有意保持中文——那不是 UI。

### 24.5 数据库变更

- 提升 `AppDatabase.kt` 的 `version`，新增显式 `MIGRATION_N_N+1`（绝不做破坏性迁移），在 `.addMigrations(...)` 注册，并更新第 14 章的 schema 清单。
- 给既有表加列必须有默认值（`ALTER TABLE … ADD COLUMN … NOT NULL DEFAULT …`）或允许为空。

### 24.6 新设置项

简单偏好放 `SettingsRepository`（SharedPreferences）；列表型数据用 Room 表（如 agents/automations）。设置界面在 `SettingsScreen.kt`；沿用现有分区模式（每个分区是接收 `container/scope/snackbar` 的 composable）。

---

## 25. 排障与已知平台限制

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 刚截过图就报「截屏授权已失效」 | v0.27.4 之前的行为；Android 14+ token 单次使用 | 已由持续投影重写修复（§11.2）。如今再出现说明系统收回了投影（其他应用投屏、用户停止投屏） |
| 截图正常，应用被杀后失效 | MediaProjection 授权按平台设计绑定进程 | 重新授权；前台服务正常情况下会保住它 |
| 工具报 "JsonLiteral is not a JsonObject" | 网关双重编码参数 | v0.26.4 已修（`parseToolArguments`）；出现新格式时扩展该函数并加测试 |
| AI 在坏工具调用上循环 | 模型反复给出非法参数 | 熔断在连续 3 次失败后拒绝；检查该工具的 `description` 是否把参数写清楚 |
| Markdown 显示为纯文本 | 模型输出了 CommonMark 不接受的形式（`###标题`、无外框表格） | `MarkdownNormalizer` 修复已知情况；新增修复要带测试 |
| 搜索无结果 | 网络受限/引擎限流 | 工具会报告引擎诊断；在真机上用真实查询验证 |
| 本地绿、CI 红 | SDK/JDK 差异或测试过期 | 本地跑与 CI 完全相同的命令；检查 workflow 文件 |
| `assembleRelease` 产物未签名 | 签名是刻意的流程（keystore 不入库） | 见第 26 章 |

**刻意的设计限制**（不理解前不要「修复」）：

- CHAT 模式**完全不广播工具**——请求更小、不会误触发工具。
- Plan 模式只暴露 `readOnly` 工具；闸门在服务端二次校验（永不信任模型）。
- Shizuku 的 `run_shell` 按设计无限制；它由显式授权 + BUILD 确认/MAX 模式把守。
- OCR 文件/文件读取工具被白名单限制在应用目录与公共下载/文档/图片目录。

---

## 26. 术语表与交接清单

### 26.1 术语表

| 术语 | 含义 |
| --- | --- |
| **Agent** | 保存的 AI 配置（服务商+Key+模型+参数+提示）；会话绑定其一 |
| **Mode（模式）** | Chat / Plan / Build / Max——控制工具暴露与确认行为 |
| **Gate（闸门）** | 任何工具执行前的服务端权限检查（`ChatEngine.gate`） |
| **Skill（技能）** | markdown 定义的过程（SKILL.md），可经 `load_skill` 作为工具加载 |
| **Distill（提炼）** | 把持久的用户事实提取进 `memories` 表 |
| **Snapshot（快照）** | 压缩前保存的最近 6 轮备份 |
| **Circuit breaker（熔断）** | 同一轮内连续 3 次失败后拒绝该工具 |
| **Self-healing（自愈）** | `DeviceToolRunner.healArgs` 的执行前参数修复 |
| **Pinned（贴底）** | 「最后一项完整可见」——恢复自动跟随的条件 |
| **Flavor（风味）** | `full`（含 OCR）与 `lite` 构建变体 |

### 26.2 交接清单（仓库之外的一切）

- **签名密钥**：`betteraichat-release.keystore` + `keystore_pass.txt` 在 **git 之外**（已 gitignore）。发布手动签名：
  `zipalign -f 4 … && apksigner sign --ks … --ks-pass pass:$(cat keystore_pass.txt)`。丢失 keystore 意味着用户无法原地升级——务必备份。
- **local.properties** 含机器相关的 `sdk.dir`，已 gitignore。
- **GitHub**：发布以 `vX.Y.Z` tag 创建；附带两个 APK（`full`、`lite`）并在说明中写入 SHA-256，中英双语。注意 `gh release create … --notes-file`——默认的 `--generate-notes` 只产出英文。
- **版本约定**：每次发布 `versionCode` +1；`versionName` 近似 semver 的 `0.MINOR.PATCH`，MINOR = 功能，PATCH = 修复。
- **CI**：保持 workflow 常绿；它是唯一的自动化门禁。
- **发布顺序**：升版本 → `assembleFullRelease`/`assembleLiteRelease` → zipalign + 签名 → `git tag vX.Y.Z && git push --tags` → `gh release create` 附说明与两个 APK。
- **已知第三方版本锁定**：markdown 渲染器锁定 0.41.0（更新版本要求 compileSdk 37）；表格渲染是自定义的（`MarkdownTable`），正是为了避开该升级。

---

## 27. 设计哲学与开发方法

第 1–26 章讲代码**做了什么**，本章讲**为什么是这种形态**——信奉的原则、关键决策及其取舍、以及产出这套代码的工作方法。在动手做重大改动之前先读这一章：大多数「显而易见的改进」都已被考虑过，并因某些原因被否决。

### 27.1 设计原则

以下是代码库真正遵循的规则，写新代码时值得保持。

1. **本地优先，无云端。** 应用直接与用户配置的模型服务商通信。没有后端、没有遥测、没有账号。API Key 用 Android Keystore 加密，除发往服务商本身外不离开设备。这是产品决策，并且约束了架构：一切（记忆、快照、Agent、自动化）都必须存在本地 Room/SharedPreferences 中。

2. **永不信任模型。** 模型输出只是*建议*，权威在服务端（应用内）：模式闸门决定哪些工具可见，`gate()` 复核每一次调用，只读工具由注册表强制，权限在执行时再次检查。这是纵深防御——系统提示也会告知模型规则，但没有任何环节依赖模型遵守。

3. **每条工具结果都是带预算的文本。** 工具结果是模型「看到」的东西，所以要为注意力有限的读者写作：长度有界（截断预算）、噪音已滤（作者栏、样板文字）、重复已去、按相关性排序。Token 经济学是一等设计考量——见 `read_top` 预算与引擎侧 6000 字符上限。

4. **失败必须可行动——对模型而言。** 错误文案不只给人看。`"ERROR:缺少通讯录权限，请到 设置 → 权限 → 通讯录 中授权"` 告诉模型下一步做什么。没有下一步的失败会引发重试循环；有下一步的失败会终结循环。

5. **默认拒绝。** CHAT 模式完全不广播工具；未知工具一律拒绝；Plan 模式只暴露 `readOnly` 工具；缺少权限时给出清晰提示而非静默失败。拿不准时，代码库选择更安全的默认，让用户显式开启。

6. **响应，而非轮询。** 流式跟随滚动监听布局变化并响应；文件/屏幕监听走 Flow；SSE 解析是推送驱动。唯一保留下来的轮询（流式 ticker）是因为 delta 到达频率高于合理重组频率——而且它有节流。其余所有「每 N 毫秒检查一次」都作为 bug 来源被移除了。

7. **小而可测的接缝。** 逻辑尽可能放在 composable 与 Android 框架类之外：`ChatEngine` 接收 `ToolRunner` 接口，OCR 是 `OcrProvider` 接口（full/lite 风味的接缝），服务商实现 `ChatProvider`。这才使 60+ 个无需模拟器的单测成为可能——也让 `lite` 仅靠风味差异换掉 ML Kit。

8. **每个关注点只有一个权威来源。** Agent 掌管模型配置；`SettingsRepository` 掌管设备级偏好；conversations 表掌管会话状态；`resolveConfig()` 是唯一的解析链。当两个地方可能回答同一个问题时，代码库选定一个归属，并让所有路径经由它。

### 27.2 关键决策与取舍

| 决策 | 原因 | 代价 |
| --- | --- | --- |
| **手写 `AppContainer` 而非 Hilt** | 无注解处理器（构建快）；完全掌控初始化顺序（见旧配置迁移的顺序 bug）；面积小 | 手工接线；新依赖必须手动穿线 |
| **工具内置，而非 MCP** | 权限桥接高度 Android 专属（无障碍、Shizuku、MediaProjection）；进程内执行没有 IPC 延迟，并共享应用的权限状态 | 暂时无法复用第三方 MCP 服务器——在 `ToolRegistry` 加适配器即可补齐，无需改引擎 |
| **手写 SSE 解析器** | 完全掌控分片事件、重试、双重编码负载与厂商怪癖；无重依赖；单测成本极低 | 需随服务商变化维护——这就是解析器测试存在的原因 |
| **Agent 循环用 `Flow<EngineEvent>`** | 取消是结构化的（停止按钮 = `job.cancel()`）；循环无需 UI 即可测试；ViewModel 可自由过滤/变换事件 | 比回调略啰嗦；线程纪律要求高（见 `runToken` 守卫） |
| **Room + 显式迁移，绝不破坏性** | 聊天历史是用户的数据；对聊天应用而言静默丢失不可接受 | 每次 schema 变更都要手写迁移并更新第 14 章 |
| **自定义表格渲染 + 锁定 markdown 库版本** | 上游 markdown 库更新版本要求 compileSdk 37；表格是模型输出中最常损坏的构件，自定义渲染器物有所值 | 表格渲染的边界情况（无外框、转义）与测试由我们维护 |
| **参数自愈 *与* 熔断双层** | 真实模型持续产出畸形参数（类型错误、拼错键名、双重编码 JSON）。自愈把大多数失败变成成功；熔断阻止其余失败烧光轮次 | 两层都要维护；自愈绝不能*掩盖*真实问题——修正总会标注在结果里 |
| **布局驱动滚动（第三版）** | 轮询式滚动与异步 markdown 布局互相打架；响应布局变化是唯一既正确又无抖动的方案 | 需要理解 `snapshotFlow(layoutInfo)` 与 `DragInteraction` 语义——已写进 §15.3，防止有人把它「简化」回循环 |
| **手动签名发布** | 对个人规模的项目，keystore 绝不能进仓库或 CI secrets | 发布是手动流程；第 26 章的清单因此存在 |

### 27.3 功能是如何做出来的

本应用每个功能都按这个顺序产出：

1. **定义可观察行为**——用户看到什么、模型看到什么、失败长什么样。不是实现。
2. **先造 mock 场景。** 本地 SSE 服务器（§20.6）按*模型名*编排脚本——`newtools`、`mtr`、`shot`……每个场景编码一种行为：一次工具调用序列、一个 markdown 边界、一个双重编码参数。这让整条管线在数秒内可复现，且不消耗 API 配额。
3. **对着 mock 实现**，验证完整路径：请求构造 → 流式 → 工具执行 → 持久化 → UI。
4. **在真机/模拟器上验证**——mock 通过是必要不充分（平台行为有差异：MediaProjection、无障碍、存储作用域）。
5. **为出过问题或可能出问题的逻辑写单测**——解析器边界、规范化器、计算器、参数自愈。不为覆盖率；本仓库的每个测试都对应一次真实故障或真实边界。
6. **周期性做审计。** 这套代码库由三轮并行审计（聊天核心/工具/存储+UI）打磨而成，产出 45+ 项修复。审计的最佳形态是带着新眼睛的独立轮次，随后批量修复。
7. **保持 CI 常绿，小步提交。** CI（单测 + 双风味构建）是唯一自动化门禁；历史线性且可 bisect。

### 27.4 这套代码库回避的反模式（及原因）

- **在 composable 里做 IO 或位图解码**——曾造成主线程卡顿与 ANR 风险；现在所有解码都在 `withContext(Dispatchers.Default)`。
- **`eval()` 式数学求值**——计算器是手写的递归下降解析器；即便只是「算数」，任意代码执行也永远不是选项。
- **硬编码用户可见文案**——i18n 纪律（两个 strings 文件）；唯一保留中文的地方是面向模型的内容，这是刻意的。
- **用 `currentTimeMillis()` 当标识符**——同一毫秒创建的两个提醒会互相覆盖；现在 `RequestCodes` 用原子生成器。
- **`fallbackToDestructiveMigration()`**——永不。数据丢失不是迁移策略。
- **用 `isScrollInProgress` 判断用户意图**——程序滚动也会触发它；跟随逻辑曾被自己的滚动静默关闭，直到改用 `DragInteraction`。
- **把取消异常包进 `runCatching`**——吞掉 `CancellationException` 会泄漏资源（语音识别器）并破坏结构化并发；持有资源的工具显式重抛它。
- **无界的工具循环**——模型卡在坏调用上曾耗尽轮次预算；现在是：自愈 → 标注重试 → 熔断 → 可行动的拒绝。

### 27.5 如果你接手：这个项目的精神

BetterAIChat 的存在是为了证明：独立开发者也能在手机上交付*真正的*智能体——不是聊天壳——本地优先、尊重隐私、具备真实的设备能力。贯穿始终的工程取向是**让模型的工作变容易**（清晰的工具、干净的结果、可行动的错误）与**让人保持掌控**（模式、确认、显式权限）。在「聪明」与「可调试」之间，这套代码库永远选可调试——这就是 mock 服务器、审计轮次和本文档存在的原因。
