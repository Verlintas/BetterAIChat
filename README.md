# BetterAIChat

[简体中文](README.zh-CN.md) · English

> A native Android AI assistant that uses **your own API keys**. Chat with leading LLMs, and let the AI operate your phone — open apps, take screenshots, search the web, run shell commands, and follow reusable Skills.

Unlike mainstream AI apps, BetterAIChat is a **local-first agent**: your API keys are encrypted on-device, there is no cloud, and the AI can actually *do things* on your device through function calling.

---

## Screenshots

| Conversations | Chat | Settings |
| --- | --- | --- |
| <img src="docs/screenshots/screen-conversations.png" width="220" alt="Conversations"> | <img src="docs/screenshots/screen-chat.png" width="220" alt="Chat"> | <img src="docs/screenshots/screen-settings.png" width="220" alt="Settings"> |

---

## Features

### Chat & models
- **Multiple providers**: OpenAI-compatible (OpenAI, DeepSeek, Moonshot, Qwen, Ollama, any gateway), Anthropic Claude, Google Gemini
- **Streaming replies** with Markdown rendering, thinking-process display (reasoning stream), blinking cursor
- **Agent picker**: per-conversation Agent selection; built-in model catalog + server model fetch (`/v1/models`)
- **Context usage tracking** in the header (`117.3K (12%)`)
- **Context compression**: summarize long conversations to free the window
- **AI auto-titles** for conversations; prompt templates (translate / summarize / polish / write / explain code / brainstorm)

### Agents — one-tap setup, one per conversation
An **Agent** bundles a full configuration: provider + API key + model + temperature / max tokens / deep reasoning + optional custom system prompt.
- **One-tap onboarding (4 steps)**: paste your API key → provider auto-detected from the prefix (`sk-ant-` = Claude, `AIza` = Gemini), with quick presets for DeepSeek / Qwen / Kimi / GLM / SiliconFlow → models fetched automatically → pick model → tune temperature / max tokens → default or custom system prompt
- Pick a different Agent per conversation (⋮ → Select Agent); manage all Agents in Settings → Agents
- API keys are encrypted with the Android Keystore; legacy settings auto-migrate to a default Agent

### Modes (opencode-style)
| Mode | Behavior |
| --- | --- |
| `Chat` | Pure conversation, no tools |
| `Plan` | Read-only analysis, write tools forbidden |
| `Build` | Default — every tool execution asks for confirmation |
| `Max` | Autonomous multi-step tool calls + deep reasoning (max effort / thinking) |

