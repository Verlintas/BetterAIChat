# How BetterAIChat Works — The Complete Technical Walkthrough

This document explains the internals of **BetterAIChat** in exhaustive detail: module architecture, every layer of the request pipeline, the streaming protocol, the agent loop, the tool system, permission bridging, the automation engine, storage, UI rendering, security, and the engineering lessons learned from real bugs. It is written as a study guide for programmers who want to understand a real, working Android AI-agent application.

> Scope: ~45 built-in device tools, opencode-style Skills, Shizuku + Accessibility + MediaProjection integration, and a background automation engine.

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
│   │   │   │   ├── ChatScreen.kt           # Chat UI, input bar, dialogs, scroll logic (1011 lines)
│   │   │   │   ├── ChatViewModel.kt        # State machine + send/stop/compress/edit (950+ lines)
│   │   │   │   └── MessageViews.kt         # Bubble/tool-card/code-card composables (508 lines)
│   │   │   └── settings/SettingsScreen.kt  # All settings sections (1038+ lines)
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
│   │   ├── db/AppDatabase.kt               # Room entities + DAOs + migrations (v8)
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
        └── tools/                          # 45+ tool implementations
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

    val tools: List<DeviceTool> = listOf( /* 45+ tools */ )
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
    val reasoning: Boolean       // "deep thinking" mode for reasoning models
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

