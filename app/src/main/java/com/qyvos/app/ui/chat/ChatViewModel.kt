package com.qyvos.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qyvos.app.data.AppConfig
import com.qyvos.app.data.MessageDao
import com.qyvos.app.data.SessionDao
import com.qyvos.app.data.models.LogLevel
import com.qyvos.app.data.models.Message
import com.qyvos.app.data.models.MessageRole
import com.qyvos.app.data.models.MessageType
import com.qyvos.app.data.models.Session
import com.qyvos.app.engine.AgentOutput
import com.qyvos.app.network.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val currentSessionId: String = UUID.randomUUID().toString(),
    val inputEnabled: Boolean = true,
    val error: String? = null
)

/**
 * Chat orchestration. Reads user-controlled engine config from
 * [AppConfig] on every send, calls [ChatRepository] over plain HTTP, and
 * persists both sides of the conversation in Room.
 *
 * The on-device Python agent (OpenManusEngine) is intentionally NOT used
 * here anymore — heavy AI logic now lives on the configurable backend
 * (QYVOS Hugging Face Space, OpenAI, DeepSeek, etc.) so the APK stays
 * Chaquopy-compatible.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val messageDao: MessageDao,
    private val sessionDao: SessionDao,
    private val appConfig: AppConfig
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _executionLogs = MutableSharedFlow<AgentOutput>(replay = 0, extraBufferCapacity = 512)
    val executionLogs: SharedFlow<AgentOutput> = _executionLogs.asSharedFlow()

    private val _browserUrl = MutableStateFlow<String?>(null)
    val browserUrl: StateFlow<String?> = _browserUrl.asStateFlow()

    private val _streamingMessage = MutableStateFlow<String?>(null)
    val streamingMessage: StateFlow<String?> = _streamingMessage.asStateFlow()

    private var streamingMessageId: String? = null
    private var currentJob: Job? = null

    init {
        createNewSession()
    }

    fun createNewSession() {
        val sessionId = UUID.randomUUID().toString()
        viewModelScope.launch {
            sessionDao.insert(Session(id = sessionId, title = "New Session"))
            _uiState.update { it.copy(currentSessionId = sessionId, messages = emptyList()) }
        }
    }

    fun sendMessage(userInput: String) {
        if (userInput.isBlank() || _uiState.value.isLoading) return

        currentJob = viewModelScope.launch {
            val sessionId = _uiState.value.currentSessionId

            // 1) Persist + render the user's bubble immediately.
            val userMsg = Message(
                sessionId = sessionId,
                role      = MessageRole.USER,
                content   = userInput.trim()
            )
            messageDao.insert(userMsg)
            _uiState.update { state ->
                state.copy(
                    messages     = state.messages + userMsg,
                    isLoading    = true,
                    inputEnabled = false,
                    error        = null
                )
            }

            // 2) Streaming placeholder (currently non-streaming, but the
            //    UI already wired for live updates).
            streamingMessageId = UUID.randomUUID().toString()
            _streamingMessage.value = ""

            val cfg = appConfig.getSnapshot()

            // God's Eye live log
            emitLog(
                sessionId,
                "POST ${cfg.fullUrl()}  model=${cfg.modelName}",
                LogLevel.STEP
            )

            // 3) History sent to the backend = everything BEFORE the new
            //    user message we just inserted.
            val history = _uiState.value.messages.dropLast(1)

            val result = chatRepository.sendMessage(history = history, userPrompt = userInput)

            when (result) {
                is ChatRepository.Result.Success -> {
                    emitLog(
                        sessionId,
                        "Response received in ${result.latencyMs} ms (${result.content.length} chars)",
                        LogLevel.TOOL_RESULT
                    )

                    val assistantMsg = Message(
                        id        = streamingMessageId ?: UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        role      = MessageRole.ASSISTANT,
                        type      = MessageType.TEXT,
                        content   = result.content
                    )
                    messageDao.insert(assistantMsg)

                    _streamingMessage.value = null
                    streamingMessageId = null

                    _uiState.update { state ->
                        state.copy(
                            messages     = state.messages + assistantMsg,
                            isLoading    = false,
                            inputEnabled = true
                        )
                    }
                    updateSessionTitle()
                }
                is ChatRepository.Result.Failure -> {
                    val hint = if (cfg.apiKey.isBlank()) {
                        "  •  Tip: open Settings and add an API Key for ${cfg.baseUrl}."
                    } else ""

                    emitLog(sessionId, "Request failed: ${result.errorMessage}", LogLevel.ERROR)

                    val errMsg = Message(
                        sessionId = sessionId,
                        role      = MessageRole.ASSISTANT,
                        type      = MessageType.ERROR,
                        content   = "⚠️ ${result.errorMessage}$hint"
                    )
                    messageDao.insert(errMsg)
                    _streamingMessage.value = null
                    streamingMessageId = null
                    _uiState.update {
                        it.copy(
                            messages     = it.messages + errMsg,
                            isLoading    = false,
                            inputEnabled = true,
                            error        = result.errorMessage
                        )
                    }
                }
            }
        }
    }

    private suspend fun emitLog(
        sessionId: String,
        message: String,
        level: LogLevel
    ) {
        _executionLogs.emit(
            AgentOutput(
                sessionId = sessionId,
                content   = message,
                logLevel  = level
            )
        )
    }

    private suspend fun updateSessionTitle() {
        val messages = _uiState.value.messages
        val firstUserMsg = messages.firstOrNull { it.role == MessageRole.USER }?.content ?: return
        val title = if (firstUserMsg.length > 40) firstUserMsg.take(37) + "..." else firstUserMsg
        val sessionId = _uiState.value.currentSessionId
        sessionDao.update(Session(id = sessionId, title = title))
    }

    fun cancelCurrentTask() {
        currentJob?.cancel()
        currentJob = null
        _uiState.update { it.copy(isLoading = false, inputEnabled = true) }
        _streamingMessage.value = null
    }

    fun clearCurrentSession() {
        viewModelScope.launch {
            val sessionId = _uiState.value.currentSessionId
            messageDao.deleteSession(sessionId)
            _uiState.update { it.copy(messages = emptyList()) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()
    }
}
