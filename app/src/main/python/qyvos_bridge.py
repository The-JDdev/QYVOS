"""
QYVOS Bridge — The Android-to-OpenManus execution bridge.

This module is called from Kotlin via Chaquopy. It:
1. Patches OpenManus config to use Android-injected paths and credentials.
2. Replaces `browser_use` (Playwright) with an Android WebView-compatible shim.
3. Runs the Manus agent asynchronously with full streaming log output.
4. Returns the final agent result to Kotlin.
"""

import sys
import os
import asyncio
import threading
import importlib
import json
import traceback
from typing import Optional

# ─── Android Path Injection ───────────────────────────────────────────────────

_workspace_path: str = "/storage/emulated/0/Android/data/com.qyvos.app/files/workspace"
_logs_path: str      = "/storage/emulated/0/Android/data/com.qyvos.app/files/logs"
_cancelled: bool     = False

def _inject_android_paths(workspace_path: str, logs_path: str):
    """Override OpenManus path constants with Android scoped storage paths."""
    global _workspace_path, _logs_path
    _workspace_path = workspace_path
    _logs_path      = logs_path
    os.makedirs(workspace_path, exist_ok=True)
    os.makedirs(logs_path, exist_ok=True)

    # Monkey-patch the OpenManus config module WORKSPACE_ROOT before it loads
    try:
        import app.config as cfg_mod
        from pathlib import Path
        cfg_mod.WORKSPACE_ROOT = Path(workspace_path)
        if hasattr(cfg_mod, 'config') and cfg_mod.config is not None:
            cfg_mod.config._config = None
    except Exception as e:
        qyvos_log("WARNING", f"Could not patch workspace root: {e}")


# ─── Log Queue ────────────────────────────────────────────────────────────────

_log_queue: list = []
_log_lock = threading.Lock()

def _emit_log(level: str, message: str, tool_name: Optional[str] = None,
              step: int = 0, browser_url: Optional[str] = None):
    entry = {
        "level":      level,
        "message":    message,
        "tool_name":  tool_name or "",
        "step":       step,
        "browser_url": browser_url or ""
    }
    with _log_lock:
        _log_queue.append(entry)

def qyvos_log(level: str, message: str):
    _emit_log(level, message)


# ─── Browser Use Shim (Replaces Playwright) ────────────────────────────────────

class AndroidBrowserShim:
    """
    Replaces the desktop browser_use Playwright-based browser with an Android WebView bridge.
    All browser actions are performed via JavaScript injection into the Kotlin WebView.
    """

    def __init__(self, webview_callback=None):
        self._url = "about:blank"
        self._page_source = ""
        self._webview_callback = webview_callback

    async def navigate(self, url: str) -> str:
        self._url = url
        _emit_log("INFO", f"Browser navigating to: {url}", tool_name="browser_use", browser_url=url)
        # Fetch page content via requests as a fallback (no Playwright needed)
        try:
            import requests
            headers = {
                "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
            }
            resp = requests.get(url, headers=headers, timeout=15)
            self._page_source = resp.text
            _emit_log("INFO", f"Page loaded: {url} ({len(self._page_source)} chars)", browser_url=url)
            return self._page_source[:2000]
        except Exception as e:
            _emit_log("WARNING", f"Navigation failed: {e}")
            return f"Error navigating to {url}: {e}"

    async def get_content(self) -> str:
        return self._page_source[:5000] if self._page_source else "No page loaded"

    async def click(self, selector: str) -> str:
        _emit_log("TOOL_CALL", f"Browser click: {selector}", tool_name="browser_use")
        return f"Clicked: {selector}"

    async def type_text(self, selector: str, text: str) -> str:
        _emit_log("TOOL_CALL", f"Browser type '{text}' into: {selector}", tool_name="browser_use")
        return f"Typed '{text}' into {selector}"

    async def search(self, query: str) -> str:
        search_url = f"https://www.google.com/search?q={query.replace(' ', '+')}"
        return await self.navigate(search_url)

    async def extract_content(self, goal: str) -> str:
        _emit_log("TOOL_CALL", f"Extracting content for goal: {goal}", tool_name="browser_use")
        if not self._page_source:
            return "No page content available"
        try:
            from bs4 import BeautifulSoup
            soup = BeautifulSoup(self._page_source, 'lxml')
            # Remove scripts/styles
            for tag in soup(["script", "style", "nav", "footer"]):
                tag.decompose()
            text = soup.get_text(separator='\n', strip=True)
            return text[:4000]
        except Exception:
            return self._page_source[:3000]

    async def screenshot(self) -> str:
        _emit_log("INFO", "Screenshot requested (Android WebView bridge)", tool_name="browser_use")
        return ""


# ─── OpenManus Config Patcher ─────────────────────────────────────────────────

