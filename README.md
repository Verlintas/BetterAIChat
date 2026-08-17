# BetterAIChat

An Android app that lets you chat with leading LLMs using your **own API keys**, opencode-style. The AI can not only talk to you, but also perform simple device operations via **Function Calling + device tools (Skills)**.

## Features

- **Multiple providers**: OpenAI-compatible (OpenAI / DeepSeek / Moonshot / Qwen / Ollama, etc.), Anthropic Claude, Google Gemini
- **Streaming chat**: SSE streaming output, Markdown rendering, stop-generation control
- **Image & file understanding**: attach photos (up to 4, auto-compressed) for vision models to analyze, or attach text files (up to 200KB) whose contents are read into the conversation
- **Model selection**: built-in model catalog + custom model ID input + per-conversation model switching
- **Context usage tracking**: live token usage shown as `117.3K (12%)` in the conversation header, like opencode
- **Mode system (aligned with opencode)**:
  - `Chat` — pure conversation, no tool access
  - `Plan` — read-only analysis, write tools forbidden
  - `Build` — default mode, every tool execution requires user confirmation
  - `Max` — autonomous multi-step tool calls + deep reasoning (`reasoning_effort` / `thinking` injected per model capability)
- **Device tools**: open apps, send notifications, adjust brightness/volume, take screenshots (MediaProjection), query device info, clipboard read/write, scheduled reminders, flashlight, screen timeout, open system settings pages
- **Web search**: real-time web search (Bing with DuckDuckGo fallback) + web page content extraction — no API key required
- **Custom Skills**: import opencode-style `SKILL.md` files; skills can define their own tools (alarm / notification / clipboard / intent / settings actions) that the AI registers and calls on demand
- **Local-first security**: API keys encrypted with Android Keystore (AES-GCM); conversations stored in Room. No cloud sync, ever
- **Multi-conversation management**: create / switch / delete conversations

## Build

```bash
# Requires JDK 17 + Android SDK (compileSdk 36)
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Getting Started

1. Open the app → Settings
2. Pick a provider, enter your API key (and a custom Base URL for local Ollama etc.)
3. Choose a model and default mode, save
4. Start a new conversation and pick a mode (Build/Max lets the AI operate your device; Max executes tools autonomously — grant permission with care)

## Custom Skills

Import a markdown file with YAML frontmatter (opencode `SKILL.md` style). Skills can do more than give instructions — they can **define their own tools** that become callable by the AI once loaded.

### Skill with built-in tools

```markdown
---
name: reminder_helper
description: Set reminder notifications for the user
allowed-tools:
  - send_notification
tools:
  - name: set_reminder
    description: Set a reminder after N minutes
    parameters:
      type: object
      properties:
        minutes:
          type: integer
          description: Minutes from now
        text:
          type: string
          description: Reminder content
      required: [minutes, text]
    action:
      type: alarm
      config:
        title: "{text}"
        content: "{minutes} 分钟后提醒"
---
1. Ask the user what to be reminded of and when.
2. Call set_reminder with the user's minutes and text.
```

The AI discovers skills through the `load_skill` tool; loading a skill registers its custom tools for that session. `allowed-tools` restricts which built-in tools the skill may use.

### Available action types for skill tools

| type | config keys | effect |
| --- | --- | --- |
| `alarm` | `title`, `content` | Schedule a notification after `minutes`/`seconds` arg |
| `notification` | `title`, `content` | Send a notification immediately |
| `clipboard` | `text` | Write text to the clipboard |
| `intent` | `package`, `action`, `data` | Launch an Android intent |
| `settings` | `key` (brightness/volume/screen_timeout), `value` | Change a system setting |

Config values support `{param}` placeholders filled from tool arguments. Deleting a skill unregisters its tools.

## Web Search

No API key needed. The AI can:

- `web_search` — search the web (Bing first, DuckDuckGo fallback) and get titles/URLs/snippets
- `web_read` — fetch a page and extract its main text

Works in Plan/Build/Max modes (read-only tools).

## Permissions

| Permission | Purpose |
| --- | --- |
| Notifications | AI sends reminder notifications |
| Camera | AI controls the flashlight (torch) |
| Modify system settings | AI adjusts brightness / screen timeout |
| Screen capture (MediaProjection) | AI takes screenshots |
| Foreground service | Screenshot capture |

All permissions are granted manually in Settings; tools simply return an error when unauthorized.

## Tech Stack

Kotlin · Jetpack Compose · Material 3 · OkHttp (SSE) · kotlinx.serialization · Room · Android Keystore

Modules: `:app` (UI) · `:core` (models/engine/storage) · `:providers` (adapters) · `:skills` (device tools)
