# How BetterAIChat Works — A Technical Walkthrough

This document explains the internals of BetterAIChat in depth, written for programmers who want to learn from a real, working Android AI-agent application. It covers the module architecture, the request/response pipeline, streaming, the tool-calling loop, device-permission bridging, the automation engine, and the UI rendering tricks.

---

## 1. High-level architecture

The project is split into four Gradle modules:

```
┌─────────────────────────────────────────────────────────────┐
│  :app   (Android application)                                │
│  MainActivity, ChatScreen, SettingsScreen, ChatViewModel     │
│  ScreenshotManager, AccessibilityService, AutomationScheduler│
│  NotificationListener, AppContainer (dependency wiring)      │
└──────────────┬──────────────────────────────┬────────────────┘
               │ depends on                   │ depends on
               ▼                              ▼
┌─────────────────────────┐     ┌──────────────────────────────┐
│  :core   (pure logic)   │     │  :providers (LLM adapters)   │
│  ChatEngine (agent loop)│     │  OpenAI / Anthropic / Gemini │
│  ChatRepository, Room DB│     │  SSE parsing, retry, timeout │
│  SseParser               │     └──────────────────────────────┘
└──────────────┬──────────┘
               │ depends on
               ▼
┌─────────────────────────┐
│  :skills   (device tools)│
│  DeviceTool interface    │
│  45+ tools, ShizukuExec, │
│  SkillActionExecutor     │
└─────────────────────────┘
```

- `:app` is the only module that touches Android framework components tied to a UI (Activities, Services, Compose).
- `:core` holds the agent brain — the engine loop that decides *what to do next* — and the database. It has no UI.
- `:providers` adapts each vendor's chat API (OpenAI-compatible, Anthropic, Gemini) into one uniform `Flow<StreamEvent>`.
- `:skills` implements the `DeviceTool` interface — the actual abilities the AI can use. It stays UI-free by receiving *bridges* (interfaces) for system capabilities instead of referencing Android components directly.

### Dependency direction (important design lesson)

`:skills` never imports classes from `:app`. When a tool needs to take a screenshot or run an accessibility gesture, it calls an interface defined in `:skills` (`ScreenshotProvider`, `OcrProvider`, `AccessibilityBridge`) that `:app` implements and injects. This is a classic **dependency inversion** pattern: the tool layer defines the contract, the app layer provides the implementation. It keeps modules testable and prevents circular dependencies.

---

## 2. The journey of a single message

Everything starts when the user taps send. This is the full data flow:

```
User taps ⏎
   │
   ▼
ChatScreen (Compose) ── onSend() ──▶ ChatViewModel.send()
   │
   ▼
sendWithContent():
   1. repository.insertMessage(USER message)   ──▶ Room DB
   2. state.sendTick++                          (triggers UI scroll-to-bottom)
   3. runGeneration(cid)                        (starts the agent loop)
   │
   ▼
ChatEngine.run(history, config, mode) : Flow<EngineEvent>
   │  loops until no tool calls remain
   ├─▶ provider.chatStream(messages, config, tools) : Flow<StreamEvent>
   │     OkHttp POST {baseUrl}/chat/completions  (stream=true)
   │     ──▶ SseParser parses `data:` lines ──▶ StreamEvent.Delta / Done
   │
   ├─▶ emits EngineEvent.Delta(text)  ──▶ ViewModel appends to streaming message
   ├─▶ emits EngineEvent.AssistantFinished(message) ──▶ persisted to Room
   ├─▶ if message contains tool_calls:
   │     for each call: gate() → confirm dialog → ToolRunner.run()
   │     append tool results to history, loop again
   └─▶ emits EngineEvent.Completed
   │
   ▼
ChatViewModel refreshes UI state ──▶ Compose recomposes ──▶ Markdown rendered
```

Key types:

| Type | Module | Meaning |
|---|---|---|
| `StreamEvent` | `:core` | Raw stream events from the API (`Delta`, `ToolCallsDone`, `Usage`, `Done`, `Error`) |
| `EngineEvent` | `:core` | Higher-level agent events (`Delta`, `AssistantFinished`, `ToolCallStarted/Finished`, `Failed`, `Completed`) |
| `UiMessage` | `:app` | UI model combining DB messages + the live streaming message |

