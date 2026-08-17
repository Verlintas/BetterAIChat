# BetterAIChat

An Android app that lets you chat with leading LLMs using your **own API keys**, opencode-style. The AI can not only talk to you, but also perform simple device operations via **Function Calling + device tools (Skills)**.

## Features

- **Multiple providers**: OpenAI-compatible (OpenAI / DeepSeek / Moonshot / Qwen / Ollama, etc.), Anthropic Claude, Google Gemini
- **Streaming chat**: SSE streaming output, Markdown rendering, stop-generation control
- **Model selection**: built-in model catalog + custom model ID input + per-conversation model switching
- **Context usage tracking**: live token usage shown as `117.3K (12%)` in the conversation header, like opencode
- **Mode system (aligned with opencode)**:
  - `Chat` — pure conversation, no tool access
  - `Plan` — read-only analysis, write tools forbidden
  - `Build` — default mode, every tool execution requires user confirmation
  - `Max` — autonomous multi-step tool calls + deep reasoning (`reasoning_effort` / `thinking` injected per model capability)
- **Device tools**: open apps, send notifications, adjust brightness/volume, take screenshots (MediaProjection), query device info
- **Web search**: real-time web search (Bing with DuckDuckGo fallback) + web page content extraction — no API key required
- **Custom Skills**: import opencode-style `SKILL.md` files; the AI loads and follows them via the `load_skill` tool
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

Import a markdown file with YAML frontmatter (opencode `SKILL.md` format):

```markdown
---
name: reminder_helper
description: Helps set up reminder notifications for the user
allowed-tools:
  - send_notification
---
1. Ask the user what to be reminded of.
2. Use the send_notification tool to deliver the reminder.
```

The AI discovers skills through the `load_skill` tool and follows their instructions. `allowed-tools` optionally restricts which built-in tools the skill may use.

## Web Search

No API key needed. The AI can:

- `web_search` — search the web (Bing first, DuckDuckGo fallback) and get titles/URLs/snippets
- `web_read` — fetch a page and extract its main text

Works in Plan/Build/Max modes (read-only tools).

## Permissions

| Permission | Purpose |
| --- | --- |
| Notifications | AI sends reminder notifications |
| Modify system settings | AI adjusts screen brightness |
| Screen capture (MediaProjection) | AI takes screenshots |
| Foreground service | Screenshot capture |

All permissions are granted manually in Settings; tools simply return an error when unauthorized.

## Tech Stack

Kotlin · Jetpack Compose · Material 3 · OkHttp (SSE) · kotlinx.serialization · Room · Android Keystore

Modules: `:app` (UI) · `:core` (models/engine/storage) · `:providers` (adapters) · `:skills` (device tools)
