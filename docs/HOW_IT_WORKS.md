# How BetterAIChat Works — The Complete Technical Walkthrough

This document explains the internals of **BetterAIChat** in exhaustive detail: module architecture, every layer of the request pipeline, the streaming protocol, the agent loop, the tool system, permission bridging, the automation engine, storage, UI rendering, security, and the engineering lessons learned from real bugs. It is written as a study guide for programmers who want to understand a real, working Android AI-agent application.

> Scope: ~63 built-in device tools, opencode-style Skills, Agents (one-tap per-conversation configs), Shizuku + Accessibility + MediaProjection integration, and a background automation engine.

---

## Table of Contents

1. [Project overview & file map](#1-project-overview--file-map)
2. [Module architecture & dependency inversion](#2-module-architecture--dependency-inversion)
3. [The domain model](#3-the-domain-model)
4. [Provider adapters & model catalog](#4-provider-adapters--model-catalog)
5. [The journey of a single message](#5-the-journey-of-a-single-message)
6. [SSE streaming, deep dive](#6-sse-streaming-deep-dive)
7. [The agent loop (ChatEngine)](#7-the-agent-loop-chatengine)
8. [Modes & the safety gate](#8-modes--the-safety-gate)
9. [The confirmation flow](#9-the-confirmation-flow)
10. [The tool system](#10-the-tool-system)
11. [Permission bridging — how the AI "touches" your phone](#11-permission-bridging--how-the-ai-touches-your-phone)
12. [Skills (opencode-style)](#12-skills-opencode-style)
13. [The automation engine](#13-the-automation-engine)
14. [Storage & state management](#14-storage--state-management)
15. [The UI layer](#15-the-ui-layer)
16. [Security design](#16-security-design)
17. [Error handling matrix](#17-error-handling-matrix)
18. [Engineering lessons from real bugs](#18-engineering-lessons-from-real-bugs)
19. [Suggested study order](#19-suggested-study-order)
20. [Getting started — build, run, test, debug](#20-getting-started--build-run-test-debug)
21. [Agents — per-conversation configurations](#21-agents--per-conversation-configurations)
22. [The web-search pipeline](#22-the-web-search-pipeline)
23. [Long-term memory](#23-long-term-memory)
24. [Extending the app — how to add things](#24-extending-the-app--how-to-add-things)
25. [Troubleshooting & known platform limits](#25-troubleshooting--known-platform-limits)
26. [Glossary & handover checklist](#26-glossary--handover-checklist)
27. [Design philosophy & development approach](#27-design-philosophy--development-approach)

> **Reading guide for new maintainers**: skim chapters 1–3, then jump to **20 (Getting started)** to get the app running, **24 (Extending)** for your first change, and **26 (Handover checklist)** for everything outside the repository. Chapters 4–19 are deep dives — read them when you touch the corresponding area.

---

## 1. Project overview & file map

```
BetterAIChat/
├── settings.gradle.kts                     # include(":app", ":core", ":providers", ":skills")
├── gradle/libs.versions.toml               # version catalog (single source of truth for deps)
│
├── app/                                    # Android application (UI + system integration)
│   ├── src/main/java/com/betteraichat/
│   │   ├── BetterAIChatApp.kt              # Application class + AppContainer (DI wiring)
│   │   ├── MainActivity.kt                 # Single-activity Compose entry
│   │   ├── ui/
│   │   │   ├── navigation/AppNav.kt        # Navigation graph
│   │   │   ├── conversations/             # Conversation list screen
│   │   │   ├── chat/
│   │   │   │   ├── ChatScreen.kt           # Chat UI, input bar, dialogs, scroll logic
│   │   │   │   ├── ChatViewModel.kt        # State machine + send/stop/compress/edit
│   │   │   │   └── MessageViews.kt         # Bubble/tool-card/code-card composables
│   │   │   ├── settings/SettingsScreen.kt  # All settings sections
│   │   │   └── agents/                        # Agent onboarding wizard + picker dialogs
│   │   └── tools/
│   │       ├── ScreenshotManager.kt        # MediaProjection capture service + bridge
│   │       ├── BacAccessibilityService.kt  # AccessibilityService (ua_* gestures)
│   │       ├── BacNotificationListener.kt  # NotificationListenerService + cache
│   │       ├── AutomationScheduler.kt      # AlarmManager/battery automations
│   │       ├── SpeechInputHelper.kt        # Voice input (SpeechRecognizer)
│   │       ├── SpeechPlayer.kt             # TTS
│   │       ├── AttachmentProcessor.kt      # Image/doc/PDF preprocessing
│   │       ├── ScreenOcr.kt (full variant) # ML Kit Chinese OCR
│   │       └── ShizukuManager.kt           # Shizuku permission state
│   └── src/full/java/  src/lite/java/      # Flavor-specific sources (OCR only in full)
│
├── core/                                  # Pure logic (no Android UI)
│   ├── src/main/java/com/betteraichat/core/
│   │   ├── engine/ChatEngine.kt            # The agent loop
│   │   ├── chat/ChatRepository.kt          # DB access layer
│   │   ├── db/AppDatabase.kt               # Room entities + DAOs + migrations (v10)
│   │   ├── model/ChatModels.kt             # ChatMessage, ToolCall, ProviderConfig…
│   │   ├── mode/AppMode.kt                 # Chat/Plan/Build/Max
│   │   ├── catalog/ModelCatalog.kt         # Built-in model registry per provider
│   │   ├── provider/ChatProvider.kt        # The provider interface
│   │   ├── sse/SseParser.kt                # SSE line parser
│   │   ├── skills/SkillRepository.kt       # SKILL.md parsing (YAML frontmatter)
│   │   └── storage/                        # SettingsRepository, KeyStoreCrypto
│   └── ...
│
├── providers/                             # Vendor API adapters
│   └── src/main/java/com/betteraichat/providers/
│       ├── ProviderFactory.kt              # providerId → ChatProvider
│       ├── openai/OpenAiProvider.kt        # OpenAI-compatible (incl. deepseek/qwen/kimi…)
│       ├── anthropic/AnthropicProvider.kt
│       └── gemini/GeminiProvider.kt
│
└── skills/                                # Device tools
    └── src/main/java/com/betteraichat/skills/
        ├── ToolModels.kt                   # DeviceTool interface, ToolContext, bridges
        ├── ToolRegistry.kt                 # builtin + skill-defined tools
        ├── DeviceToolRunner.kt             # name+args → DeviceTool.execute
        ├── SkillActionExecutor.kt          # Runs skill-defined action types
        └── tools/                          # 63 tool implementations
```

Two build flavors exist: **full** (everything, ~55 MB) and **lite** (~10 MB, no on-device OCR). Flavor-specific code lives in `app/src/full/` and `app/src/lite/`.

---

## 2. Module architecture & dependency inversion

### 2.1 The dependency graph

```
:app ────────▶ :core
   │           :providers
   └─────────▶ :skills ────▶ :core
```

- `:app` depends on everything.
- `:skills` depends on `:core` only.
- `:providers` depends on `:core` only.
- `:core` depends on nothing internal (only Android SDK + libraries).

### 2.2 Why this layering matters

The `:skills` module contains code that *must* interact with Android framework services (screenshots, accessibility gestures, notifications). The naive approach would be importing `android.app.Activity` or the app's `ScreenshotManager` directly into tools. That creates a hard dependency from `:skills` → `:app`, which makes the modules inseparable and untestable.

Instead, `:skills` defines **interfaces** for anything app-specific, and `:app` implements them:

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

`:app` constructs the real implementations and injects them into `ToolContext` (see `BetterAIChatApp.kt`). The `ScreenshotManager` implements `ScreenshotProvider`; `BacAccessibilityService` implements `AccessibilityBridge`; `ScreenOcr` implements `OcrProvider`.

This is **dependency inversion**: the abstraction lives with the consumer (tools), the implementation lives with the producer (app). Consequences:

- `:skills` can be unit-tested by faking `ToolContext`.
- No circular Gradle dependencies (Gradle would fail the build on a cycle).
- The lite build can provide a stub `OcrProvider` returning an error message, with zero changes to `:skills`.

### 2.3 AppContainer — hand-rolled dependency injection

`BetterAIChatApp.kt` contains `AppContainer`, a plain class that constructs the whole object graph:

```kotlin
class AppContainer(context: Application) {
    val db = AppDatabase.get(context)
    val settings = SettingsRepository(context)
    val repository = ChatRepository(db)
    val skillRepository = SkillRepository(context.applicationContext)

    private val screenshotManager = ScreenshotManager(context.applicationContext)
    private val ocrBridge = ScreenOcr(screenshotManager)              // full variant
    private val accessibilityBridge = object : AccessibilityBridge { … }

    private val toolContext = ToolContext(context.applicationContext, screenshotManager, ocrBridge, accessibilityBridge)

    val automationScheduler = AutomationScheduler(context.applicationContext, db) { runner }
    private val automationBridge = object : AutomationBridge { … }

    val tools: List<DeviceTool> = listOf( /* 63 tools, see BetterAIChatApp.kt */ )
    val registry = ToolRegistry(tools)
    val runner = DeviceToolRunner(registry, toolContext)
    val engine = ChatEngine(providerFactory, registry, runner)
}
```

Note the **lambda-based lazy dependency** for the scheduler: `AutomationScheduler(context, db) { runner }` — the scheduler needs `runner`, but `runner` needs the registry which needs the tool list that includes `CreateAutomationTool(automationBridge)`… a circular construction. The lambda defers the `runner` lookup until the scheduler actually executes an automation, breaking the cycle. This is a neat trick worth remembering for object graphs with cycles.

Other components resolve it from anywhere:

```kotlin
val container = (applicationContext as BetterAIChatApp).container
```

---

## 3. The domain model

All core types live in `:core/model/ChatModels.kt`. Understanding these makes the whole codebase readable.

### 3.1 Providers and roles

```kotlin
enum class ProviderId(val displayName: String) {
    OPENAI_COMPAT("OpenAI 兼容"), ANTHROPIC("Anthropic Claude"), GEMINI("Google Gemini")
}

enum class ChatRole(val wire: String) {
    SYSTEM("system"), USER("user"), ASSISTANT("assistant"), TOOL("tool")
}
```

`ChatRole` carries the `wire` string used in API requests — this keeps the domain clean while the providers translate to wire format.

### 3.2 ToolCall and its state machine

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

A `ToolCall` travels through these states:

```
PENDING ──▶ RUNNING ──▶ DONE
   │           │
   │           └──────▶ FAILED        (execution threw)
   ├──────▶ REJECTED                  (user declined or request dropped)
   └──────▶ DENIED                    (mode gate refused: e.g. Chat mode)
```

The UI renders each state as a colored badge in `MessageViews.kt` (`StatusBadge`):

| Status | Badge | Color |
|---|---|---|
| PENDING | 等待 | gray |
| RUNNING | 执行中… | primary |
| DONE | 已完成 | green |
| FAILED | 失败 | red |
| REJECTED | 已拒绝 | red |
| DENIED | 已禁止 | red |

### 3.3 ChatMessage — the wire/domain message

```kotlin
data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,        // for TOOL messages: which call this answers
    val toolName: String? = null,
    val model: String? = null,             // display metadata
    val mode: AppMode? = null,             // display metadata
    val attachments: List<Attachment> = emptyList(),
    val thinkingText: String? = null,
    val thinkingSignature: String? = null
)
```

Two important details:

- `toolCallId` links a `TOOL` role message back to the assistant's `tool_calls` entry — this is what OpenAI/Anthropic require for the request to be valid (see §7.4).
- `thinkingText`/`thinkingSignature` carry reasoning content from reasoning models (o1/deepseek-reasoner/Claude Opus) and are sent back with `reasoning_signature` where the vendor requires it.

### 3.4 ProviderConfig

```kotlin
data class ProviderConfig(
    val provider: ProviderId,
    val baseUrl: String,
    val apiKey: String,          // stored encrypted — see KeyStoreCrypto
    val model: String,
    val temperature: Double,
    val maxTokens: Int,
    val reasoning: Boolean,     // "deep thinking" mode for reasoning models
    val systemPrompt: String = ""  // custom Agent prompt, prepended to the mode prompt in ChatEngine
)
```

The API key is **encrypted with the Android Keystore** (`:core/storage/KeyStoreCrypto.kt`) — the key material never leaves the hardware-backed keystore; the app only stores the ciphertext.

### 3.5 Attachment

```kotlin
@Serializable
data class Attachment(
    val kind: String,           // "image" | "doc"
    val name: String,
    val mimeType: String,
    val dataBase64: String = "",      // images are base64-embedded
    val textContent: String? = null   // PDF/docx/xlsx extracted text
)
```

Images are downscaled/compressed and sent inline (vision models accept base64 data URLs). Documents (PDF, Word, Excel) are parsed **on-device** — including Chinese OCR for PDFs — and sent as extracted text.

---

## 4. Provider adapters & model catalog

### 4.1 The ChatProvider interface

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

Every vendor (OpenAI-compatible, Anthropic, Gemini) implements this one method: take history + config + tool specs, return a cold `Flow<StreamEvent>`. `ProviderFactory` maps `ProviderId` → implementation.

`StreamEvent`:

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

The abstraction is deliberately vendor-neutral: OpenAI emits `delta.tool_calls`, Anthropic emits `delta.content[]` with `input_json_delta`, Gemini emits `functionCall` in its own shape — but all three boil down to `Delta` + `ToolCallsDone`.

### 4.2 ModelCatalog — curated model metadata

```kotlin
data class ModelEntry(
    val id: String, val label: String,
    val temperature: Double = 0.7, val maxTokens: Int = 4096,
    val supportsReasoning: Boolean = false,
    val contextWindow: Int = 200_000
)
```

The catalog (~50 models across three providers) gives the UI sensible defaults (temperature, max tokens, context window for the usage meter, reasoning support) without hardcoding them in the screens. `contextWindow` also drives the token-usage indicator (`usageInput / contextWindow %`) shown in the chat top bar.

### 4.3 Wire format translation

OpenAI-compatible request body:

```kotlin
val body = OpenAiRequest(
    model = config.model,
    messages = messages.mapNotNull { it.toWire() },
    temperature = if (reasoning) null else config.temperature,   // reasoning models forbid temperature
    maxTokens = config.maxTokens,
    reasoningEffort = if (reasoning) "high" else null,
    streamOptions = OpenAiStreamOptions(),
    tools = tools.map { OpenAiTool(OpenAiToolFunction(it.name, it.description, it.parameters)) }
        .takeIf { it.isNotEmpty() }
)
```

Subtleties:

- **Reasoning models reject `temperature`** — it's `null` when reasoning is on.
- **Tool schema** is the `DeviceTool.parameters` JSON Schema passed through untouched — the LLM uses it to generate arguments.
- `messages.mapNotNull { it.toWire() }` lets `toWire()` drop messages that can't be represented (e.g. empty assistant content).

---

## 5. The journey of a single message

### 5.1 Full sequence diagram

```
 User                    ChatScreen            ChatViewModel             ChatEngine              Provider             Room
  │  tap ⏎ (send)            │                        │                       │                     │                   │
  │─────────────────────────▶│  onSend()              │                       │                     │                   │
  │                          │───────────────────────▶│  send()               │                     │                   │
  │                          │                        │  ├─ isRunning? → return
  │                          │                        │  ├─ state.isRunning = true
  │                          │                        │  └─ launch { sendWithContent() }
  │                          │                        │        ├─ insertMessage(USER) ────────────────────────────────▶ INSERT
  │                          │                        │        ├─ sendTick++ (UI scrolls to bottom)
  │                          │                        │        └─ runGeneration(cid)
  │                          │                        │              │  runJob?.isActive → return   (mutex)
  │                          │                        │              │  state.isRunning = true
  │                          │                        │              │  streaming = placeholder(id=-1)
  │                          │                        │              ├─▶ engine.run(history, config, mode)
  │                          │                        │              │        └─ provider.chatStream(...) ──▶ HTTP POST
  │                          │                        │              │            │
  │                          │ ◀── Delta ─────────────┼──── Delta ───┼────────────┼──── SSE parse
  │                          │  (100ms ticker refresh)│              │            │
  │ ◀── recompose ───────────│◀── state.messages ──────┘              │            │
  │                          │                                        │            │
  │                          │                          └── AssistantFinished ───▶ INSERT (assistant)
  │                          │                                        │
  │                          │                              tool_calls? ── yes ──▶ for each call:
  │                          │                                        │        gate(mode) → confirm dialog
  │  ◀── AlertDialog ────────│◀── confirmRequest ────────────────────┼──── tryEmit(call)
  │  tap 允许 ──────────────▶│  respondConfirm(true) ─────────────────┼──── deferred.complete(true)
  │                          │                                        │        ToolRunner.run()  ──▶ DeviceTool.execute()
  │                          │                                        │        insertMessage(TOOL result)
  │                          │                                        │        loop again (MAX: 50 rounds, others: 12)
  │                          │                                        └── Completed
  │ ◀── final UI ────────────│◀── refresh()
```

### 5.2 The ViewModel state machine

`ChatUiState` is one immutable data class held in a `MutableStateFlow`:

```kotlin
data class ChatUiState(
    val conversationId: Long, val title: String,
    val provider: ProviderId, val model: String, val mode: AppMode,
    val messages: List<UiMessage>,        // dbMessages + streaming
    val input: String,
    val isRunning: Boolean,
    val sendTick: Int,                    // increment = "please scroll to bottom"
    val confirmRequest: ConfirmRequest?,  // pending tool confirmation
    val notification: String?,            // transient snackbar message
    val error: String?,                   // persistent error banner
    val pendingAttachments: List<PendingAttachment>,
    val processing: Boolean,
    val agentId: Long?, val agentName: String,  // Agent bound to this conversation
    val attachmentError: String?,
    // … (usage totals are derived from the last message with usage, not stored)
)
```

The key discipline: **all mutations go through `_state.update { it.copy(...) }`** — no direct field writes, no partial updates. This gives:

- Thread safety (StateFlow update is atomic).
- A single source of truth for the UI.
- Easy debugging (log the state transitions).

### 5.3 Merging DB messages with the live stream

```kotlin
private var dbMessages: List<UiMessage> = emptyList()
private var streaming: UiMessage? = null

private fun refresh() {
    _state.update { it.copy(messages = dbMessages + listOfNotNull(streaming)) }
}
```

- `dbMessages` is kept fresh by a Room Flow: `repository.observeMessages(id).collect { list -> dbMessages = mapToUi(list); refresh() }` — **any DB change (including the AI's own inserts) automatically re-renders the chat.**
- `streaming` is the in-flight assistant message with `id = -1` (a sentinel meaning "not in DB yet").
- A 100 ms ticker job (`streamTickerJob`) calls `refresh()` while streaming so deltas appear even though `state.messages` only changes when the ticker runs — this is the *decoupling* of event rate (many deltas/sec) from UI refresh rate (10 Hz).

### 5.4 The streaming accumulator

Each `Delta` appends to the streaming message. The subtle performance trap (which we hit and fixed): appending to an immutable `String` on every delta is **O(n²)** — a 40,000-character reply delivered in 2,000 deltas copies ~40M characters total on the main thread. The fix pattern (used in the engine) is a `StringBuilder` accumulator that is flushed on a timer or at completion. The ViewModel keeps the simple append but relies on the 100 ms ticker to bound recompositions.

---

## 6. SSE streaming, deep dive

### 6.1 The SSE protocol in practice

Server-Sent Events over HTTP look like:

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

Rules used by the parser:

1. Lines starting with `data:` accumulate payload.
2. A **blank line** terminates the event → dispatch the accumulated data.
3. `[DONE]` is a sentinel event → stop consuming.

### 6.2 The parser, line by line

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
                            if (!shouldContinue) break          // ← cancellation via return value
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

Learning points:

- **Backpressure-free by design**: `flowOn(Dispatchers.IO)` moves the blocking read off the main thread; the collector runs wherever it collects.
- **Cancellation without exceptions**: instead of throwing, the callback returns `false` and the loop breaks — clean, no `CancellationException` handling needed.
- `event:` lines are used by some servers (e.g. Anthropic uses `event: content_block_delta`); the parser preserves the event name so consumers can branch on it.

### 6.3 Chunk decoding and tool-call accumulation

In `OpenAiProvider.chatStream()`:

```kotlin
val toolAcc = mutableMapOf<Int, ToolAcc>()          // index → accumulated tool call
SseParser.parse(body) { _, data ->
    if (data == "[DONE]") {
        emit(StreamEvent.ToolCallsDone(toolAcc.toToolCalls()))   // flush partial calls
        emit(StreamEvent.Done)
        return@parse false
    }
    val chunk = runCatching { json.decodeFromString(OpenAiChunk.serializer(), data) }
        .getOrNull() ?: return@parse true            // ignore malformed chunk, keep going
    chunk.usage?.let { /* emit Usage */ }
    val choice = chunk.choices.firstOrNull() ?: return@parse true
    choice.delta?.content?.let { emit(StreamEvent.Delta(it)) }
    choice.delta?.reasoning?.let { emit(StreamEvent.ThinkingDelta(it)) }
    choice.delta?.toolCalls?.forEach { tc ->
        // tc.index + tc.function.name + tc.function.arguments (possibly split across chunks)
        toolAcc.mergeFragment(tc)
    }
    true
}
```

Tool-call arguments arrive **split across chunks** (often JSON fragments like `{"app":` then `"计算器"}`), so the provider must accumulate per `index` until `finish_reason: "tool_calls"` or `[DONE]`. `ToolCallsDone` is emitted once at the end with the fully-joined arguments.

### 6.4 Anthropic specifics

Anthropic's streaming is event-oriented:

```
event: message_start
event: content_block_start   (content_block {type:"tool_use", id, name})
event: content_block_delta   (input_json_delta: {partial_json:"{\"app\":"})
event: content_block_delta   (input_json_delta: {partial_json:"计算器\"}"})
event: content_block_stop
event: message_stop
```

The adapter (`AnthropicProvider.kt`) listens for `content_block_delta` and joins `partial_json` fragments; text deltas arrive as `text_delta`. The SSE `event:` field is exactly why `SseParser` preserves event names.

### 6.5 Retry & timeout discipline

```kotlin
private suspend fun executeWithRetry(call: okhttp3.Call): okhttp3.Response {
    val retryableCodes = setOf(429, 502, 503, 504)
    repeat(2) { attempt ->
        try {
            val response = call.clone().execute()        // ← clone! a Call can execute once
            if (response.isSuccessful || response.code !in retryableCodes) return response
            response.close()
            if (attempt == 0) delay(500)                 // backoff
        } catch (e: java.io.IOException) {
            if (attempt == 0) delay(500) else throw e
        }
    }
    return call.clone().execute()
}
```

Why `clone()` matters: `OkHttp.Call` is single-use. Executing it twice throws `IllegalStateException("Already Executed")`. The old code reused the same `Call`, so retries *never worked* — a real bug fixed in v0.20.1.

Timeouts: `connectTimeout(30s)` + `readTimeout(120s)`. The read timeout is the guard against a server that stops sending but keeps the connection open — without it the app would hang forever in a state where the user can only force-stop.

---

## 7. The agent loop (ChatEngine)

### 7.1 The loop

```kotlin
fun run(messages, config, mode): Flow<EngineEvent> = flow {
    val provider = providerFactory(config.provider)     // guarded in try
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
            if (e is CancellationException) throw e        // user stop propagates
            emit(EngineEvent.Failed(e.message ?: e.javaClass.simpleName))
            return@flow
        }
        val assistantMsg = ChatMessage(role = ASSISTANT, content = text, toolCalls = toolCalls, …)
        emit(EngineEvent.AssistantFinished(assistantMsg))
        if (toolCalls.isEmpty()) break                    // ← normal exit
        toolRounds++
        history = history + assistantMsg
        for (call in toolCalls) {
            val spec = toolCatalog.find(call.name)
            val decision = gate(mode, spec, call)
            val (status, resultText) = when (decision) {
                Denied  → DENIED to "工具被拒绝执行：reason"
                NeedsConfirm → … confirm dialog … then execute
                Allow   → executeTool(call, spec)
            }
            emit(EngineEvent.ToolCallFinished(finished))
            history = history + ChatMessage(role = TOOL, content = resultText, toolCallId = call.id, …)
        }
    }
    emit(EngineEvent.Completed)
}.flowOn(Dispatchers.IO)
```

### 7.2 Why a `Flow` and not a callback

The engine is a **cold flow**: nothing runs until someone collects. This gives the ViewModel:

- Structured concurrency (`viewModelScope.launch { engine.run(...).collect { … } }` — cancellation of the job cancels the flow).
- A natural stream of events (delta / finished / failed).
- Composability (map, filter, timeout…).

### 7.3 The system prompts — prompt engineering per mode

```kotlin
private fun systemPromptFor(mode: AppMode): String = when (mode) {
    AppMode.CHAT -> "你是一个友好的 AI 助手，请用简洁、准确的语言回答用户的问题。你不需要也不允许调用任何工具。"
    AppMode.PLAN -> "你现在处于 Plan（计划）模式。你只能进行分析、制定方案和查看只读信息，绝对禁止执行任何修改设备的操作。…"
    AppMode.BUILD -> "你现在处于 Build（构建）模式。你可以调用设备工具来帮助用户完成任务，但每个工具执行前系统会请求用户确认，因此请大胆、合理地提出工具调用，明确说明每一步的目的。"
    AppMode.MAX -> "你现在处于 Max（最大）模式。你可以自主、连续地调用设备工具来完成用户任务，无需每次询问用户确认。…"
}
```

The prompt and the gate (§8) are **two independent layers**: the prompt tells the model what to prefer; the gate *enforces* it regardless of what the model does. Defense in depth.

### 7.4 Tool-result validity — the poisoned-conversation bug

OpenAI requires: every `tool_calls` entry in an assistant message must have a corresponding `tool` role message with the same `tool_call_id`, or the API returns HTTP 400. The same rule applies to Anthropic (`tool_result`) and Gemini (`functionResponse`).

The dangerous scenario: the user presses **stop** while the engine is waiting for a confirmation or executing a tool. The engine is cancelled before it can emit `ToolCallFinished`, but the assistant message (with PENDING tool calls) was already persisted. The next message in that conversation would 400 forever — a *poisoned conversation*.

The fix (v0.20.1), in `ChatViewModel.stop()`:

```kotlin
private suspend fun failPendingToolCalls() {
    val entity = pendingAssistantEntity ?: return
    val calls = decodeToolCalls(entity.toolCallsJson)
    val pending = calls.filter { it.status == PENDING }
    if (pending.isEmpty()) return
    // 1. update the assistant entity: PENDING → REJECTED, result = "已取消"
    repository.updateMessage(entity.copy(toolCallsJson = encode(cancelled)))
    // 2. insert a TOOL message per cancelled call so the API sees a complete pair
    pending.forEach { call ->
        repository.insertMessage(domainToMessage(
            ChatMessage(role = TOOL, content = "已取消", toolCallId = call.id, toolName = call.name),
            entity.conversationId
        ))
    }
    pendingAssistantEntity = null
}
```

This is a great example of a **correctness invariant** (conversation history must be a valid tool-call transcript) discovered through a failure mode analysis, not through testing.

---

## 8. Modes & the safety gate

### 8.1 The four modes

| Mode | Tools | Confirmations | Use case |
|---|---|---|---|
| `Chat` | none | — | plain conversation |
| `Plan` | read-only only | none | analysis, advice, planning |
| `Build` | all | every tool | user-supervised tasks |
| `Max` | all | none | autonomous multi-step tasks |

### 8.2 The gate implementation

```kotlin
private fun gate(mode: AppMode, spec: ToolSpec?, call: ToolCall): GateResult {
    if (mode == AppMode.CHAT) return GateResult.Denied("Chat 模式不执行设备工具")
    val s = spec ?: return GateResult.Denied("未知工具：${call.name}")
    if (mode == AppMode.PLAN && !s.readOnly) return GateResult.Denied("Plan 模式仅允许只读工具")
    if (mode == AppMode.BUILD) return GateResult.NeedsConfirm
    return GateResult.Allow
}
```

Every tool declares `readOnly`; the gate is the only place that trusts it. Note `spec == null` → DENIED: even if a model hallucinates a tool name, the engine refuses instead of crashing.

### 8.3 Tool selection per mode — `ToolRegistry.specsFor`

```kotlin
override fun specsFor(mode: AppMode): List<ToolSpec> = when (mode) {
    AppMode.CHAT -> emptyList()                                    // no tools advertised at all
    AppMode.PLAN -> builtinTools.filter { it.readOnly }.map { it.spec() }
    AppMode.BUILD, AppMode.MAX -> builtinTools.map { it.spec() } + dynamicTools.values.map { it.spec() }
}
```

The `Chat` mode **doesn't even advertise tools** in the request — the model can't call what it doesn't see. This is both cheaper (smaller request) and safer (the model never attempts).

---

## 9. The confirmation flow

### 9.1 Decoupling engine and UI

The engine runs on `Dispatchers.IO`; the dialog lives in Compose. They communicate through:

1. `MutableSharedFlow<ToolCall>` with `extraBufferCapacity = 8` — engine → UI direction.
2. A `CompletableDeferred<Boolean>` per call — UI → engine direction.

```kotlin
// Engine side (NeedsConfirm branch)
val deferred = CompletableDeferred<Boolean>()
pendingConfirms[call.id] = deferred
val allow = if (!confirmRequestsFlow.tryEmit(call)) {       // no subscriber / buffer full?
    pendingConfirms.remove(call.id)
    false                                                   // safe fallback: REJECTED
} else {
    try { withTimeout(300_000) { deferred.await() } }
    catch (e: CancellationException) { throw e }            // user stopped the whole job
    catch (e: Exception) { false }                          // timeout → reject
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

The dialog offers **允许 (allow) / 拒绝 (reject) / 停止整个任务 (stop entire task)**. Stop cancels `runJob`, which cancels the flow, which cancels `deferred.await()` via `CancellationException` propagation — the `throw e` path — and then `failPendingToolCalls()` (see §7.4) keeps history valid.

### 9.2 `tryEmit` failure handling

`MutableSharedFlow.tryEmit` returns `false` if there are no subscribers or the buffer is full (e.g. 8+ simultaneous confirmations). The old code silently ignored the result — the user would never see the dialog and the call would time out after 5 minutes. The fix falls back to an immediate REJECTED with an explicit message, so a dropped request can never stall the loop.

---

## 10. The tool system

### 10.1 The DeviceTool contract

```kotlin
interface DeviceTool {
    val name: String
    val description: String       // ← prompt material for the LLM
    val readOnly: Boolean
    val parameters: JsonObject    // ← JSON Schema for argument generation
    suspend fun execute(context: ToolContext, arguments: JsonObject): String
}
```

Because `description` and `parameters` are *the only* information the model has about the tool, writing them well is prompt engineering:

- Describe **when** to use the tool and **what it returns**.
- Declare required vs optional parameters.
- Return human-readable strings — they're inserted into the conversation history and re-sent to the model on the next round.

### 10.2 A representative tool

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

### 10.3 Argument parsing pattern

```kotlin
val percent = arguments["percent"]?.jsonPrimitive?.content?.toIntOrNull()
    ?.coerceIn(0, 100) ?: return "percent 参数无效"
```

Every tool validates and sanitizes arguments before acting — bad arguments return an error string rather than throwing. Since errors are fed back to the model, the model can **self-correct** on the next round (a real emergent behavior: the LLM reads "percent 参数无效" and retries with a valid value).

### 10.4 Shizuku execution — root-level commands safely

`ShizukuExec` wraps the Shizuku binder into a small helper:

```kotlin
val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
val proc = service.newProcess(arrayOf("sh", "-c", command), null, null)
// read stdout/stderr asynchronously
val exited = proc.waitForTimeout(timeoutSeconds.toLong(), "SECONDS")
if (!exited) { proc.destroy(); close streams; return "命令超时" }
```

Details worth copying:

- **Timeout before reading**: `waitForTimeout` bounds the whole command; after timeout the streams are closed so blocked `readText()` futures can't leak IO threads.
- **Output cap**: results longer than 8000 chars are truncated with a note.
- **Whitelist where possible**: `manage_app` validates the package name with `Regex("[a-zA-Z0-9._]+")` before interpolating into `pm …` — only `run_shell` (explicitly designed to run arbitrary commands) is unrestricted.

### 10.5 The calculator — a safe evaluator instead of eval

```kotlin
class Parser(private val input: String) {
    fun parse(): String { /* recursive descent: expression → term → factor → number */ }
}
```

`calculator` implements a **recursive-descent parser** (~80 lines) supporting `+ - * / % ^ ( )` and decimals — deliberately *not* `eval()` or a JavaScript engine, so no arbitrary code execution is possible. It's also a nice teaching example of grammar → code.

---

## 11. Permission bridging — how the AI "touches" your phone

### 11.1 The full capability matrix

| Capability | Bridge | User grant | Example tools |
|---|---|---|---|
| Screenshot & screen analysis | `MediaProjection` + foreground service | one-time auth (Android 15: pick BetterAIChat as the app to capture) | `take_screenshot`, `screen_ocr` |
| UI automation | `AccessibilityService` | enable in system settings | `ua_type`, `ua_tap`, `ua_swipe`, `ua_press`, `ua_tap_text`, `ua_find_text` |
| Root-level shell | Shizuku | install Shizuku + grant | `run_shell`, `manage_app`, `set_wifi`, `set_power_saver` |
| Read notifications | `NotificationListenerService` | notification access | `read_notifications` |
| Foreground app | `UsageStatsManager` | usage access (appops) | `get_foreground_app` |
| System settings | `WRITE_SETTINGS` | settings access | `set_brightness`, `set_screen_timeout` |
| Notifications out | `POST_NOTIFICATIONS` | runtime permission | `send_notification`, `schedule_repeat` |
| DND | `NotificationManager` | policy access (Android <15) | `set_dnd` |
| Location | `LocationManager` | location permission | `get_location` |
| Vibration / wake-lock | `Vibrator` / `PowerManager` | — (normal) | `vibrate`, `keep_screen_on` |
| Reboot recovery | `BOOT_COMPLETED` receiver | — | re-registers repeat tasks & automations |
| Record & transcribe | `SpeechRecognizer` | record audio | `transcribe_audio` |
| Screen recording | `MediaProjection` + `MediaRecorder` | capture grant | `screen_record` |

The Settings screen shows live status for each grant and deep-links to the system screen to grant it.

### 11.2 MediaProjection lifecycle (the tricky one)

**Android 14+ changed the rules**: a MediaProjection token can be consumed exactly once (`getMediaProjection`), and its `createVirtualDisplay` can be called only once per token. The naive "create projection -> capture -> tear down" flow therefore works exactly once; the second capture fails with "authorization expired" - an app that analyzes the screen and then takes another screenshot goes blind.

The service therefore creates the `VirtualDisplay` + `ImageReader` **once, when authorization arrives**, and keeps them alive for the whole session:

```kotlin
// On first authorized request: create the persistent pipeline (single token use)
private fun ensurePersistentDisplay() {
    if (reader != null) return
    val (w, h, d) = screenMetrics()
    val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
    vDisplay = projection!!.createVirtualDisplay("BetterAIChatShot", w, h, d,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null)
    reader = r
}

// Every capture just reads the latest frame from the persistent reader
private suspend fun captureFromPersistent(): String {
    maybeResize()                       // rotation/resolution changes: vd.resize + swap reader
    val image = reader!!.acquireLatestImage() ?: waitAndRetry()
    // planes -> bitmap (rowStride padding handled) -> cacheDir/screenshots/*.png
}
```

Key mechanics:

- The **foreground service** (`foregroundServiceType="mediaProjection"`) owns the pipeline so the process (and the grant) survive backgrounding and screen-off.
- `acquireLatestImage()` always returns the newest frame; when the screen is static the projection keeps producing frames, so no "trigger a repaint first" dance is needed.
- `registerCallback(onStop)` marks the projection broken when the system reclaims it (user stops casting, another app requests projection) so the next capture fails fast with a clear message.
- `ScreenshotBridge` is a **synchronized** registry of request-id -> `CompletableDeferred<String>` so concurrent captures can't cross-wire results.
- If the OS fully kills the app process, Android requires re-authorization - true for every screen-sharing app; the foreground service normally keeps it alive.

### 11.3 Accessibility gestures (threads matter)

```kotlin
override suspend fun tap(x: Int, y: Int): String = withContext(Dispatchers.Main) {
    val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
    val gesture = GestureDescription.Builder()
        .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
        .build()
    if (dispatchGestureInternal(gesture)) "已点击 (${x}, ${y})" else "ERROR:手势分发失败"
}
```

- **Gestures must be dispatched on the main thread** — the engine runs on `Dispatchers.IO`, so each gesture method wraps itself in `withContext(Dispatchers.Main)`.
- A 5-second `withTimeoutOrNull` guards against callbacks that never fire (service rebound, system hiccup) — otherwise the tool call would hang the entire agent loop.
- `onDestroy` clears the static `instance` so stale references can't be used.

### 11.4 Notification listener caching

`BacNotificationListener` keeps the 30 most recent notifications in a deque. The cache is touched from the listener thread (`onNotificationPosted`) and read from tool-execution threads, so access is `synchronized` and snapshots are copied under the lock — a classic shared-mutable-state fix.

---

## 12. Skills (opencode-style)

### 12.1 SKILL.md format

```markdown
---
name: send-reminder
description: Set a reminder for the user
allowed-tools: set_alarm
---

1. Ask for the reminder content and time
2. Call set_alarm with the details
```

`SkillRepository` parses the YAML frontmatter and stores the body as instructions.

### 12.2 Loading skills as tools

`LoadSkillTool` is itself a tool: it lists loaded skills (name + description) so the model knows when to "call" one. `load_skill` resolves a skill name to its tool descriptions, letting the model execute the skill's steps using its declared tools.

### 12.3 Skill-defined tools & the action executor

Skills can declare their **own tools** with action types. `SkillActionExecutor` interprets `{param}` templates and dispatches to Android:

| Action type | Implementation |
|---|---|
| `alarm` | `AlarmManager` one-shot → notification |
| `notification` | post a notification |
| `clipboard` | read/write clipboard |
| `intent` | launch an activity via `Intent` |
| `settings` | open a system settings screen |
| `repeat` | repeating alarm (daily/weekly/hourly) with cancel via notification action |

Skill tools are registered in `ToolRegistry.registerSkillTools()` and unregistered on delete — the registry treats them as dynamic additions to the builtin list, advertised in `specsFor` for Build/Max modes.

### 12.4 Recording a skill from a conversation

The "save as skill" feature serializes the assistant message's `tool_calls` into a SKILL.md:

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

## 13. The automation engine

### 13.1 Schema

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

### 13.2 Time triggers — AlarmManager

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

- `setExactAndAllowWhileIdle` fires even in Doze (battery-saver deep sleep) — with `USE_EXACT_ALARM` declared, it works without user permission.
- The receiver (`AutomationAlarmReceiver`, statically registered in the manifest) **reschedules the next day's alarm after executing** — so a killed process can't silently kill the automation for good.
- The receiver calls `goAsync()` before launching coroutines so the process isn't reclaimed mid-execution.

### 13.3 Battery triggers

`ACTION_BATTERY_CHANGED` is a **sticky broadcast** — `registerReceiver` delivers the current level immediately, then every change. The scheduler:

```kotlin
val batteryReceiver = object : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val snapshot = synchronized(batteryThresholds) { batteryThresholds.toMap() }
        snapshot.forEach { (id, config) ->
            val (direction, threshold) = config
            val hit = if (direction == "low") level <= threshold else level >= threshold
            if (hit && running.putIfAbsent(id, true) == null) {   // ← mutex per automation
                scope.launch { try { executeAutomation(id) } finally { running.remove(id) } }
            }
        }
    }
}
```

### 13.4 Execution

```kotlin
suspend fun executeAutomation(id: Long) {
    runCatching {
        val automation = db.automationDao().getEnabled().firstOrNull { it.id == id } ?: return
        if (!daysMatch(automation.days)) return
        db.automationDao().setLastRun(id, System.currentTimeMillis())
        runCatching {
            val actions = json.decodeFromString<List<ActionSpec>>(automation.actionsJson)
            val results = actions.mapNotNull { spec ->
                val result = withTimeoutOrNull(60_000) {          // ← per-action timeout
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

### 13.5 Creating automations from chat

`CreateAutomationTool` validates the trigger format (HH:mm or low:/high:), then `AutomationBridge.create` inserts into Room and schedules. Users manage them in Settings → Automations (list, toggle, delete). Full round-trip verified on emulator: AI creates "睡前模式" → alarm fires at the exact minute → `set_volume` + `set_dnd` execute → completion notification.

---

## 14. Storage & state management

### 14.1 Room schema (v10)

```
conversations (id, title, provider, model, mode, agentId, pinned, archived, createdAt, updatedAt)
messages (id, conversationId FK, role, content, toolCallsJson, toolCallId, toolName,
          model, mode, status, usageInput, usageOutput, attachmentsJson,
          thinkingText, thinkingSignature, starred, createdAt)
repeat_tasks (id, title, content, interval, time, weekday, everyHours, requestCode, nextTriggerAt, createdAt)
automations  (id, name, triggerType, triggerValue, days, actionsJson, enabled, lastRunAt, createdAt)
memories     (id, conversationId, type [memory|snapshot], content, createdAt, updatedAt)
agents       (id, name, description, provider, baseUrl, apiKey[encrypted], model,
              temperature, maxTokens, reasoning, systemPrompt, isDefault, createdAt, updatedAt)
```

### 14.2 Explicit migrations — never destructive

```kotlin
val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations ADD COLUMN agentId INTEGER")
        db.execSQL("CREATE TABLE IF NOT EXISTS agents (…)")
    }
}
// …added to .addMigrations(MIGRATION_1_2 … MIGRATION_9_10)
```

Every schema version adds a hand-written migration so existing users upgrade in place. `fallbackToDestructiveMigration()` is deliberately absent — data loss is unacceptable for a chat app.

### 14.3 Flow-based observation

```kotlin
@Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id ASC")
fun observeForConversation(conversationId: Long): Flow<List<MessageEntity>>
```

Room re-emits whenever the table invalidates — the chat list, conversation list, stats, repeat tasks, and automations all use this pattern with `collectAsStateWithLifecycle(initialValue = …)`. This is what makes the "AI inserts a message → UI updates by itself" behavior work with zero manual refresh calls.

### 14.4 Entity ↔ domain mapping

`ChatRepository.messageToDomain()` and `domainToMessage()` translate between Room entities and `ChatMessage`. JSON fields (`toolCallsJson`, `attachmentsJson`) are decoded with `runCatching{…}.getOrDefault(emptyList())` — a corrupt field degrades to empty rather than crashing the conversation.

### 14.5 Settings & crypto

`SettingsRepository` wraps `SharedPreferences` with typed getters/setters. The API key is stored encrypted:

```kotlin
// KeyStoreCrypto: AES/GCM key inside AndroidKeyStore
val encrypted = encrypt(apiKey)      // random IV + ciphertext
// decrypt on read; wrong keystore (e.g. after backup restore) → key cleared, user re-enters
```

---

## 15. The UI layer

### 15.1 Screen structure

```
AppNav
 ├── ConversationListScreen        (search, pinned, list via Room Flow)
 └── ChatScreen
      ├── TopAppBar (title, agent + usage subtitle, mode selector, more menu → Select Agent)
      ├── LazyColumn (messages)
      │     └── MessageItem
      │           ├── UserBubble      (right-aligned, primaryContainer)
      │           └── AiBlock
      │                 ├── meta row (model · mode · time)
      │                 ├── markdown bubble (+ blinking cursor while streaming)
      │                 ├── ToolCallCard per call (compact row + Details expander)
      │                 ├── "执行步骤：已完成 n / m" progress while streaming tools
      │                 ├── HighlightedCodeCard per extracted code block
      │                 ├── link cards
      │                 └── ThinkingCard (collapsible reasoning)
      ├── ConfirmDialog (允许/拒绝/停止整个任务)
      ├── WelcomePanel (mode-aware example chips)
      └── InputBar (attach, mic, OutlinedTextField, send/stop)
```

### 15.2 Message rendering details

- **User vs AI alignment**: user bubble right-aligned (`Arrangement.End`), AI block left with an avatar row.
- **Tool cards**: compact by default - a single row with the tool name, a colored `StatusBadge` and a Details button; expanding reveals monospace args and the result (8-line ellipsis with expand-all).
- **Code blocks**: extracted from markdown and rendered separately with a dark background and a copy button — this avoids the markdown renderer choking on long/fenced code and gives a native-feeling experience.
- **Thinking**: `ThinkingCard` collapses long reasoning text with an expand/collapse row.
- **Markdown normalization** (`core/chat/MarkdownNormalizer.kt`): models emit markdown that CommonMark refuses to parse. Before rendering we repair it: full-width pipes `｜` -> `|`, tables without outer pipes are still parsed by our table component, missing spaces after `#`/`-`/`1.`/`>` are inserted, full-width stars are converted, and CJK-intraword emphasis (`中文**加粗**中文`, which CommonMark rejects) is padded with hair spaces so it renders. Each repair has unit tests.

### 15.3 Layout-driven bottom-follow scrolling

Two earlier approaches failed: `animateScrollToItem` on every delta jittered (the streaming item's height changes on every frame), and a "force-scroll every 80 ms" loop fought Markdown's asynchronous layout, producing visible jitter. The current implementation (v0.27.3) is **layout-driven**: after each layout pass, if the last item's bottom exceeds the viewport and following is active, scroll exactly once. No loop, no delay, no competition with the layout system.

```kotlin
// wasAtBottom flips false ONLY on real user drags. Using isScrollInProgress
// here was a trap: programmatic scrolls also set it, which silently
// disabled the follow.
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

// The follower: fires after every layout; scrolls once only when the last
// item actually overflows the viewport (content grew), then stays quiet
// until the next growth.
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

The insight: **don't poll, react**. `snapshotFlow { layoutInfo }` fires exactly when layout changed (i.e. when the streaming text grew). Checking "does the last item overflow?" turns each growth into at most one scroll. Programmatic scrolls don't re-trigger the follow path, and user drags are detected via `DragInteraction` rather than the ambiguous `isScrollInProgress`.

### 15.4 Streaming cursor

```kotlin
val blinkAlpha = rememberInfiniteTransition(label = "cursor").animateFloat(
    initialValue = 0.7f, targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse)
).value
// content + if (streaming) "▋" rendered with Modifier.alpha(blinkAlpha)
```

A blinking `▋` appended to the live text — the cheapest possible "typing" affordance.

### 15.5 The usage meter

`totalPromptTokens` (from the last message with usage) vs `ModelCatalog.entryFor(provider, model).contextWindow` renders as `· 1.2K tokens（3%）` in the top bar — giving users a live sense of remaining context.

### 15.6 Voice input & hands-free loop

- `SpeechInputHelper` (SpeechRecognizer) → partial results stream into the input box.
- `SpeechPlayer` (TextToSpeech) reads replies aloud.
- **Voice assistant mode**: after the AI finishes, it speaks the reply, auto-opens the mic, and the user's spoken answer is sent automatically — a complete hands-free loop implemented as a small state machine in the ViewModel.

---

## 16. Security design

| Threat | Mitigation |
|---|---|
| Prompt injection trying to run arbitrary shell | Only `run_shell` executes arbitrary commands, and only with Shizuku grant; everything else is bounded by tool semantics |
| Tool-argument injection | Each tool validates: `manage_app` package whitelist regex, `calculator` uses its own parser, paths/URLs validated per tool |
| Malicious URLs | `download_file` caps at 100 MB and rejects oversized `Content-Length`; `web_read`/`fetch_rss` bound response size via jsoup's default limit |
| API key theft | Key encrypted with Android Keystore; never logged; stored only in ciphertext |
| Background abuse | Every privileged capability requires an explicit, visible user grant; automations only run sequences the user asked the AI to create |
| DoS via long content | Streaming outputs bounded by read timeout + per-action timeouts + engine max 8 tool rounds |
| Concurrent execution races | Engine mutex (`runJob?.isActive`), per-automation mutex, synchronized caches/bridges |

---

## 17. Error handling matrix

| Layer | Failure | Result |
|---|---|---|
| Network | 401/403/404/429/timeout/connect | Mapped to friendly Chinese messages (`smartError`) |
| Streaming | Malformed chunk | Chunk skipped, stream continues |
| Streaming | Server stalls | 120s read timeout → `Failed` → UI error + partial persisted |
| Tool | Bad arguments | Tool returns error string → fed back to model → self-correction |
| Tool | Execution throws | `ToolCallStatus.FAILED` with message, shown as red badge |
| Tool | Hangs | 60s timeout in engine / automation |
| Confirmation | Dialog dropped | Fallback REJECTED (tryEmit check) |
| Confirmation | User timeout | 300s timeout → REJECTED |
| Stop | Mid-tool-round | PENDING calls → REJECTED + `已取消` tool messages (history stays valid) |
| DB | Corrupt JSON column | `runCatching` → empty default, conversation continues |
| Permission missing | e.g. no MediaProjection | Tool returns `ERROR:…` with how-to-grant instructions |

---

## 18. Engineering lessons from real bugs

These are all bugs found in code review and fixed in v0.20.1 — each with a transferable lesson:

1. **A retry that never retries.** OkHttp `Call` is single-use; retrying the same instance always throws. Fix: `call.clone()`. *Lesson: verify failure paths actually execute — dead retry logic is worse than no retry (it hides errors).*

2. **A poisoned conversation.** Stopping mid-tool-round left assistant `tool_calls` without matching `tool` responses → every future request 400'd. Fix: on stop, mark PENDING calls REJECTED and insert `已取消` results. *Lesson: maintain invariants of external protocols even in cancellation paths.*

3. **Dead code that killed a feature.** The battery receiver was implemented but never registered — battery automations silently did nothing. *Lesson: grep for call sites when adding "hooks"; a feature that can't run is worse than an error.*

4. **Concurrent state corruption.** `isRunning` was set inconsistently across entry points (send/edit/analyze-screen), allowing two agent pipelines to race on shared mutable state. Fix: single mutex at the engine-entry point. *Lesson: enforce concurrency invariants in one place, not in every caller.*

5. **Infinite hang.** `readTimeout(0)` + no tool timeout meant a stalled server or a hung gesture could block forever. Fix: 120s read timeout, 60s tool timeout, 5s gesture timeout, 60s automation step timeout. *Lesson: every suspension point needs a timeout — "it should respond" is not a timeout.*

6. **Thread affinity ignored.** Accessibility gestures silently fail off the main thread. *Lesson: read the API docs' threading requirements; wrap with `withContext(Main)` at the boundary.*

7. **Shell injection via tool arguments.** Fix: whitelist validation. *Lesson: any string that ends up in a shell/URL/path is an injection surface — validate at the boundary.*

8. **Resource leaks accumulate.** Bitmaps, streams, recognizers, connections. Fix: `try/finally`, `use`, explicit `recycle()`. *Lesson: cleanup must be structural (finally), not best-effort.*

9. **`cancelAll()` collateral damage.** Cancelling one repeat task wiped every notification. Fix: cancel by id. *Lesson: prefer the narrowest scope for side effects.*

10. **Ghost messages.** A dead branch in `persistStreamingPartial` created empty assistant messages on stop. Fix: rewrite the merge logic and test the stop path. *Lesson: dead code in hot paths is a bug waiting for the right trigger.*

11. **Alarm rescheduling after execution.** If execution hangs, the next occurrence never gets scheduled. Fix: schedule before/regardless of execution outcome (and timeout each step). *Lesson: recoverability must not depend on the happy path.*

12. **O(n²) string accumulation** on the main thread during long streams. *Lesson: appending immutable strings in a loop is quadratic — accumulate, then publish.*

---

## 19. Suggested study order

If you want to genuinely learn from this codebase:

1. **Kotlin coroutines & Flow** — `ChatEngine.run(): Flow<EngineEvent>`, `snapshotFlow` in scroll logic, `withTimeout`, `goAsync()` in receivers.
2. **Dependency inversion** — `ToolContext` bridges (`ScreenshotProvider`, `OcrProvider`, `AccessibilityBridge`, `AutomationBridge`) and the lambda-lazy `runner` injection that breaks the circular graph.
3. **Reactive UI** — one immutable `StateFlow`, `collectAsStateWithLifecycle`, `derivedStateOf`, the 10 Hz ticker decoupling event rate from render rate.
4. **Room** — entities, DAOs, Flow observation, explicit migrations v1→v10.
5. **Networking** — OkHttp + hand-rolled SSE parser, `call.clone()` retry, read-timeout discipline, per-vendor wire translation (OpenAI/Anthropic/Gemini).
6. **Android system integration** — `AlarmManager.setExactAndAllowWhileIdle`, sticky battery broadcasts, `MediaProjection` + foreground service lifecycle, `AccessibilityService` gesture dispatch (main thread + timeout), `NotificationListenerService`, Shizuku binder IPC.
7. **Agent design** — mode-based gate (Chat/Plan/Build/Max), prompt+enforcement defense in depth, the confirm loop via `SharedFlow` + `CompletableDeferred`, and the tool-transcript invariant (§7.4).
8. **Safety** — permission matrix with live status UI, input validation at boundaries, sandboxed evaluator, output truncation, concurrency mutexes.
9. **UX engineering** — layout-driven pinned scrolling (react to layout, don't poll), blinking cursor, compact tool cards with detail expansion, in-chat search, code-block extraction, mode-aware welcome panel.

Each of these topics maps to a concrete, working file in the repo — start at `ChatViewModel.send()` and trace through `ChatEngine.run()` to `OpenAiProvider.chatStream()`, then branch out into the tools and system services.

---

*Project: [BetterAIChat](https://github.com/Verlintas/BetterAIChat) — a native Android AI agent with 63 built-in tools, opencode-style Skills, per-conversation Agents, Shizuku/accessibility/MediaProjection capabilities, and a background automation engine. Built with Kotlin, Jetpack Compose, Room, OkHttp and kotlinx.serialization.*

---

## 20. Getting started — build, run, test, debug

### 20.1 Requirements

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 17 | Gradle toolchain targets JVM 17 |
| Android SDK | platform 36 + build-tools 36.0.0 | `compileSdk = 36`, `minSdk = 26` |
| Gradle | wrapper (8.14.3) | always use `./gradlew` |
| Device | Android 8.0+ (emulator fine) | Android 14/15 recommended for MediaProjection testing |

No API key is needed to build. To actually chat you need a key for some OpenAI-compatible endpoint (DeepSeek, OpenAI, a local gateway, …) configured through the in-app Agent wizard.

### 20.2 Build variants

The app has two product flavors:

| Flavor | Contents | Output |
| --- | --- | --- |
| `full` | everything, incl. ML Kit on-device OCR | `app/build/outputs/apk/full/debug/app-full-debug.apk` |
| `lite` | no OCR model (~10 MB smaller) | `app/build/outputs/apk/lite/debug/app-lite-debug.apk` |

```bash
./gradlew assembleFullDebug          # the usual dev build
./gradlew assembleLiteDebug          # lite variant
./gradlew assembleFullRelease        # release (unsigned; signing is manual, see ch. 26)
```

OCR is a flavor-specific implementation of one interface: `app/src/full/java/.../ScreenOcr.kt` vs `app/src/lite/java/.../ScreenOcr.kt`. `OcrProvider` in `:skills` is the seam.

### 20.3 Install & run

```bash
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
adb shell am start -n com.betteraichat/.MainActivity
```

First-run flow: Settings → Agents → New Agent → paste an API key → the wizard auto-detects the provider from the key prefix (`sk-ant-` → Claude, `AIza` → Gemini, otherwise OpenAI-compatible), fetches the model list and saves. No key at hand? Point the Base URL at any local mock (next section).

### 20.4 Tests

```bash
./gradlew :core:testDebugUnitTest :skills:testDebugUnitTest :providers:testDebugUnitTest
```

Current coverage is focused on pure logic that has historically broken: SSE parsing, markdown normalization (incl. CJK bold and pipe-less tables), calculator (incl. scientific functions), tool-argument merging/parsing, unit conversion, agent-key inference. UI and Android-framework code is verified on-device — the codebase deliberately keeps logic out of composables so it stays testable.

### 20.5 CI

`.github/workflows/ci.yml` runs on every push/PR: unit tests for the three library modules, then `assembleFullDebug` + `assembleLiteDebug`. If CI fails, the build or a test regressed — never merge red.

### 20.6 Debugging with a local mock server

The fastest way to exercise the full pipeline (streaming, tool calls, markdown edge cases) without burning real API quota is a tiny SSE server on the host. Minimal version:

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
        # scripted behavior keyed on the model name
        if 'tool' in req.get('model', ''):
            ev({"choices": [{"delta": {"tool_calls": [
                {"index": 0, "id": "c1", "function": {"name": "get_time", "arguments": "{}"}}]}}]})
        else:
            ev({"choices": [{"delta": {"content": "hello **world**"}}]})
        ev({"choices": [{"delta": {}, "finish_reason": "stop"}]})
        self.wfile.write(b'data: [DONE]\n\n'); self.wfile.flush()

HTTPServer(('0.0.0.0', 9999), H).serve_forever()
```

Then create an Agent with Base URL `http://10.0.2.2:9999/v1` (emulator → host loopback), any API key, and a model name containing `tool` to trigger the scripted tool call. Switching the model name switches scenarios — that is how every feature in this app was verified.

### 20.7 Emulator testing tips

- `adb shell input text` cannot type CJK; use English test strings or paste via the clipboard.
- `uiautomator dump` occasionally returns **stale snapshots** during scrolling/animations. When in doubt, take a screenshot (`adb shell screencap -p /sdcard/x.png`) and OCR it — this repo's authors were misled by stale dumps more than once.
- Grant permissions through the UI (Settings → Permissions); screenshot authorization in particular requires the system dialog ("Start now").
- MediaProjection state lives in the app process; `adb shell am force-stop` drops it — re-authorize after force-stops.

---

## 21. Agents — per-conversation configurations

### 21.1 The model

An **Agent** is a complete AI configuration stored in the `agents` table:

```
AgentEntity(id, name, description, provider, baseUrl, apiKey, model,
            temperature, maxTokens, reasoning, systemPrompt, isDefault,
            createdAt, updatedAt)
```

- `apiKey` is stored **encrypted** (KeyStoreCrypto). The repository writes `"enc:" + ciphertext` and decrypts on read (`AgentRepository.encryptIfPlain/decrypt`), so the DB never holds a plaintext key.
- `systemPrompt` is the optional custom prompt; empty means "use the mode prompt only".
- Exactly one agent is `isDefault` (the first one created, or the one explicitly marked). Deleting the default promotes the earliest remaining agent.

### 21.2 How a conversation resolves its config

`ChatViewModel.resolveConfig(state)`:

1. `state.agentId` (bound to the conversation) → load that agent → `AgentRepository.toConfig()`;
2. if unset/deleted → the default agent;
3. if there are no agents at all → legacy `SettingsRepository.configFor(provider)`.

Conversations store `agentId`; the model picker in the chat menu calls `updateAgent(id)` which persists the binding via `ChatRepository.updateMeta`. Compression, memory distillation and title generation all use the same `resolveConfig`, so a conversation behaves consistently everywhere.

### 21.3 The onboarding wizard

`ui/agents/AgentOnboarding.kt` — four steps:

1. **Key & provider** — paste the key; `inferProviderFromKey()` guesses the provider from the prefix; quick presets (DeepSeek / Qwen / Kimi / GLM / SiliconFlow) fill the Base URL; "Detect" runs `ModelProbe` (`GET {baseUrl}/models`) and lists the server's models.
2. **Model** — server-detected models, the curated catalog, or a manual id.
3. **Parameters** — temperature, max tokens, deep-reasoning toggle.
4. **Prompt** — default mode prompts or a custom system prompt → save.

### 21.4 Where the custom prompt goes

`ChatEngine.run()` prepends it to the mode prompt:

```kotlin
val sysContent = if (custom.isBlank()) systemPromptFor(mode)
                 else "$custom\n\n${systemPromptFor(mode)}"
```

Mode prompts encode safety behavior (e.g. "confirm before acting") and are never replaced — the custom prompt adds persona/domain context.

### 21.5 Legacy migration

`BetterAIChatApp.ensureDefaultAgentFromLegacySettings()` (runs once on a background scope at app start): if the agents table is empty and legacy settings contain an API key, it converts the old provider/baseUrl/model/temperature settings into a "默认 Agent" so existing users never see an empty picker.

---

## 22. The web-search pipeline

`web_search` is deliberately more than "call an engine". The whole chain lives in `skills/tools/WebSearchTool.kt`.

### 22.1 Fan-out and merge

- Six engines run **concurrently** (`async` each): Bing, Baidu, Brave, DuckDuckGo, Mojeek, 360.
- Merge rules: URL-normalized dedup, **max 2 results per domain** (diversity), **title-similarity dedup** (same article, different links).
- **Relevance ranking**: results are scored by query-term matches (title ×3, snippet ×1) and sorted — engine order alone surfaces off-topic pages (a query about "Android 16 release date" used to return the developer-tools homepage first).
- URLs are stripped of tracking params (`utm_*`, `spm`, `from`, …).

### 22.2 One-call bodies (`read_top`)

The default `read_top = 1` attaches the body of the first *successfully fetched* result:

- element-level extraction (`p, h1-h6, li, pre, blockquote` inside `article/main`), not `body.text()` — this is what strips author bars;
- `NOISE_RE` filters ~20 boilerplate patterns (views/favorites/二维码/红包…);
- adjacent duplicate paragraphs are dropped (source pages often repeat list content in `<p>`);
- **skip-on-failure**: blocked sites (e.g. baike.baidu.com → 403) are skipped and the next result is tried, up to 8 attempts, until `read_top` bodies are collected;
- per-body budget = 5000/read_top chars, and the engine truncation budget (6000) keeps bodies intact on the way to the model.

### 22.3 Multi-query, cache, refresh

- `query` accepts an **array** (≤3) — comparison questions complete in one round trip; results merge across queries.
- A 5-minute in-memory cache keyed by `query|max_results|read_top`; `refresh=true` bypasses it for time-sensitive follow-ups.
- Engine diagnostics: all-fail and partial-failure notes tell the model whether retrying is even useful.

### 22.4 Failure economics (why searches used to burn all rounds)

- `DeviceToolRunner.healArgs` **self-heals arguments** before execution: wrong-typed values converted, misspelled parameter names corrected (levenshtein ≤2), arrays preserved; the result is annotated with what was fixed.
- `parseToolArguments` tolerates double-encoded strings and prose-wrapped JSON (some gateways double-encode).
- `ChatEngine.runWithCircuitBreaker` denies a tool after 3 consecutive failures with explicit guidance.
- MAX mode allows up to 50 tool rounds; other modes 12.

---

## 23. Long-term memory

### 23.1 Storage

The `memories` table (v9) holds two kinds of rows, distinguished by `type`:

- `memory` — distilled facts ("User's name is …"), injected into every request;
- `snapshot` — the last 6 turns saved before a context compression, restorable via "Import recent chat".

### 23.2 Distillation

- **Automatic**: every 10 completed turns (`autoDistillCount` in `ChatViewModel`) a background distillation runs silently.
- **Manual**: ⋮ menu → Distill important info.
- The distillation prompt asks the model for *new* bullet points only, given the existing memories; results are filtered (length 3–200, no echo of the prompt) and inserted.

### 23.3 Injection

`injectMemory()` prepends a system message listing the memories to the history of every generation — this is why the assistant "remembers you" across sessions without any server-side state.

### 23.4 Interaction with compression

When usage crosses 85% of the model's context window, the app warns and auto-compresses after the turn: the last 6 turns are snapshotted, older history is replaced by a summary message, and the snapshot remains importable.

---

## 24. Extending the app — how to add things

### 24.1 Add a device tool (the most common task)

1. Create a class in `skills/src/main/java/com/betteraichat/skills/tools/`:

```kotlin
class MyTool : DeviceTool {
    override val name = "my_tool"                       // snake_case, unique
    override val description = "One sentence the MODEL reads to decide when to use it. State the argument formats."
    override val readOnly = false                       // true => allowed in Plan mode
    override val parameters = schemaOf(
        "text" to stringProp("what this argument means"),
        "count" to intProp("integer argument, default 3"),
        required = listOf("text")
    )
    override suspend fun execute(context: ToolContext, arguments: JsonObject): String {
        val text = arguments["text"]?.jsonPrimitive?.content ?: return "text 参数无效"
        // ... do the work ...
        return "result the model will read"
    }
}
```

2. Register it in `BetterAIChatApp.kt`'s tool list (`val tools: List<DeviceTool> = listOf(...)`).
3. That's it — the registry exposes it per mode (`readOnly` tools in Plan; all tools in Build/Max), the engine gates it, and arguments get self-healed automatically.
4. Return `"ERROR:…"` for failures with actionable text — the model reads it; `DeviceToolRunner` also treats `工具参数/工具执行`-prefixed strings as failures for the circuit breaker.
5. Add a unit test if the tool has non-trivial logic (`skills/src/test/...`). Tools that only wrap a system API don't need one.

### 24.2 Add a provider

1. Implement `ChatProvider` (`core/provider/ChatProvider.kt`) in `:providers` — you must emit `StreamEvent`s: `Delta`, `ThinkingDelta`, `ToolCallsDone`, `Usage`, `Done`, `Error`.
2. Add the id to `ProviderId`, register in `ProviderFactory`, and add base URL + curated models in `ModelCatalog`.
3. If the wire format is OpenAI-like, reuse `OpenAiProvider` patterns (SSE parsing, tool-call accumulation) rather than starting fresh.

### 24.3 Add a skill action type

`SkillActionExecutor.kt` maps action strings to Android intents. Add a branch to the `when` and document the YAML shape in the chapter 12 table. Keep actions parameterized via `{placeholders}` — never interpolate untrusted text into shell commands.

### 24.4 UI strings & i18n discipline

- All user-visible strings live in `app/src/main/res/values/strings.xml` (Chinese, default) and `values-en/strings.xml` (English). **Both files must stay in sync** — CI doesn't check this yet, so be careful in review.
- In composables use `stringResource(R.string.x)`; in ViewModels/services use `appContext.getString(R.string.x)`. Never hardcode user-visible text.
- Content that is *sent to the model* (system prompts, tool descriptions, distillation instructions) intentionally stays Chinese-only — it is not UI.

### 24.5 Database changes

- Bump `version` in `AppDatabase.kt`, add an explicit `MIGRATION_N_N+1` (never destructive), register it in `.addMigrations(...)`, and update chapter 14's schema listing.
- New columns on existing tables need a default (`ALTER TABLE … ADD COLUMN … NOT NULL DEFAULT …`) or must be nullable.

### 24.6 New settings

`SettingsRepository` for simple prefs (SharedPreferences) or a Room table if it's a list (like agents/automations). Settings UI lives in `SettingsScreen.kt`; follow the existing section pattern (each section is a composable taking `container/scope/snackbar`).

---

## 25. Troubleshooting & known platform limits

| Symptom | Cause | What to do |
| --- | --- | --- |
| "截屏授权已失效" right after a working capture | Pre-v0.27.4 behavior; Android 14+ tokens are single-use | Fixed by the persistent-projection rewrite (ch. 11.2). If you see it now, the system revoked the projection (another app cast, user stopped casting) |
| Capture works, then stops after the app was killed | MediaProjection grants are process-scoped by platform design | Re-authorize; the foreground service normally prevents this |
| Tool calls fail with "JsonLiteral is not a JsonObject" | Gateway double-encodes arguments | Fixed in v0.26.4 (`parseToolArguments`); if a new format appears, extend that function and add a test |
| AI loops on a broken tool call | Model keeps re-issuing invalid arguments | Circuit breaker denies after 3 consecutive failures; check the tool's `description` for unclear argument docs |
| Markdown renders as plain text | Model emits format CommonMark rejects (`###title`, pipe-less tables) | `MarkdownNormalizer` repairs the known cases; add new repairs there with tests |
| Search returns nothing | Network blocked / engines throttling | The tool reports engine diagnostics; verify with a real query on the device |
| CI red but local green | Different SDK/JDK or a stale test | Run the exact CI command locally; check the workflow file |
| `assembleRelease` output is unsigned | Signing is manual by design (keystore not in the repo) | See chapter 26 |

**Deliberate design limits** (do not "fix" without understanding):

- CHAT mode advertises **no tools** at all — smaller requests, no accidental tool attempts.
- Plan mode only exposes `readOnly` tools; the gate re-checks server-side (the model is never trusted).
- Shizuku `run_shell` is unrestricted by design; it is gated behind explicit authorization + BUILD confirmation/MAX mode.
- OCR file / file reading tools are allow-listed to app + public Downloads/Documents/Pictures directories.

---

## 26. Glossary & handover checklist

### 26.1 Glossary

| Term | Meaning |
| --- | --- |
| **Agent** | A saved AI configuration (provider+key+model+params+prompt); conversations bind to one |
| **Mode** | Chat / Plan / Build / Max — controls tool exposure and confirmation behavior |
| **Gate** | The server-side permission check before any tool executes (`ChatEngine.gate`) |
| **Skill** | A markdown-defined procedure (SKILL.md) loadable as tools via `load_skill` |
| **Distill** | Extracting durable user facts into the `memories` table |
| **Snapshot** | Last-6-turns backup saved before compression |
| **Circuit breaker** | Denies a tool after 3 consecutive failures in one run |
| **Self-healing** | Pre-execution argument repair in `DeviceToolRunner.healArgs` |
| **Pinned** | "Last item fully visible" — the condition that resumes auto-follow |
| **Flavor** | `full` (with OCR) vs `lite` build variants |

### 26.2 Handover checklist (everything outside the repo)

- **Keystore**: `betteraichat-release.keystore` + `keystore_pass.txt` live **outside git** (gitignored). Releases are signed manually:
  `zipalign -f 4 … && apksigner sign --ks … --ks-pass pass:$(cat keystore_pass.txt)`. Losing the keystore means users cannot update in place — back it up.
- **local.properties** with `sdk.dir` is machine-specific and gitignored.
- **GitHub**: releases are created from tags `vX.Y.Z`; attach both APKs (`full`, `lite`) and include SHA-256 sums in bilingual (zh+en) notes. Keep `gh release create … --notes-file` in mind — the default `--generate-notes` produces English-only text.
- **Versioning**: `versionCode` increments by 1 every release; `versionName` follows semver-ish `0.MINOR.PATCH` where MINOR = features, PATCH = fixes.
- **CI**: keep the workflow green; it is the only automated gate.
- **Publish order**: bump version → `assembleFullRelease`/`assembleLiteRelease` → zipalign + sign → `git tag vX.Y.Z && git push --tags` → `gh release create` with notes + both APKs.
- **Known third-party pin**: the markdown renderer is pinned to 0.41.0 (newer versions require compileSdk 37); table rendering is custom (`MarkdownTable`) precisely to avoid that upgrade.

---

## 27. Design philosophy & development approach

Chapters 1–26 describe *what* the code does. This chapter describes *why it is shaped this way* — the principles, the decisions with their trade-offs, and the working method that produced the codebase. If you are about to make a significant change, read this first; most "obvious improvements" were already considered and rejected for a reason.

### 27.1 Design principles

These are the rules the codebase actually follows. They are worth preserving in new code.

1. **Local-first, no cloud.** The app talks directly to the model provider the user configured. There is no backend, no telemetry, no account. API keys are encrypted with the Android Keystore and never leave the device except to the provider itself. This is a product decision, and it constrains architecture: everything (memory, snapshots, agents, automations) must live in local Room/SharedPreferences.

2. **The model is never trusted.** Model output is a *proposal*. Authority sits server-side (in the app): the mode gate decides which tools are even visible, `gate()` re-checks every call, read-only tools are enforced by the registry, and permissions are checked again at execution time. This is defence in depth — the system prompt also tells the model the rules, but nothing depends on it complying.

3. **Every tool result is text with a budget.** Tool results are what the model "sees", so they are written for a reader with finite attention: bounded length (truncation budgets), noise filtered (author bars, boilerplate), deduplicated, and ranked by relevance. Token economics is a first-class design concern — see `read_top` budgets and the 6000-char engine cap.

4. **Failures must be actionable — for the model.** Error strings are not just for humans. `"ERROR:缺少通讯录权限，请到 设置 → 权限 → 通讯录 中授权"` tells the model what to do next. A failure without a next step causes retry loops; a failure with one ends them.

5. **Deny by default.** CHAT mode advertises no tools at all. Unknown tools are denied. Plan mode only exposes `readOnly` tools. Permissions that are missing produce a clear message instead of a silent no-op. When in doubt, the codebase chooses the safer default and makes the user opt in.

6. **React, don't poll.** The streaming follow-scroll listens to layout changes and reacts; the file/screen watchers use flows; the SSE parser is push-driven. The one poll that survived (the streaming ticker) exists because deltas arrive faster than recomposition should run — and it is throttled. Every other "check every N ms" was removed as a bug source.

7. **Small, testable seams.** Logic lives outside composables and Android framework classes wherever possible: `ChatEngine` takes a `ToolRunner` interface, OCR is an `OcrProvider` interface (the full/lite flavor seam), providers implement `ChatProvider`. This is what makes 60+ unit tests possible without an emulator — and what lets `lite` swap out ML Kit with a one-line flavor difference.

8. **One source of truth per concern.** Agents own model configuration; `SettingsRepository` owns device-level preferences; the conversations table owns chat state; `resolveConfig()` is the single resolution chain. When two places could answer the same question, the codebase picks one owner and routes everything through it.

### 27.2 Key decisions and their trade-offs

| Decision | Why | What it costs |
| --- | --- | --- |
| **Hand-rolled `AppContainer` instead of Hilt** | No annotation processor (fast builds), full control of init order (see the legacy-agent migration ordering bug), tiny surface | Manual wiring; new dependencies must be threaded by hand |
| **Tools are built-in, not MCP** | Permission bridging is deeply Android-specific (accessibility, Shizuku, MediaProjection); in-process execution has no IPC latency and shares the app's permission state | Cannot reuse third-party MCP servers (yet) — a `ToolRegistry` adapter could add this without touching the engine |
| **Hand-written SSE parser** | Full control over partial events, retries, double-encoded payloads and vendor quirks; no heavyweight dependency; trivially unit-testable | Must maintain it against provider changes — hence the parser tests |
| **`Flow<EngineEvent>` for the agent loop** | Cancellation is structured (stop button = `job.cancel()`), the loop is testable without UI, and the ViewModel can filter/transform events freely | Slightly more ceremony than callbacks; discipline required around thread-safety (see `runToken` guards) |
| **Room with explicit migrations, never destructive** | Chat history is the user's data; silent loss is unacceptable for a chat app | Every schema change needs a hand-written migration and a chapter-14 update |
| **Custom table rendering + pinned markdown lib** | The upstream markdown library's newer versions require compileSdk 37; tables are the most common broken construct in model output, so a custom renderer was worth it | We own table rendering edge cases (outer-pipe-less tables, escapes) and their tests |
| **Argument self-healing *and* a circuit breaker** | Real models produce malformed arguments constantly (wrong types, misspelled keys, double-encoded JSON). Healing turns most failures into successes; the breaker stops the rest from burning rounds | Two layers to maintain; healing must never *hide* a real problem — fixes are always annotated in the result |
| **Layout-driven scrolling (third iteration)** | Poll-and-scroll fought async markdown layout; reacting to layout changes is the only approach that is both correct and jitter-free | Requires understanding `snapshotFlow(layoutInfo)` and `DragInteraction` semantics — documented in ch. 15.3 so nobody "simplifies" it back into a loop |
| **Manual release signing** | The keystore must never be in the repo or CI secrets for a hobby-scale project | Releases are manual; the checklist in ch. 26 exists because of this |

### 27.3 How features get built here

The workflow that produced every feature in this app, in order:

1. **Define the observable behavior** — what the user sees, what the model sees, what failure looks like. Not the implementation.
2. **Build the mock scenario first.** The local SSE server (ch. 20.6) is scripted by *model name* — `newtools`, `mtr`, `shot`… Each scenario encodes one behavior: a tool call sequence, a markdown edge case, a double-encoded argument. This makes the pipeline reproducible in seconds, without API quota.
3. **Implement against the mock**, verifying the full path: request construction → streaming → tool execution → persistence → UI.
4. **Verify on a real device/emulator** — mock success is necessary but not sufficient (platform behavior differs: MediaProjection, accessibility, storage scopes).
5. **Write unit tests for the logic that broke or could break** — parser edge cases, normalizers, calculators, argument healing. Not for coverage's sake; each test in this repo traces to a real failure or a real edge case.
6. **Run an audit pass periodically.** This codebase was hardened by three parallel audits (chat core / tools / storage+UI) that produced 45+ fixes. Audits work best as independent passes with fresh eyes, followed by batch fixes.
7. **Keep CI green, commit in small steps.** CI (tests + both flavors) is the only automated gate; history is linear and bisectable.

### 27.4 Anti-patterns this codebase avoids (and why)

- **IO or bitmap decoding in composables** — caused main-thread jank and ANR risk; all decoding is `withContext(Dispatchers.Default)` now.
- **`eval()`-style math** — the calculator is a hand-written recursive-descent parser; arbitrary code execution is never an option, even for "just math".
- **Hardcoded user-visible strings** — i18n discipline (both string files); the one place Chinese-only text remains is model-facing content, deliberately.
- **`currentTimeMillis()` as an identifier** — two reminders created in the same millisecond overwrote each other; `RequestCodes` uses an atomic generator now.
- **`fallbackToDestructiveMigration()`** — never. Data loss is not a migration strategy.
- **Trusting `isScrollInProgress` for user intent** — programmatic scrolls set it too; the follow logic was silently disabled by its own scrolling until `DragInteraction` was used instead.
- **`runCatching` around cancellation** — swallowing `CancellationException` leaks resources (speech recognizers) and breaks structured concurrency; resource-holding tools rethrow it explicitly.
- **Unbounded tool loops** — a model stuck on a broken call used to exhaust the round budget; now: self-heal → annotated retry → circuit breaker → actionable denial.

### 27.5 If you take over: the spirit of the project

BetterAIChat exists to prove that a single developer can ship a *real* agent — not a chat wrapper — on a phone: local-first, privacy-respecting, with genuine device capabilities. The engineering bias throughout is **make the model's job easy** (clear tools, clean results, actionable errors) and **keep the human in control** (modes, confirmations, explicit permissions). When choosing between a clever implementation and a debuggable one, this codebase picks debuggable every time — that is why the mock server, the audit passes, and this document exist.