The engine produces a `Flow<EngineEvent>` instead of a callback — this is **reactive design**: the ViewModel collects it, the UI observes `StateFlow` via `collectAsStateWithLifecycle`, and every layer stays decoupled.

---

## 3. Streaming & SSE parsing

APIs stream responses as Server-Sent Events. Each line looks like:

```
data: {"choices":[{"delta":{"content":"你好"}}]}

data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

data: [DONE]
```

`SseParser.parse()` (`:core`) reads the OkHttp `ResponseBody` line by line on a background dispatcher:

```kotlin
while (!source.exhausted()) {
    val line = source.readUtf8Line() ?: break
    when {
        line.isBlank() -> if (dataBuffer.isNotEmpty()) {
            val shouldContinue = onEvent(eventName, dataBuffer.toString())
            dataBuffer.clear()
            if (!shouldContinue) break     // [DONE] stops the flow
        }
        line.startsWith("data:") -> dataBuffer.append(line.removePrefix("data:").trimStart())
        line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
    }
}
```

Two subtle details worth learning:

1. **Blank line = event boundary.** SSE uses `\n\n` to delimit events, so the parser buffers `data:` lines until the blank line.
2. **Return value controls cancellation.** `onEvent` returns `Boolean`; returning `false` breaks the read loop — this is how `[DONE]` cleanly terminates without exceptions.

The provider (`:providers/openai/OpenAiProvider.kt`) decodes each chunk with kotlinx.serialization and maps it into `StreamEvent`s:

```kotlin
SseParser.parse(body) { _, data ->
    if (data == "[DONE]") { emit(StreamEvent.Done); return@parse false }
    val chunk = json.decodeFromString(OpenAiChunk.serializer(), data)
    choice.delta?.content?.let { emit(StreamEvent.Delta(it)) }
    choice.delta?.toolCalls?.forEach { /* accumulate into ToolAcc */ }
    true
}
```

Retry logic is worth studying too: the provider retries 429/502/503/504 once with a 500ms backoff, and — the subtle bug we fixed — **each retry must use `call.clone()`** because an OkHttp `Call` can only be executed once. A 120s read timeout prevents a dead server from hanging the app forever.

---

## 4. The tool-calling loop (the "agent" part)

This is the heart of the app. The engine (`:core/engine/ChatEngine.kt`) runs a loop:

```
┌──────────────────────────────┐
│ build request from history   │
└──────────────┬───────────────┘
               ▼
┌──────────────────────────────┐     tool_calls?
│ provider.chatStream()        │ ──────────────────────┐
└──────────────┬───────────────┘                       │ no
               ▼                                       ▼
      emit AssistantFinished ───────────────▶ break loop ──▶ emit Completed
               │
               │ yes
               ▼
      for each tool_call:
        gate(mode, spec, call)
          ├─ CHAT  mode ──▶ DENIED ("Chat 模式不执行设备工具")
          ├─ PLAN  mode + non-readonly tool ──▶ DENIED
          ├─ BUILD mode ──▶ NeedsConfirm  (show dialog)
          └─ MAX   mode ──▶ Allow         (autonomous)
        │
        ▼
      ToolRunner.run(name, arguments)   (60s timeout)
        │
        ▼
      append assistant + tool result to history ──▶ loop again (max 8 rounds)
```

The **gate** is a safety layer that maps the current `AppMode` to a decision:

| Mode | Behavior |
|---|---|
| `Chat` | No tools at all |
| `Plan` | Read-only tools only (`device_info`, `get_weather`, `calculator`…) |
| `Build` | Every tool asks the user first (confirm dialog) |
| `Max` | Tools run autonomously; only a summary is shown |

### Confirmation flow (shared-flow + deferred)

`ChatEngine` and `ChatScreen` are decoupled, so confirmation uses a `MutableSharedFlow<ToolCall>` plus a per-call `CompletableDeferred<Boolean>`:

```kotlin
// Engine side
val deferred = CompletableDeferred<Boolean>()
pendingConfirms[call.id] = deferred
confirmRequestsFlow.tryEmit(call)              // ▶ dialog appears in UI
val allow = withTimeout(300_000) { deferred.await() }   // wait for user

// UI side (ChatScreen)
state.confirmRequest?.let { req ->
    AlertDialog(... 允许/拒绝/停止整个任务 ...)
}
vm.respondConfirm(true)  // ──▶ engine.respond(call.id, true) ──▶ deferred.complete(true)
```

