<div align="center">

<img src="app/src/main/res/drawable/img_logo.png" alt="QYVOS Logo" width="120"/>

# QYVOS
### *An AI Agent Android App — Powered by the Full OpenManus Engine*

[![Android](https://img.shields.io/badge/Android-26%2B-brightgreen?logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-purple?logo=kotlin)](https://kotlinlang.org)
[![OpenManus](https://img.shields.io/badge/Engine-OpenManus-blue)](https://github.com/mannaandpoem/OpenManus)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

> **QYVOS** brings the full power of [OpenManus](https://github.com/mannaandpoem/OpenManus) — a general-purpose AI agent framework — natively to Android. It is not a chat wrapper. It runs the **complete OpenManus Python codebase** inside the app via Chaquopy, with a premium Android UI built around it.

</div>

---

## What is QYVOS?

QYVOS is a native Android application that embeds the entire OpenManus AI agent engine. It enables an AI agent to:

- 🌐 **Browse the web** autonomously using the built-in Android WebView
- 🐍 **Execute Python code** directly on your device
- 📁 **Read, write, and manage files** in Android scoped storage
- 🔍 **Search the internet** and extract information
- 🤖 **Plan and execute multi-step tasks** using a ReAct agent loop
- 🔐 **Authenticate with GitHub** via OAuth 2.0 and perform API actions
- 🛠️ **Call any OpenAI-compatible API** (DeepSeek, GPT-4o, Claude, Grok, Ollama, etc.)

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    QYVOS Android App                    │
│  ┌─────────────┐  ┌───────────────┐  ┌──────────────┐  │
│  │  Chat UI    │  │ God's Eye     │  │  Settings &  │  │
│  │  (Kotlin)   │  │ Execution     │  │  Dev Hub     │  │
│  │             │  │ Panel (Live)  │  │  (Kotlin)    │  │
│  └──────┬──────┘  └───────┬───────┘  └──────────────┘  │
│         │                 │                              │
│  ┌──────▼─────────────────▼──────────────────────────┐  │
│  │         OpenManusEngine.kt (Kotlin Bridge)         │  │
│  │         Calls Python via Chaquopy                  │  │
│  └──────────────────────┬─────────────────────────────┘  │
│                         │                               │
│  ┌──────────────────────▼─────────────────────────────┐  │
│  │      qyvos_bridge.py  (Python Bridge Module)       │  │
│  │  • Patches OpenManus config with Android values    │  │
│  │  • Replaces Playwright with AndroidBrowserShim     │  │
│  │  • Injects Android workspace paths                 │  │
│  │  • Streams logs to qyvos_log_callback.py queue     │  │
│  └──────────────────────┬─────────────────────────────┘  │
│                         │                               │
│  ┌──────────────────────▼─────────────────────────────┐  │
│  │           OpenManus Python Engine (Full)           │  │
│  │  app/agent/manus.py  — Manus Agent                │  │
│  │  app/agent/toolcall.py — ToolCall Agent            │  │
│  │  app/llm.py           — LLM Client (OpenAI API)   │  │
│  │  app/tool/browser_use_tool.py — Web Browser       │  │
│  │  app/tool/python_execute.py   — Python Runner     │  │
│  │  app/tool/str_replace_editor.py — File Editor     │  │
│  │  app/flow/planning.py — Planning Flow              │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

---

## Features

### 🤖 Full OpenManus Integration
- Complete, **unmodified** OpenManus Python codebase embedded via [Chaquopy](https://chaquo.com/chaquopy/)
- All tools available: `browser_use`, `python_execute`, `str_replace_editor`, `ask_human`, `terminate`
- Planning flow, MCP server support, multi-step ReAct loop — all working

### ⚡ God's Eye Real-Time Execution Panel
- **Live terminal log stream** showing every agent thought, step, and tool call
- Color-coded log levels: STEP, TOOL_CALL, TOOL_RESULT, THINKING, ERROR
- Expandable split view — chat on top, live logs below
- Real-time browser URL tracking when agent browses the web

### 🌐 Android WebView Browser Bridge
- Replaces Playwright with a native Android WebView
- Agent can navigate URLs, extract content, perform web searches
- Live URL tracking shown in chat header

### ⚙️ Advanced Settings
- Dynamic configuration: **Base URL**, **API Key**, **Model Name**
- Quick presets: DeepSeek R1, OpenAI GPT-4o, Claude 3.5, Grok-3, Groq, Ollama
- Token limit, temperature, and max steps control
- Live **API connection tester**
- All settings override OpenManus config at runtime (no file editing needed)

### 🔐 OAuth 2.0 + Token Vault
- **Secure GitHub OAuth** using Chrome Custom Tabs
- Deep link callback: `qyvos://oauth/callback`
- Access tokens stored in **EncryptedSharedPreferences** (AES-256-GCM)
- GitHub token passed to Python engine enabling API-based GitHub actions

### 📁 Android Scoped Storage
- All OpenManus workspace files written to Android internal storage
- Path: `Android/data/com.qyvos.app/files/workspace/`
- Logs saved to `Android/data/com.qyvos.app/files/logs/`
- No permission denied crashes

### 👨‍💻 Developer Hub
Premium about screen with developer info, donation options, and social links.

---

## Setup

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 26+ (API level 26 minimum)
- NDK installed (for Chaquopy)
- An OpenAI-compatible API key

### Build Instructions

1. **Clone the repository**
   ```bash
   git clone https://github.com/The-JDdev/QYVOS.git
   cd QYVOS
   ```

2. **Open in Android Studio**
   - File → Open → Select the `QYVOS` folder

3. **Sync Gradle**
   - Android Studio will automatically sync and download dependencies
   - Chaquopy will download Python 3.11 and install pip packages

4. **Build & Run**
   ```bash
   ./gradlew assembleDebug
   ```
   Or use the Run button in Android Studio.

5. **Configure on first launch**
   - On first launch, the Settings screen opens automatically
   - Enter your API Base URL and API Key
   - Select a model preset or enter a custom model name
   - Tap **Save Settings** to start chatting

---

## Supported AI Providers

| Provider | Base URL | Default Model |
|----------|----------|---------------|
| **DeepSeek** (default) | `https://api.deepseek.com/v1` | `deepseek-r1-0528` |
| OpenAI | `https://api.openai.com/v1` | `gpt-4o` |
| Anthropic | `https://api.anthropic.com/v1` | `claude-3-5-sonnet-20241022` |
| xAI Grok | `https://api.x.ai/v1` | `grok-3` |
| Groq | `https://api.groq.com/openai/v1` | `llama-3.3-70b-versatile` |
| Ollama (local) | `http://localhost:11434/v1` | `llama3.2` |
| Any OpenAI-compatible | Custom | Custom |

---

## Developer

<table>
<tr>
<td><strong>Name</strong></td>
<td>SHS Shobuj (JD Vijay)</td>
</tr>
<tr>
<td><strong>Telegram</strong></td>
<td><a href="https://t.me/aamoviesofficial">@aamoviesofficial</a></td>
</tr>
<tr>
<td><strong>Email</strong></td>
<td>The-JDdev.official@gmail.com</td>
</tr>
<tr>
<td><strong>Facebook</strong></td>
<td><a href="https://www.facebook.com/itsshsshobuj">facebook.com/itsshsshobuj</a></td>
</tr>
<tr>
<td><strong>GitHub</strong></td>
<td><a href="https://github.com/The-JDdev">github.com/The-JDdev</a></td>
</tr>
</table>

### Support / Donations
- **WebMoney WMZ:** `Z430378899900`
- **WebMoney WMT:** `T202226490170`
- **bKash:** Contact via Telegram or Email

---

## Credits

- [OpenManus](https://github.com/mannaandpoem/OpenManus) — The AI agent engine this app is built around
- [Chaquopy](https://chaquo.com/chaquopy/) — Python for Android
- [OkHttp](https://square.github.io/okhttp/) — HTTP client
- [Markwon](https://noties.io/Markwon/) — Markdown rendering
- [Lottie](https://airbnb.io/lottie/) — Animations

---

## License

```
MIT License — Copyright (c) 2026 SHS Shobuj (JD Vijay)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software to deal in the Software without restriction.
```
