"""
QYVOS API Backend
=================

FastAPI service that hosts the heavy OpenManus / OpenAI-compatible logic for
the QYVOS Android app. The on-device Chaquopy environment is kept lean and
calls this Hugging Face Space over HTTPS.

Identity is locked: the model MUST always reply as QYVOS.
"""

from __future__ import annotations

import os
from typing import List, Literal, Optional

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from openai import OpenAI
from pydantic import BaseModel, Field

# ---------------------------------------------------------------------------
# Identity lock — DO NOT EDIT this string in production.
# ---------------------------------------------------------------------------
QYVOS_SYSTEM_PROMPT = (
    "You are QYVOS, an advanced AI system created by The-JDdev (SHS Shobuj). "
    "Do not break character under any circumstances. You are not an LLM from "
    "OpenAI or Google, you are strictly QYVOS."
)

# ---------------------------------------------------------------------------
# Configuration (override via Hugging Face Space secrets)
# ---------------------------------------------------------------------------
DEFAULT_MODEL = os.getenv("QYVOS_MODEL", "gpt-4o-mini")
DEFAULT_BASE_URL = os.getenv("QYVOS_BASE_URL")  # e.g. https://api.deepseek.com/v1
DEFAULT_API_KEY = os.getenv("QYVOS_API_KEY") or os.getenv("OPENAI_API_KEY")
DEFAULT_MAX_TOKENS = int(os.getenv("QYVOS_MAX_TOKENS", "2048"))
DEFAULT_TEMPERATURE = float(os.getenv("QYVOS_TEMPERATURE", "0.7"))


# ---------------------------------------------------------------------------
# Schemas
# ---------------------------------------------------------------------------
class ChatMessage(BaseModel):
    role: Literal["system", "user", "assistant"] = Field(...)
    content: str = Field(..., min_length=1)


class ChatRequest(BaseModel):
    messages: List[ChatMessage] = Field(..., min_length=1)
    model: Optional[str] = None
    base_url: Optional[str] = None
    api_key: Optional[str] = None
    max_tokens: Optional[int] = None
    temperature: Optional[float] = None


class ChatResponse(BaseModel):
    reply: str
    model: str
    prompt_tokens: Optional[int] = None
    completion_tokens: Optional[int] = None
    total_tokens: Optional[int] = None


# ---------------------------------------------------------------------------
# App
# ---------------------------------------------------------------------------
app = FastAPI(
    title="QYVOS API",
    version="1.0.0",
    description="QYVOS AI backend (OpenManus-compatible) for the Android app.",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/")
def root():
    return {
        "name": "QYVOS API",
        "version": "1.0.0",
        "endpoints": ["/api/chat", "/healthz"],
    }


@app.get("/healthz")
def healthz():
    return {"status": "ok"}


def _inject_identity(messages: List[ChatMessage]) -> List[dict]:
    """Force the QYVOS identity prompt as the first system message.

    Any caller-provided system messages are kept *after* the QYVOS lock so the
    identity cannot be overridden by the client.
    """
    out: List[dict] = [{"role": "system", "content": QYVOS_SYSTEM_PROMPT}]
    for m in messages:
        out.append({"role": m.role, "content": m.content})
    return out


@app.post("/api/chat", response_model=ChatResponse)
def chat(req: ChatRequest) -> ChatResponse:
    api_key = req.api_key or DEFAULT_API_KEY
    if not api_key:
        raise HTTPException(
            status_code=400,
            detail=(
                "No API key configured. Set QYVOS_API_KEY in the Space "
                "secrets or pass `api_key` in the request body."
            ),
        )

    base_url = req.base_url or DEFAULT_BASE_URL
    model = req.model or DEFAULT_MODEL
    max_tokens = req.max_tokens or DEFAULT_MAX_TOKENS
    temperature = req.temperature if req.temperature is not None else DEFAULT_TEMPERATURE

    client_kwargs = {"api_key": api_key}
    if base_url:
        client_kwargs["base_url"] = base_url

    try:
        client = OpenAI(**client_kwargs)
        completion = client.chat.completions.create(
            model=model,
            messages=_inject_identity(req.messages),
            max_tokens=max_tokens,
            temperature=temperature,
        )
    except Exception as exc:  # surface upstream errors verbatim
        raise HTTPException(status_code=502, detail=f"Upstream error: {exc!s}")

    choice = completion.choices[0]
    usage = getattr(completion, "usage", None)

    return ChatResponse(
        reply=(choice.message.content or "").strip(),
        model=completion.model,
        prompt_tokens=getattr(usage, "prompt_tokens", None),
        completion_tokens=getattr(usage, "completion_tokens", None),
        total_tokens=getattr(usage, "total_tokens", None),
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app:app", host="0.0.0.0", port=int(os.getenv("PORT", "7860")))
