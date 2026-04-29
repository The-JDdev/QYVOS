"""
QYVOS Log Callback Module
Provides a thread-safe log queue that Kotlin reads from Chaquopy to stream
OpenManus execution logs to the God's Eye execution panel in real time.
"""

import threading
from typing import List, Dict, Any

_log_queue: List[Dict[str, Any]] = []
_lock = threading.Lock()

def reset():
    """Clear the log queue (called at start of each agent run)."""
    with _lock:
        _log_queue.clear()

def push_log(level: str, message: str, tool_name: str = "",
             step: int = 0, browser_url: str = ""):
    """Push a log entry (called from qyvos_bridge or any Python module)."""
    entry = {
        "level":       level,
        "message":     message,
        "tool_name":   tool_name,
        "step":        step,
        "browser_url": browser_url
    }
    with _lock:
        _log_queue.append(entry)

def drain_logs() -> List[Dict[str, Any]]:
    """Drain all pending logs and return them. Called by Kotlin polling loop."""
    with _lock:
        logs = list(_log_queue)
        _log_queue.clear()
    return logs

def get_log_queue():
    """Return reference to this module (used by Kotlin to call drain_logs)."""
    return __import__(__name__)