This is an elegant pattern: the engine *suspends* waiting for the user while the UI *emits* the question through a flow — no callbacks, no coupling. A "stop entire task" button cancels the whole `runJob`, and the ViewModel then marks any still-pending tool calls as REJECTED with a `已取消` (cancelled) result so the conversation history stays valid for the API (OpenAI/Anthropic require every `tool_call` to have a matching `tool` response, otherwise the next request returns HTTP 400).

---

## 5. Device tools & permission bridges

Tools live in `:skills/tools/` and implement the `DeviceTool` interface:

```kotlin
interface DeviceTool {
    val name: String
    val description: String        // ← this is fed to the LLM!
    val readOnly: Boolean
    val parameters: JsonObject     // ← JSON Schema for the LLM
    suspend fun execute(context: ToolContext, arguments: JsonObject): String
}
```

`description` and `parameters` matter more than you might think: they are serialized into the tool-call request, so the LLM decides *which* tool to call and *with which arguments* based purely on these strings. Writing precise descriptions is prompt engineering for tools.

### ToolContext — dependency injection for tools

`ToolContext` carries everything a tool may need, all injected by `:app`:

```kotlin
data class ToolContext(
    val appContext: Context,
    val screenshotProvider: ScreenshotProvider,   // capture screen
    val ocrProvider: OcrProvider?,                // screen OCR (full build)
    val accessibility: AccessibilityBridge?       // type/tap/swipe/press
)
```

### Permission matrix — how the AI "touches" your phone

| Capability | Bridge | User setup | Example tool |
|---|---|---|---|
| Screenshot / screen analysis | `MediaProjection` | one-time auth in Settings | `take_screenshot`, `screen_ocr` |
| UI automation (tap/type/swipe/press) | `AccessibilityService` | enable in system settings | `ua_tap`, `ua_type`, `ua_press` |
| Shell commands (root-level) | Shizuku | install + grant Shizuku | `run_shell`, `manage_app`, `set_wifi` |
| Read notifications | `NotificationListenerService` | notification access | `read_notifications` |
| Foreground app detection | `UsageStatsManager` | usage access | `get_foreground_app` |
| Volume/brightness/DND | System APIs | — (DND needs access on <15) | `set_volume`, `set_dnd` |

This layered approach is a great study case: **every privileged capability is behind an explicit user-visible grant** (Settings screen shows live status for each), and tools degrade gracefully with a readable error when the grant is missing.

---

## 6. Skills (opencode-style)

Beyond the 45 built-in tools, users can import `SKILL.md` files (same format as opencode). A skill:

```markdown
---
name: send-reminder
description: Set a reminder for the user
allowed-tools: set_alarm
---

1. Ask for the reminder content and time
2. Call set_alarm with the details
```

- `LoadSkillTool` loads skill descriptions into the tool list so the LLM knows when to invoke them.
- Skills can **define their own tools** with action types (`alarm`, `notification`, `clipboard`, `intent`, `settings`) executed by `SkillActionExecutor`.
- Any multi-step tool sequence can be recorded from a conversation ("save as skill") and reused later — the app serializes the assistant message's `tool_calls` into a SKILL.md.

---

## 7. The automation engine

Automations let the AI schedule unattended tool sequences. Two trigger types:

| Trigger | Implementation |
|---|---|
| `time` ("22:00" daily) | `AlarmManager.setExactAndAllowWhileIdle` + static `BroadcastReceiver` → reschedules next day after running |
| `battery` ("low:20") | `BroadcastReceiver` on `ACTION_BATTERY_CHANGED` (sticky broadcast) → checks threshold |

The scheduler:

```
create_automation(tool) ──▶ Room table `automations`
   │
   ├─ time:    scheduleTime() → AlarmManager (exact, allow-while-idle)
   └─ battery: registerBattery() → threshold map + battery receiver
   │
   ▼  trigger fires
executeAutomation(id):
   1. load actions JSON: [{"tool":"set_volume","args":{"percent":0}}, ...]
   2. for each action: runner.run(tool, args)   (60s timeout each, mutex per id)
   3. notify user with per-action results
   4. (time type) re-schedule the next occurrence
```

Stability lessons encoded here (all from real bugs we fixed):

