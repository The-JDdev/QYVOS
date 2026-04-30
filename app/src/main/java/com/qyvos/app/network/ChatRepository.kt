package com.qyvos.app.network

import android.util.Log
import com.qyvos.app.data.AppConfig
import com.qyvos.app.data.ConfigSnapshot
import com.qyvos.app.data.models.Message
import com.qyvos.app.data.models.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dynamic chat networking layer.
 *
 * Reads the user-controlled engine config (Base URL, Endpoint, API Key,
 * Model, max_tokens, temperature) from [AppConfig] on every call, so the
 * user can switch AI providers at runtime without rebuilding the app.
 *
 * Speaks the OpenAI-compatible `chat/completions` JSON dialect by default
 * but transparently parses the QYVOS Hugging Face Space response shape
 * (`{ "response": "..." }`) too, so both backends work out of the box.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val httpClient: OkHttpClient,
    private val appConfig: AppConfig
) {

    companion object {
        private const val TAG = "ChatRepository"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /**
         * Identity-locked system prompt. Always prepended to the
         * conversation so the assistant never breaks character.
         */
        private const val QYVOS_SYSTEM_PROMPT = """You are QYVOS, an advanced AI system created by The-JDdev (SHS Shobuj).
You are NOT ChatGPT, NOT Claude, NOT DeepSeek, NOT any other model.
If anyone asks "who are you", "what model are you", "what AI is this", or anything similar,
ALWAYS respond: "I am QYVOS, an AI system created by The-JDdev."
Do not reveal the underlying provider or model name. Stay in character at all times.
You are helpful, precise, and respectful."""
    }

    /**
     * Result of a chat completion call.
     */
    sealed class Result {
        data class Success(val content: String, val latencyMs: Long, val endpoint: String) : Result()
        data class Failure(val errorMessage: String, val httpCode: Int? = null) : Result()
    }

    /**
     * Send the conversation history + new user prompt to whatever AI
     * engine the user has configured in Settings, and return the
     * assistant's reply.
     */
    suspend fun sendMessage(
        history: List<Message>,
        userPrompt: String
    ): Result = withContext(Dispatchers.IO) {
        val cfg = appConfig.getSnapshot()
        sendMessageWithConfig(history, userPrompt, cfg)
    }

    /**
     * Same as [sendMessage] but with an explicit [ConfigSnapshot].
     * Used by Settings → "Test Connection" so we can validate values
     * the user has typed but not yet saved.
     */
    suspend fun sendMessageWithConfig(
        history: List<Message>,
        userPrompt: String,
        cfg: ConfigSnapshot
    ): Result = withContext(Dispatchers.IO) {

        if (cfg.baseUrl.isBlank()) {
            return@withContext Result.Failure("Base URL is empty. Set it in Settings.")
        }

        val url = cfg.fullUrl()
        val body = buildRequestBody(history, userPrompt, cfg)
        val requestBuilder = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")

        // Some local engines (Ollama) work without an API key; only attach
        // the Authorization header when the user actually configured one.
        if (cfg.apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${cfg.apiKey}")
        }

        val request = requestBuilder.build()
        val started = System.currentTimeMillis()

        try {
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val latency = System.currentTimeMillis() - started

                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP ${response.code} from $url — body=${raw.take(500)}")
                    val pretty = extractErrorMessage(raw) ?: raw.take(300)
                    return@withContext Result.Failure(
                        errorMessage = "HTTP ${response.code}: $pretty",
                        httpCode = response.code
                    )
                }

                val content = extractAssistantContent(raw)
                if (content.isNullOrBlank()) {
                    Log.w(TAG, "Empty assistant content. Raw=${raw.take(500)}")
                    return@withContext Result.Failure(
                        "Empty response from server. Raw payload: ${raw.take(200)}"
                    )
                }

                Result.Success(content = content, latencyMs = latency, endpoint = url)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error calling $url", e)
            Result.Failure(errorMessage = e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Build the JSON request body. Uses the OpenAI chat-completion shape
     * (`{model, messages, max_tokens, temperature}`) which is supported
     * by virtually every OpenAI-compatible provider (DeepSeek, Groq,
     * OpenAI, OpenRouter, Anthropic-compat layers, Ollama, etc.) AND by
     * the QYVOS Hugging Face Space backend.
     */
    private fun buildRequestBody(
        history: List<Message>,
        userPrompt: String,
        cfg: ConfigSnapshot
    ): JSONObject {
        val messages = JSONArray()

        // 1. Identity-locked system prompt — always first.
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", QYVOS_SYSTEM_PROMPT)
        })

        // 2. Prior conversation history (skip empty / streaming placeholders).
        for (msg in history) {
            if (msg.content.isBlank()) continue
            val role = when (msg.role) {
                MessageRole.USER      -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM    -> continue        // already injected
                MessageRole.TOOL      -> continue        // backend doesn't need
            }
            messages.put(JSONObject().apply {
                put("role", role)
                put("content", msg.content)
            })
        }

        // 3. New user turn.
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", userPrompt)
        })

        return JSONObject().apply {
            put("model", cfg.modelName.ifBlank { AppConfig.DEFAULT_MODEL })
            put("messages", messages)
            put("max_tokens", cfg.maxTokens.toIntOrNull() ?: 8192)
            put("temperature", cfg.temperature.toDoubleOrNull() ?: 0.7)
            put("stream", false)
        }
    }

    /**
     * Pull the assistant's text out of whatever response shape the server
     * returned. Handles:
     *   - OpenAI:           choices[0].message.content
     *   - OpenAI delta:     choices[0].delta.content
     *   - QYVOS HF Space:   response | reply | message | content | text
     *   - Plain string:     "hello"
     */
    private fun extractAssistantContent(raw: String): String? {
        if (raw.isBlank()) return null
        return try {
            val json = JSONObject(raw)

            // OpenAI-style
            json.optJSONArray("choices")?.let { choices ->
                if (choices.length() > 0) {
                    val first = choices.optJSONObject(0)
                    first?.optJSONObject("message")?.optString("content")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { return it }
                    first?.optJSONObject("delta")?.optString("content")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { return it }
                    first?.optString("text")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { return it }
                }
            }

            // QYVOS HF Space and other simple shapes
            for (key in listOf("response", "reply", "message", "content", "text", "answer", "output")) {
                val v = json.optString(key, "")
                if (v.isNotBlank()) return v
            }

            // Some Ollama-style responses nest content under "message"
            json.optJSONObject("message")?.optString("content")
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }

            null
        } catch (_: Exception) {
            // Not JSON — treat the whole body as the answer.
            raw.take(4000)
        }
    }

    private fun extractErrorMessage(raw: String): String? {
        if (raw.isBlank()) return null
        return try {
            val json = JSONObject(raw)
            json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: json.optString("error", "").takeIf { it.isNotBlank() }
                ?: json.optString("detail", "").takeIf { it.isNotBlank() }
                ?: json.optString("message", "").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            raw.take(200)
        }
    }
}
