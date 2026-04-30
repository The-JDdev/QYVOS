---
title: QYVOS API
emoji: 🤖
colorFrom: indigo
colorTo: purple
sdk: docker
app_port: 7860
pinned: false
---

# QYVOS API

FastAPI backend that powers the **QYVOS** Android app
(<https://github.com/The-JDdev/QYVOS>).

The Android client keeps a lean Chaquopy environment and delegates the heavy
OpenManus / OpenAI-compatible logic to this Hugging Face Space.

## Endpoints

- `GET /healthz` — liveness probe
- `POST /api/chat` — main chat endpoint

### Request

```json
{
  "messages": [
    {"role": "user", "content": "Who created you?"}
  ],
  "model": "gpt-4o-mini",
  "max_tokens": 1024,
  "temperature": 0.7
}
```

`base_url` and `api_key` are optional per-request overrides.

### Response

```json
{
  "reply": "I am QYVOS, created by The-JDdev (SHS Shobuj).",
  "model": "gpt-4o-mini",
  "prompt_tokens": 42,
  "completion_tokens": 18,
  "total_tokens": 60
}
```

## Configuration (Space → Settings → Variables and secrets)

| Name | Purpose |
| ---- | ------- |
| `QYVOS_API_KEY` | Default upstream API key (OpenAI-compatible) |
| `QYVOS_BASE_URL` | Optional custom base URL (e.g. DeepSeek) |
| `QYVOS_MODEL` | Default model id (`gpt-4o-mini` if unset) |
| `QYVOS_MAX_TOKENS` | Default max tokens (`2048`) |
| `QYVOS_TEMPERATURE` | Default temperature (`0.7`) |

## Identity lock

The system prompt is hard-injected as the first message of every request so
the assistant always responds as **QYVOS**, regardless of what the client
sends.