- **Battery receiver must actually be registered** (it was dead code once — the threshold map was filled but nobody listened).
- **Alarm receivers should call `goAsync()`** before launching coroutines, or the process may be killed mid-execution.
- **Concurrent triggers need a mutex** (`ConcurrentHashMap.putIfAbsent`) so one automation doesn't run twice.
- **Each action needs a timeout** so a hanging tool can't block the whole sequence.
- **Don't call `cancelAll()`** on the notification manager — it wipes unrelated notifications; cancel by id instead.

---

## 8. Storage & state

**Room database** (`:core/db/AppDatabase.kt`, currently schema v8):

```
conversations ─ 1:N ─ messages (content, toolCallsJson, attachmentsJson,
                                thinkingText, usageInput/Output, starred)
repeat_tasks   (repeating reminders)
automations    (time/battery triggers + actionsJson)
```

Notes for learners:

- `messages.toolCallsJson` stores the full tool-call state as JSON (status: PENDING/RUNNING/DONE/FAILED/REJECTED/DENIED) — the UI renders this as step cards.
- The app observes the DB with **Room Flow** (`observeMessages`) and merges it with the in-flight streaming message: `state.messages = dbMessages + listOfNotNull(streaming)`. The streaming message has `id = -1` as a sentinel.
- Schema changes use explicit `Migration` objects (v1→v8) — never `fallbackToDestructiveMigration`.

**UI state** lives in `ChatUiState` (a single immutable data class in a `MutableStateFlow`). Every mutation goes through `_state.update { it.copy(...) }` — atomic, single-source-of-truth, and trivially testable.

---

## 9. UI rendering details worth stealing

### Terminal-style bottom-follow scrolling

The chat list pins itself to the bottom like a terminal while streaming. The naive approach (`animateScrollToItem` on every delta) causes jitter because the streaming item's height changes every frame. The working approach:

```kotlin
LaunchedEffect(streaming, shouldAutoScroll, forceFollow, initialScrollDone) {
    // poll-scroll until the last item's bottom reaches the viewport bottom
    while (attempts++ < 60) {
        listState.scrollToItem(total - 1, Int.MAX_VALUE)
        if (lastItem.bottom >= viewportBottom - 20) break   // actually pinned
        delay(120)
    }
}
```

The insight: `scrollToItem(index, Int.MAX_VALUE)` is only reliable **after** the item's height is measured — Markdown rendering is async, so you must re-scroll until the layout confirms it's pinned.

### Streaming cursor

A blinking `▋` is appended to the content while streaming, with an infinite alpha animation — the simplest possible "typing" affordance.

### Long-reply handling

Code blocks are extracted from markdown and rendered in a separate syntax-highlighted card (so the markdown renderer doesn't choke on them), with a copy button.

---

## 10. What to study, in order

If you want to learn from this codebase:

1. **Kotlin coroutines & Flow**: `ChatEngine`'s `Flow<EngineEvent>` pipeline, `snapshotFlow` in the scroll logic, `withTimeout` on tool execution.
2. **Dependency inversion**: `ToolContext` bridges + `ToolRunner`/`ScreenshotProvider`/`AccessibilityBridge` interfaces — the module boundary design.
3. **Reactive UI**: single `StateFlow` + `collectAsStateWithLifecycle` + derived state (`shouldAutoScroll`).
4. **Room**: entities, DAOs, Flow-based observation, explicit migrations.
5. **OkHttp + SSE**: hand-rolled streaming parser, `call.clone()` retry, read-timeout discipline.
6. **Android system integration**: `AlarmManager`, `BroadcastReceiver.goAsync()`, `MediaProjection` lifecycle, `AccessibilityService` gesture dispatch (main-thread only!), `NotificationListenerService`, Shizuku binder calls.
7. **Agent design**: the mode-based gate (Chat/Plan/Build/Max), the confirm loop via `SharedFlow` + `CompletableDeferred`, tool-result validity (never leave dangling tool calls).
8. **Safety**: prompt-level restrictions (system prompts per mode), permission gating per capability, input validation (the `manage_app` package whitelist), and a sandboxed math evaluator (`calculator` has its own parser instead of `eval`).

---

*Project: [BetterAIChat](https://github.com/Verlintas/BetterAIChat) · Native Android AI agent — 45 built-in tools, opencode-style skills, Shizuku + accessibility + MediaProjection capabilities, and a full automation engine.*
