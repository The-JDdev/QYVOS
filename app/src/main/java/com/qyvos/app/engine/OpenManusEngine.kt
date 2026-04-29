package com.qyvos.app.engine

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.PyObject
import com.google.gson.Gson
import com.qyvos.app.WorkspaceManager
import com.qyvos.app.data.AppConfig
import com.qyvos.app.data.models.ExecutionLog
import com.qyvos.app.data.models.LogLevel
import com.qyvos.app.security.TokenVault
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class AgentOutput(
    val sessionId: String,
    val content: String,
    val logLevel: LogLevel,
    val step: Int = 0,
    val toolName: String? = null,
    val isThinking: Boolean = false,
    val isFinal: Boolean = false,
    val browserUrl: String? = null
)

@Singleton
class OpenManusEngine @Inject constructor(
    private val context: Context,
    private val appConfig: AppConfig,
    private val tokenVault: TokenVault
) {

    companion object {
        private const val TAG = "OpenManusEngine"
    }

    private val gson = Gson()
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // SharedFlow for broadcasting execution logs to the UI in real time
    private val _executionFlow = MutableSharedFlow<AgentOutput>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val executionFlow: SharedFlow<AgentOutput> = _executionFlow.asSharedFlow()

    private var currentPyEngine: PyObject? = null
    private var isRunning = false

    /**
     * Runs the OpenManus Manus agent with the given prompt.
     * Yields AgentOutput events via [executionFlow].
     */
    fun runAgent(
        sessionId: String,
        prompt: String,
        baseUrl: String,
        apiKey: String,
        modelName: String,
        maxTokens: Int = 8192,
        temperature: Double = 0.7,
        maxSteps: Int = 20
    ) {
        if (isRunning) {
            emit(sessionId, "Agent is already running. Please wait or cancel the current task.", LogLevel.WARNING)
            return
        }

        engineScope.launch {
            isRunning = true
            try {
                runAgentInternal(
                    sessionId, prompt, baseUrl, apiKey, modelName, maxTokens, temperature, maxSteps
                )
            } catch (e: CancellationException) {
                emit(sessionId, "Task cancelled.", LogLevel.WARNING, isFinal = true)
            } catch (e: Exception) {
                Log.e(TAG, "Engine error", e)
                emit(sessionId, "Engine error: ${e.message}", LogLevel.ERROR, isFinal = true)
            } finally {
                isRunning = false
            }
        }
    }

    private suspend fun runAgentInternal(
        sessionId: String,
        prompt: String,
        baseUrl: String,
        apiKey: String,
        modelName: String,
        maxTokens: Int,
        temperature: Double,
        maxSteps: Int
    ) = withContext(Dispatchers.IO) {
        val workspacePath = WorkspaceManager.getWorkspacePath(context)
        val logsPath = WorkspaceManager.getLogsPath(context)

        emit(sessionId, "Initializing OpenManus engine...", LogLevel.INFO)

        val py = Python.getInstance()

        // Load the Android bridge module that wraps the OpenManus engine
        val bridge = py.getModule("qyvos_bridge")

        emit(sessionId, "Engine ready. Starting agent with model: $modelName", LogLevel.INFO)
        emit(sessionId, "Workspace: $workspacePath", LogLevel.DEBUG)

        // Inject GitHub token if available
        val githubToken = tokenVault.getToken(TokenVault.KEY_GITHUB_TOKEN) ?: ""

        // Create a log callback PyObject using Python callable
        val logCallbackModule = py.getModule("qyvos_log_callback")
        logCallbackModule.callAttr("reset")

        // Call the bridge run function
        val result = bridge.callAttr(
            "run_agent",
            prompt,
            baseUrl,
            apiKey,
            modelName,
            maxTokens,
            temperature,
            maxSteps,
            workspacePath,
            logsPath,
            githubToken
        )

        // Poll log queue while agent runs
        val logQueue = logCallbackModule.callAttr("get_log_queue")
        val resultStr = result.toString()

        // Process any remaining logs
        processLogQueue(sessionId, logCallbackModule)

        emit(sessionId, resultStr, LogLevel.INFO, isFinal = true)
    }

    private fun processLogQueue(sessionId: String, logCallbackModule: PyObject) {
        try {
            val logs = logCallbackModule.callAttr("drain_logs")
            val logList = logs.asList()
            for (logEntry in logList) {
                val logMap = logEntry.asMap()
                val level = when (logMap["level"]?.toString()) {
                    "WARNING"     -> LogLevel.WARNING
                    "ERROR"       -> LogLevel.ERROR
                    "STEP"        -> LogLevel.STEP
                    "TOOL_CALL"   -> LogLevel.TOOL_CALL
                    "TOOL_RESULT" -> LogLevel.TOOL_RESULT
                    "THINKING"    -> LogLevel.THINKING
                    else          -> LogLevel.INFO
                }
                val msg       = logMap["message"]?.toString() ?: ""
                val toolName  = logMap["tool_name"]?.toString()
                val step      = logMap["step"]?.toString()?.toIntOrNull() ?: 0
                val browserUrl = logMap["browser_url"]?.toString()
                val isThinking = level == LogLevel.THINKING

                engineScope.launch {
                    _executionFlow.emit(
                        AgentOutput(
                            sessionId = sessionId,
                            content   = msg,
                            logLevel  = level,
                            step      = step,
                            toolName  = toolName,
                            isThinking = isThinking,
                            browserUrl = browserUrl
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing log queue", e)
        }
    }

    fun cancelCurrentTask() {
        engineScope.coroutineContext.cancelChildren()
        isRunning = false
        try {
            val py = Python.getInstance()
            py.getModule("qyvos_bridge").callAttr("cancel")
        } catch (e: Exception) {
            Log.w(TAG, "Could not cancel Python task: ${e.message}")
        }
    }

    fun isCurrentlyRunning(): Boolean = isRunning

    private fun emit(
        sessionId: String,
        message: String,
        level: LogLevel,
        step: Int = 0,
        toolName: String? = null,
        isFinal: Boolean = false,
        browserUrl: String? = null
    ) {
        engineScope.launch {
            _executionFlow.emit(
                AgentOutput(
                    sessionId  = sessionId,
                    content    = message,
                    logLevel   = level,
                    step       = step,
                    toolName   = toolName,
                    isFinal    = isFinal,
                    browserUrl = browserUrl
                )
            )
        }
    }
}