### The AI can operate your device
| Tool | What it does |
| --- | --- |
| `open_app` | Launch any installed app |
| `send_notification` | Post notifications |
| `set_brightness` / `set_volume` / `set_screen_timeout` | Adjust system settings |
| `set_flashlight` | Torch control |
| `take_screenshot` | Screen capture |
| `device_info` | Device / battery / storage info |
| `set_clipboard` / `get_clipboard` | Clipboard read & write |
| `set_alarm` | One-shot reminders |
| `schedule_repeat` | Daily / weekly / hourly repeating reminders (manageable in Settings → Scheduled tasks) |
| `speak_text` | TTS read-aloud |
| `web_search` / `web_read` | Real-time multi-engine web search (Bing / Baidu / Brave / DuckDuckGo / Mojeek) and page reading |
| `open_settings` | Jump to system settings pages |
| `run_shell` | **Root-level shell execution via Shizuku** (pm, am, dumpsys, files…) |
| `get_time` | Current date / time / timezone |
| `media_control` | Play / pause / next / previous for active media sessions |
| `set_ringer_mode` | Switch ring / vibrate / silent mode |
| `share_text` | Share text or links via the system share sheet |
| `open_dialer` | Open the dialer pre-filled with a number |
| `vibrate` | Haptic vibration feedback |
| `network_status` | Connectivity, network type, WiFi name |
| `get_foreground_app` | Detect the currently active app |
| `screen_ocr` | **Read on-screen text** (screenshot + on-device Chinese/English OCR) |
| `download_file` | Download any file from a URL into the Downloads folder |
| `ua_type` / `ua_tap` / `ua_swipe` / `ua_press` | **UI automation via Accessibility**: type text, tap coordinates, swipe, press Home/Back/Recents/notification keys |
| `set_dnd` | Toggle Do Not Disturb mode |
| `manage_app` | **App management via Shizuku**: force-stop / disable / enable / clear data / uninstall |
| `write_document` | Save AI-generated content as .md/.txt/.html to Downloads/Documents |
| `set_wifi` | Toggle WiFi (via Shizuku) |
| `set_power_saver` | Toggle battery saver (via Shizuku) |
| `fetch_rss` | Parse any RSS/Atom feed (titles, links, timestamps) |
| `get_weather` | Weather for any city (current + 3-day forecast, no API key) |
| `calculator` | Safe math evaluation: `(12+5)*3`, `2^8`, `%`, decimals |
| `generate_qr` | Generate QR code images from text/links |
| `keep_screen_on` | Keep the screen awake for N seconds |
| `create_automation` | **Create automations**: time/battery triggers that run a sequence of tool actions unattended |
| `list_automations` / `delete_automation` | Manage automations |
| `read_notifications` | Read recent notifications (needs notification access) |
| `get_screen_state` | Screen on/off & lock state |
| `list_installed_apps` | App inventory with name/package/system flag, filter + limit |
| `ping_network` | Latency check for IP / domain / HTTP endpoints |
| `get_exchange_rate` | Live FX rates (no API key) |
| `get_location` | GPS / network location fix + accuracy |
| `transcribe_audio` | Record microphone audio and transcribe to text |
| `ocr_file` | OCR any image file (ML Kit, full build) |
| `screen_record` | Record the screen to MP4 (needs capture grant) |
| `send_email` | Compose an email via the system mail client |

### Screen analysis — the AI can *see* your screen
One tap → screenshot → vision model describes what's on your screen and gives operation advice. Works with any vision-capable model.

### Automations — set-and-forget tasks
The AI can create automations that run tool sequences automatically:
- **Time trigger**: e.g. "every day at 22:00 silence the phone + enable DND"
- **Battery trigger**: e.g. "when battery drops below 20%, remind me to charge"
- Executed in the background via AlarmManager (no confirmation needed), completion is reported via notification
- Manage / toggle / delete them in Settings → Automations

### Long-term memory
- The AI automatically distills key user information (name, preferences, agreements) into **persistent local memory**
- Memories are injected into every request, so the AI remembers you across sessions
- Auto-distills every 10 messages; manual trigger via ⋮ menu → "Distill important info (提炼重要信息)"
- Manage / delete memories in Settings → Memory (记忆)

### Seamless context continuation
- When usage reaches **85%** of the context window, the app warns and **auto-compresses** after the current turn
- Before compressing, the last 6 turns are saved as a snapshot — "Import recent chat" (导入最近对话) restores them as a message, so you continue without losing recent context

### Full UI automation — the AI can *drive* your phone
With two toggles in Settings (Accessibility + Usage access), the AI gains a complete automation loop:
1. `screen_ocr` reads the text on screen
2. `ua_tap` / `ua_swipe` / `ua_type` / `ua_press` perform the actions (tap, scroll, type, Home/Back/Recents)
3. `take_screenshot` / `screen_ocr` verify the result

Works on any app — no root, no Shizuku needed for this path.

### Skills (opencode-style)
- Import `SKILL.md` files with YAML frontmatter (`name`, `description`, `allowed-tools`)
- Skills can **define their own tools** with action types: `alarm`, `notification`, `clipboard`, `intent`, `settings` — including `{param}` templates
- **Record actions into skills**: after the AI completes a multi-step task, save the tool sequence as a reusable Skill
- Built-in action recorder, import/delete management

