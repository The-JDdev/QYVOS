package com.qyvos.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.qyvos.app.R
import com.qyvos.app.data.AppConfig
import com.qyvos.app.data.ConfigSnapshot
import com.qyvos.app.databinding.ActivitySettingsBinding
import com.qyvos.app.network.ChatRepository
import com.qyvos.app.ui.chat.ChatActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FIRST_LAUNCH = "first_launch"
    }

    @Inject lateinit var appConfig: AppConfig
    @Inject lateinit var chatRepository: ChatRepository

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val isFirstLaunch = intent.getBooleanExtra(EXTRA_FIRST_LAUNCH, false)
        if (isFirstLaunch) {
            binding.tvTitle.text = "Configure QYVOS"
            binding.tvSubtitle.text = "Set up your AI engine to get started"
        }

        setSupportActionBar(binding.toolbar)
        if (!isFirstLaunch) supportActionBar?.setDisplayHomeAsUpEnabled(true)

        loadCurrentSettings()
        setupButtons()
        setupModelPresets()
    }

    private fun loadCurrentSettings() {
        lifecycleScope.launch {
            binding.etBaseUrl.setText(appConfig.baseUrl.first())
            binding.etEndpoint.setText(appConfig.endpointPath.first())
            binding.etApiKey.setText(appConfig.apiKey.first())
            binding.etModelName.setText(appConfig.modelName.first())
            binding.etMaxTokens.setText(appConfig.maxTokens.first())
            binding.etTemperature.setText(appConfig.temperature.first())
            binding.etMaxSteps.setText(appConfig.maxSteps.first())
        }
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener {
            saveSettings()
        }
        binding.btnTestConnection.setOnClickListener {
            testApiConnection()
        }
        binding.btnResetDefaults.setOnClickListener {
            resetToDefaults()
        }
    }

    private fun setupModelPresets() {
        // QYVOS Cloud (Hugging Face Space backend) — no API key required.
        binding.chipQyvosCloud.setOnClickListener {
            binding.etBaseUrl.setText("https://the-jddev-qyvos-api.hf.space")
            binding.etEndpoint.setText("api/chat")
            binding.etModelName.setText("qyvos-v1")
        }
        binding.chipDeepseek.setOnClickListener {
            binding.etBaseUrl.setText("https://api.deepseek.com/v1")
            binding.etEndpoint.setText("chat/completions")
            binding.etModelName.setText("deepseek-r1-0528")
        }
        binding.chipOpenai.setOnClickListener {
            binding.etBaseUrl.setText("https://api.openai.com/v1")
            binding.etEndpoint.setText("chat/completions")
            binding.etModelName.setText("gpt-4o")
        }
        binding.chipClaude.setOnClickListener {
            binding.etBaseUrl.setText("https://api.anthropic.com/v1")
            binding.etEndpoint.setText("chat/completions")
            binding.etModelName.setText("claude-3-5-sonnet-20241022")
        }
        binding.chipGrok.setOnClickListener {
            binding.etBaseUrl.setText("https://api.x.ai/v1")
            binding.etEndpoint.setText("chat/completions")
            binding.etModelName.setText("grok-3")
        }
        binding.chipGroq.setOnClickListener {
            binding.etBaseUrl.setText("https://api.groq.com/openai/v1")
            binding.etEndpoint.setText("chat/completions")
            binding.etModelName.setText("llama-3.3-70b-versatile")
        }
        binding.chipOllama.setOnClickListener {
            binding.etBaseUrl.setText("http://localhost:11434/v1")
            binding.etEndpoint.setText("chat/completions")
            binding.etModelName.setText("llama3.2")
        }
    }

    private fun currentSnapshot(): ConfigSnapshot = ConfigSnapshot(
        baseUrl      = binding.etBaseUrl.text?.toString()?.trim().orEmpty(),
        endpointPath = binding.etEndpoint.text?.toString()?.trim().orEmpty()
            .ifBlank { AppConfig.DEFAULT_ENDPOINT_PATH },
        apiKey       = binding.etApiKey.text?.toString()?.trim().orEmpty(),
        modelName    = binding.etModelName.text?.toString()?.trim().orEmpty()
            .ifBlank { AppConfig.DEFAULT_MODEL },
        maxTokens    = binding.etMaxTokens.text?.toString()?.trim().orEmpty()
            .ifBlank { AppConfig.DEFAULT_MAX_TOKENS },
        temperature  = binding.etTemperature.text?.toString()?.trim().orEmpty()
            .ifBlank { AppConfig.DEFAULT_TEMPERATURE },
        maxSteps     = binding.etMaxSteps.text?.toString()?.trim().orEmpty()
            .ifBlank { AppConfig.DEFAULT_MAX_STEPS }
    )

    private fun saveSettings() {
        val snap = currentSnapshot()

        binding.tilBaseUrl.error    = null
        binding.tilEndpoint.error   = null
        binding.tilModelName.error  = null

        if (snap.baseUrl.isBlank()) { binding.tilBaseUrl.error = "Required"; return }
        if (snap.endpointPath.isBlank()) { binding.tilEndpoint.error = "Required"; return }
        if (snap.modelName.isBlank()) { binding.tilModelName.error = "Required"; return }

        lifecycleScope.launch {
            appConfig.save(
                baseUrl      = snap.baseUrl,
                endpointPath = snap.endpointPath,
                apiKey       = snap.apiKey,
                modelName    = snap.modelName,
                maxTokens    = snap.maxTokens,
                temperature  = snap.temperature,
                maxSteps     = snap.maxSteps
            )
            Toast.makeText(this@SettingsActivity, "Settings saved", Toast.LENGTH_SHORT).show()

            val isFirstLaunch = intent.getBooleanExtra(EXTRA_FIRST_LAUNCH, false)
            if (isFirstLaunch) {
                startActivity(Intent(this@SettingsActivity, ChatActivity::class.java))
                finish()
            } else {
                finish()
            }
        }
    }

    /**
     * Test the AI engine using whatever the user has typed (NOT yet
     * persisted). Routes through the same [ChatRepository] used at
     * runtime, so this validates the entire dynamic networking path.
     */
    private fun testApiConnection() {
        val snap = currentSnapshot()
        if (snap.baseUrl.isBlank()) {
            Toast.makeText(this, "Enter a Base URL first", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnTestConnection.isEnabled = false
        binding.tvTestResult.setTextColor(getColor(R.color.text_secondary))
        binding.tvTestResult.text = "Testing ${snap.fullUrl()}…"

        lifecycleScope.launch {
            val result = chatRepository.sendMessageWithConfig(
                history    = emptyList(),
                userPrompt = "Say 'pong' in one word.",
                cfg        = snap
            )
            when (result) {
                is ChatRepository.Result.Success -> {
                    binding.tvTestResult.setTextColor(getColor(R.color.success_green))
                    binding.tvTestResult.text =
                        "Connected (${result.latencyMs} ms)\n${result.content.take(160)}"
                }
                is ChatRepository.Result.Failure -> {
                    binding.tvTestResult.setTextColor(getColor(R.color.error_red))
                    binding.tvTestResult.text = "Failed: ${result.errorMessage}"
                }
            }
            binding.btnTestConnection.isEnabled = true
        }
    }

    private fun resetToDefaults() {
        binding.etBaseUrl.setText(AppConfig.DEFAULT_BASE_URL)
        binding.etEndpoint.setText(AppConfig.DEFAULT_ENDPOINT_PATH)
        binding.etModelName.setText(AppConfig.DEFAULT_MODEL)
        binding.etMaxTokens.setText(AppConfig.DEFAULT_MAX_TOKENS)
        binding.etTemperature.setText(AppConfig.DEFAULT_TEMPERATURE)
        binding.etMaxSteps.setText(AppConfig.DEFAULT_MAX_STEPS)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
