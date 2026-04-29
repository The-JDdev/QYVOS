package com.qyvos.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class MessageRole { USER, ASSISTANT, SYSTEM, TOOL }
enum class MessageType { TEXT, TOOL_CALL, TOOL_RESULT, THINKING, ERROR }

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: MessageRole,
    val type: MessageType = MessageType.TEXT,
    val content: String,
    val toolName: String? = null,
    val toolCallId: String? = null,
    val thinking: String? = null,
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "New Session",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ExecutionLog(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val source: String = "ENGINE",
    val message: String,
    val details: String? = null
)

enum class LogLevel { DEBUG, INFO, WARNING, ERROR, STEP, TOOL_CALL, TOOL_RESULT, THINKING }

data class AgentStep(
    val stepNumber: Int,
    val thought: String,
    val action: String,
    val toolName: String?,
    val toolArgs: Map<String, Any>?,
    val result: String?,
    val status: StepStatus = StepStatus.PENDING
)

enum class StepStatus { PENDING, RUNNING, COMPLETED, FAILED }