### Themes
8 accent color schemes (橙/红/粉/靛蓝/蓝/紫/绿/青), default orange matching the app icon, instant switching, vibrant light mode.

### Multi-engine web search
web_search merges **Bing / Baidu / Brave / DuckDuckGo / Mojeek** with URL dedup and 5-minute caching — reliable results even when one engine blocks scraping.

### Attachments & documents
- Images (up to 4, auto-compressed, EXIF-corrected) sent to vision models
- **PDF** (on-device Chinese OCR), **Word .docx**, **Excel .xlsx** (all sheets), text files up to 1MB

### Hands-free
- **Voice assistant mode**: AI speaks its reply, mic auto-opens, your spoken answer is sent automatically — a complete hands-free loop
- Voice input button, read-aloud for any message, auto read-aloud toggle

### Organization
- Multi-conversation management: pin, archive, search (title + full-text content), rename, swipe-to-delete, clear context
- Starred messages with a dedicated favorites page
- Export conversations as Markdown (share sheet)
- Long-press message actions: copy / speak / edit & resend / star / delete
- Usage stats (conversations, messages, tokens, tool calls)
- Share-into-chat (`ACTION_SEND`) and deep link `betteraichat://ask?text=…`

---

## Getting Started

### Download
Download the APK from [Releases](https://github.com/Verlintas/BetterAIChat/releases) — **in a browser**. The GitHub Android app corrupts large APK downloads; verify with the SHA-256 printed in each release, or use the smaller `-lite` build (~10 MB, no on-device OCR).

### Configure
1. Settings → Agents → **New Agent** (or, right in a chat: ⋮ → Select Agent → New Agent)
2. Step 1: paste your API key — provider + Base URL are detected automatically (`sk-ant-` → Claude, `AIza` → Gemini), or pick a preset (DeepSeek / Qwen / Kimi / GLM / SiliconFlow) and tap **Detect** to fetch your server's model list
3. Step 2: pick a model · Step 3: temperature / max tokens / deep reasoning · Step 4: default or custom system prompt → save
4. Start a conversation with that Agent. In Build/Max modes, ask the AI to do things: *"open the calculator"*, *"remind me to drink water at 9am every day"*, *"search today's news and summarize"*…
5. Want another persona? Create more Agents and switch per conversation via ⋮ → Select Agent

### Build from source
```bash
# JDK 17 + Android SDK (compileSdk 36)
./gradlew assembleFullDebug
# APK: app/build/outputs/apk/full/debug/app-full-debug.apk (lite variant: assembleLiteDebug)
```

---

## Permissions

| Permission | Purpose |
| --- | --- |
| Notifications | AI notifications & reminders |
| Microphone | Voice input & voice assistant mode |
| Camera | Flashlight control |
| Modify system settings | Brightness / screen timeout |
| Screen capture | Screen analysis (Android 15: choose "Entire screen" during consent) |
| Shizuku (optional) | Root-level shell execution |

All permissions are granted manually; tools return clear errors when unauthorized.

---

## Tech Stack

Kotlin · Jetpack Compose (Material 3) · OkHttp (SSE) · kotlinx.serialization · Room · Android Keystore · ML Kit (Chinese OCR)

```
:app         UI (conversations, chat, settings, stats, starred)
:core        models, engine, SSE, storage, DB, skills parsing
:providers   OpenAI-compatible / Anthropic / Gemini adapters
:skills      device tools + tool registry + action executor
```

### Learning from this project

New to Android agent development? Read **[docs/HOW_IT_WORKS.md](docs/HOW_IT_WORKS.md)** — a deep technical walkthrough covering the module architecture, the message pipeline, SSE streaming, the tool-calling agent loop, permission bridging, the automation engine, and UI rendering tricks (terminal-style scrolling, streaming cursor), with a suggested study order.

---

## Roadmap

- Multi-model comparison (one question, several models side by side)
- Home screen widget
- Agent marketplace / shareable Agents

---

## License

[MIT](LICENSE)