The catalog (~25 models across three providers) gives the UI sensible defaults (temperature, max tokens, context window for the usage meter, reasoning support) without hardcoding them in the screens. `contextWindow` also drives the token-usage indicator (`usageInput / contextWindow %`) shown in the chat top bar.

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
  │                          │                                        │        loop again (max 8 rounds)
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
    val usageInput: Long, val usageOutput: Long,
    // …
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
    val maxRounds = 8
    while (true) {
        if (toolRounds >= maxRounds) { emit(Failed("工具调用轮次超过 8 次")); return@flow }
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
| UI automation | `AccessibilityService` | enable in system settings | `ua_type`, `ua_tap`, `ua_swipe`, `ua_press` |
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

```kotlin
// ScreenshotManager
override suspend fun capture(): String {
    val data = resultData ?: return "ERROR:尚未授权截屏…"
    if (ScreenshotProjectionService.isBroken()) { clearProjection(); return "ERROR:授权已失效，请重新授权" }
    val deferred = ScreenshotBridge.registerCapture()
    context.startForegroundService(intent)          // service holds the projection
    return withTimeoutOrNull(60_000) { deferred.await() } ?: "ERROR:截屏超时"
}
```

Key mechanics:

- The **foreground service** (`foregroundServiceType="mediaProjection"`) owns the `MediaProjection` so the process isn't killed while capturing.
- `ImageReader` + `VirtualDisplay` capture a frame; the bitmap is written to `cacheDir/screenshots/`.
- A `registerCallback(onStop)` marks the projection broken when the system reclaims it (e.g. after a screen rotation or Android's one-session consent expiry) so the next capture fails fast with a clear message instead of hanging.
- `ScreenshotBridge` is a **synchronized** registry of `CompletableDeferred<String>` so concurrent captures can't cross-wire results.

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

### 14.1 Room schema (v8)

```
conversations (id, title, provider, model, mode, pinned, archived, createdAt, updatedAt)
messages (id, conversationId FK, role, content, toolCallsJson, toolCallId, toolName,
          model, mode, status, usageInput, usageOutput, attachmentsJson,
          thinkingText, thinkingSignature, starred, createdAt)
repeat_tasks (id, title, content, interval, time, weekday, everyHours, requestCode, nextTriggerAt, createdAt)
automations  (id, name, triggerType, triggerValue, days, actionsJson, enabled, lastRunAt, createdAt)
```

### 14.2 Explicit migrations — never destructive

```kotlin
val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS automations (…)")
    }
}
// …added to .addMigrations(MIGRATION_1_2 … MIGRATION_7_8)
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
      ├── TopAppBar (title, mode selector, model selector, usage %, more menu)
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

### 15.2 Message rendering details

- **User vs AI alignment**: user bubble right-aligned (`Arrangement.End`), AI block left with an avatar row.
- **Tool cards**: a `Surface` with a step badge ("第 N 步"), the tool name, monospace args (2-line ellipsis), a colored `StatusBadge`, and the result (6-line ellipsis).
- **Code blocks**: extracted from markdown and rendered separately with a dark background and a copy button — this avoids the markdown renderer choking on long/fenced code and gives a native-feeling experience.
- **Thinking**: `ThinkingCard` collapses long reasoning text with an expand/collapse row.

### 15.3 Terminal-style bottom-follow scrolling

The naive implementation — `animateScrollToItem` on every delta — jitters because the streaming item's height changes on every frame, constantly toggling `shouldAutoScroll`. The working solution:

```kotlin
val shouldAutoScroll by remember {
    derivedStateOf {
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        lastVisible >= info.totalItemsCount - 3
    }
}

var initialScrollDone by remember { mutableStateOf(false) }
var forceFollow by remember { mutableStateOf(false) }

LaunchedEffect(streaming, shouldAutoScroll, forceFollow, initialScrollDone) {
    if (state.messages.isEmpty()) return@LaunchedEffect
    if (initialScrollDone && !forceFollow && !shouldAutoScroll) return@LaunchedEffect
    var attempts = 0
    while (attempts++ < 60) {
        val info = listState.layoutInfo
        val total = info.totalItemsCount
        if (total > 0) {
            runCatching { listState.scrollToItem(total - 1, Int.MAX_VALUE) }
            val lastItem = info.visibleItemsInfo.lastOrNull { it.index == total - 1 }
            val bottom = lastItem?.let { it.offset + it.size } ?: -1
            if (lastItem != null && bottom >= info.viewportEndOffset - 20) break  // truly pinned
        }
        delay(120)
    }
    initialScrollDone = true
}
```

The insight: `scrollToItem(index, Int.MAX_VALUE)` is only reliable **after the item's real height is measured**. Markdown renders asynchronously, so you re-scroll every 120 ms until the layout *confirms* the last item's bottom is at the viewport bottom. Polling until a layout invariant holds — rather than assuming one scroll suffices — is the pattern to copy.

`forceFollow` is set on send and while streaming, and cleared 400 ms after streaming ends; `shouldAutoScroll` handles the user-scrolled-up case (don't yank them back).

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
4. **Room** — entities, DAOs, Flow observation, explicit migrations v1→v8.
5. **Networking** — OkHttp + hand-rolled SSE parser, `call.clone()` retry, read-timeout discipline, per-vendor wire translation (OpenAI/Anthropic/Gemini).
6. **Android system integration** — `AlarmManager.setExactAndAllowWhileIdle`, sticky battery broadcasts, `MediaProjection` + foreground service lifecycle, `AccessibilityService` gesture dispatch (main thread + timeout), `NotificationListenerService`, Shizuku binder IPC.
7. **Agent design** — mode-based gate (Chat/Plan/Build/Max), prompt+enforcement defense in depth, the confirm loop via `SharedFlow` + `CompletableDeferred`, and the tool-transcript invariant (§7.4).
8. **Safety** — permission matrix with live status UI, input validation at boundaries, sandboxed evaluator, output truncation, concurrency mutexes.
9. **UX engineering** — terminal-style pinned scrolling (poll-until-layout-invariant), blinking cursor, tool cards, code-block extraction, mode-aware welcome panel.

Each of these topics maps to a concrete, working file in the repo — start at `ChatViewModel.send()` and trace through `ChatEngine.run()` to `OpenAiProvider.chatStream()`, then branch out into the tools and system services.

---

*Project: [BetterAIChat](https://github.com/Verlintas/BetterAIChat) — a native Android AI agent with 45+ built-in tools, opencode-style Skills, Shizuku/accessibility/MediaProjection capabilities, and a background automation engine. Built with Kotlin, Jetpack Compose, Room, OkHttp and kotlinx.serialization.*
