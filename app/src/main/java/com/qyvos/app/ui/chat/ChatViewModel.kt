package com.qyvos.app.ui.chat

import androidx.lifecycle.*
import com.qyvos.app.data.AppConfig
import com.qyvos.app.data.MessageDao
import com.qyvos.app.data.SessionDao
import com.qyvos.app.data.models.*
import com.qyvos.app.engine.AgentOutput
import com.qyvos.app.engine.OpenManusEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val currentSessionId: String = UUID.randomUUID().toString(),
    val inputEnabled: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val engine: OpenManusEngine,
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

    // Live streaming assistant message
    private val _streamingMessage = MutableStateFlow<String?>(null)
    val streamingMessage: StateFlow<String?> = _streamingMessage.asStateFlow()

    private var streamingMessageId: String? = null
    private var currentJob: Job? = null

    init {
        // Listen to engine execution events
        viewModelScope.launch {
            engine.executionFlow.collect { output ->
                handleAgentOutput(output)
            }
        }
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

        viewModelScope.launch {
            val sessionId = _uiState.value.currentSessionId

            // Save user message
            val userMsg = Message(
                sessionId = sessionId,
                role = MessageRole.USER,
                content = userInput.trim()
            )
            messageDao.insert(userMsg)
            _uiState.update { state ->
                state.copy(
                    messages = state.messages + userMsg,
                    isLoading = true,
                    inputEnabled = false,
                    error = null
                )
            }

            // Start streaming assistant placeholder
            streamingMessageId = UUID.randomUUID().toString()
            _streamingMessage.value = ""

            // Get config snapshot
            val config = appConfig.getSnapshot()
            if (config.apiKey.isBlank()) {
                val errMsg = Message(
                    sessionId = sessionId,
                    role = MessageRole.ASSISTANT,
                    type = MessageType.ERROR,
                    content = "⚠️ No API key configured. Go to Settings to add your API key."
                )
                messageDao.insert(errMsg)
                _uiState.update { it.copy(messages = it.messages + errMsg, isLoading = false, inputEnabled = true) }
                _streamingMessage.value = null
                return@launch
            }

            // Run the OpenManus agent via Python bridge
            engine.runAgent(
                sessionId   = sessionId,
                prompt      = userInput,
                baseUrl     = config.baseUrl,
                apiKey      = config.apiKey,
                modelName   = config.modelName,
                maxTokens   = config.maxTokens.toIntOrNull() ?: 8192,
                temperature = config.temperature.toDoubleOrNull() ?: 0.7,
                maxSteps    = config.maxSteps.toIntOrNull() ?: 20
            )
        }
    }

    private suspend fun handleAgentOutput(output: AgentOutput) {
        if (output.sessionId != _uiState.value.currentSessionId) return

        // Forward to execution log stream
        _executionLogs.emit(output)

        // Update browser URL if provided
        output.browserUrl?.let { url ->
            _browserUrl.value = url
        }

        when {
            output.isThinking -> {
                // Update streaming message with thinking content
                _streamingMessage.value = (_streamingMessage.value ?: "") + output.content
            }
            output.isFinal -> {
                // Finalize the assistant message
                val finalContent = output.content
                val assistantMsg = Message(
                    id        = streamingMessageId ?: UUID.randomUUID().toString(),
                    sessionId = _uiState.value.currentSessionId,
                    role      = MessageRole.ASSISTANT,
                    type      = MessageType.TEXT,
                    content   = finalContent
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

                // Update session title from first user message
                updateSessionTitle()
            }
            output.logLevel == com.qyvos.app.data.models.LogLevel.TOOL_RESULT -> {
                // Show tool results as special messages
                val toolMsg = Message(
                    sessionId = _uiState.value.currentSessionId,
                    role      = MessageRole.TOOL,
                    type      = MessageType.TOOL_RESULT,
                    content   = output.content,
                    toolName  = output.toolName
                )
                messageDao.insert(toolMsg)
                _uiState.update { it.copy(messages = it.messages + toolMsg) }
            }
        }
    }

    private suspend fun updateSessionTitle() {
        val messages = _uiState.value.messages
        val firstUserMsg = messages.firstOrNull { it.role == MessageRole.USER }?.content ?: return
        val title = if (firstUserMsg.length > 40) firstUserMsg.take(37) + "..." else firstUserMsg
        val sessionId = _uiState.value.currentSessionId
        sessionDao.update(Session(id = sessionId, title = title))
    }

    fun cancelCurrentTask() {
        engine.cancelCurrentTask()
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