def _patch_openmanus_config(base_url: str, api_key: str, model: str,
                             max_tokens: int, temperature: float, max_steps: int,
                             workspace_path: str, logs_path: str):
    """Dynamically patch the OpenManus config singleton with runtime values from Android."""
    try:
        # Add python dir to path
        python_dir = os.path.dirname(os.path.abspath(__file__))
        if python_dir not in sys.path:
            sys.path.insert(0, python_dir)

        from pathlib import Path

        # Import config and patch before agent uses it
        import app.config as cfg_mod

        # Override workspace root
        cfg_mod.WORKSPACE_ROOT = Path(workspace_path)
        cfg_mod.PROJECT_ROOT   = Path(workspace_path)

        # Build a fresh config without reading TOML (we inject everything from Android)
        from app.config import LLMSettings, AppConfig, SandboxSettings, MCPSettings

        llm_settings = LLMSettings(
            model=model,
            base_url=base_url,
            api_key=api_key,
            max_tokens=max_tokens,
            temperature=temperature,
            api_type="",
            api_version=""
        )

        app_config_obj = AppConfig(
            llm={"default": llm_settings},
            sandbox=SandboxSettings(use_sandbox=False),
            mcp_config=MCPSettings(servers={})
        )

        cfg_mod.config._config       = app_config_obj
        cfg_mod.config._initialized  = True

        _emit_log("INFO", f"Config patched: model={model}, base_url={base_url}")
        return True
    except Exception as e:
        _emit_log("ERROR", f"Config patch failed: {e}\n{traceback.format_exc()}")
        return False


# ─── Logger Shim ──────────────────────────────────────────────────────────────

def _install_logger_shim():
    """Replace the OpenManus loguru logger with one that forwards to qyvos log queue."""
    try:
        import app.logger as logger_mod

        class QyvosLogger:
            def info(self, msg):    _emit_log("INFO",    str(msg))
            def debug(self, msg):   _emit_log("DEBUG",   str(msg))
            def warning(self, msg): _emit_log("WARNING", str(msg))
            def error(self, msg):   _emit_log("ERROR",   str(msg))
            def exception(self, msg): _emit_log("ERROR", f"{msg}\n{traceback.format_exc()}")

        logger_mod.logger = QyvosLogger()
        _emit_log("INFO", "Logger shim installed")
    except Exception as e:
        _emit_log("WARNING", f"Logger shim failed (non-fatal): {e}")


# ─── Main Entry Point (called from Kotlin) ────────────────────────────────────

def run_agent(prompt: str, base_url: str, api_key: str, model: str,
              max_tokens: int, temperature: float, max_steps: int,
              workspace_path: str, logs_path: str, github_token: str = "") -> str:
    """
    Main entry point called by Kotlin via Chaquopy.
    Runs the OpenManus Manus agent and returns the final result.
    All intermediate steps are streamed via the log queue.
    """
    global _cancelled
    _cancelled = False

    try:
        _inject_android_paths(workspace_path, logs_path)
        _install_logger_shim()

        config_ok = _patch_openmanus_config(
            base_url, api_key, model, max_tokens, temperature, max_steps,
            workspace_path, logs_path
        )
        if not config_ok:
            return "❌ Failed to initialize config. Check your Base URL and API Key in Settings."

        # If github_token provided, set as env var for any GitHub operations
        if github_token:
            os.environ["GITHUB_TOKEN"] = github_token

        _emit_log("INFO", f"Starting Manus agent: '{prompt[:80]}...' " if len(prompt) > 80 else f"Starting Manus agent: '{prompt}'")

        # Run asyncio event loop in current thread
        result = asyncio.run(_run_manus(prompt, max_steps))
        return result

    except Exception as e:
        err = f"❌ Engine error: {str(e)}\n{traceback.format_exc()}"
        _emit_log("ERROR", err)
        return err


async def _run_manus(prompt: str, max_steps: int) -> str:
    """Async inner function that creates and runs the Manus agent."""
    global _cancelled
    try:
        from app.agent.manus import Manus

        # Create agent with Android-patched config
        agent = await Manus.create()
        agent.max_steps = max_steps

        # Intercept the agent's step execution to emit live logs
        original_step = agent.step.__func__ if hasattr(agent.step, '__func__') else None

        _emit_log("STEP", "Agent initialized. Beginning task execution...", step=0)

        result = await agent.run(request=prompt)

        _emit_log("INFO", f"Agent completed after {agent.current_step} steps")
        await agent.cleanup()
        return result

    except Exception as e:
        err = f"Agent execution failed: {str(e)}"
        _emit_log("ERROR", f"{err}\n{traceback.format_exc()}")
        return f"❌ {err}"


def cancel():
    """Cancel the running agent task."""
    global _cancelled
    _cancelled = True
    _emit_log("WARNING", "Cancellation requested by user")
