package com.qyvos.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.qyvos.app.data.AppConfig
import com.qyvos.app.databinding.ActivitySettingsBinding
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

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val isFirstLaunch = intent.getBooleanExtra(EXTRA_FIRST_LAUNCH, false)
        if (isFirstLaunch) {
            binding.tvTitle.text = "Configure QYVOS"
            binding.tvSubtitle.text = "Set up your AI model to get started"
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
        binding.chipDeepseek.setOnClickListener {
            binding.etBaseUrl.setText("https://api.deepseek.com/v1")
            binding.etModelName.setText("deepseek-r1-0528")
        }
        binding.chipOpenai.setOnClickListener {
            binding.etBaseUrl.setText("https://api.openai.com/v1")
            binding.etModelName.setText("gpt-4o")
        }
        binding.chipClaude.setOnClickListener {
            binding.etBaseUrl.setText("https://api.anthropic.com/v1")
            binding.etModelName.setText("claude-3-5-sonnet-20241022")
        }
        binding.chipGrok.setOnClickListener {
            binding.etBaseUrl.setText("https://api.x.ai/v1")
            binding.etModelName.setText("grok-3")
        }
        binding.chipGroq.setOnClickListener {
            binding.etBaseUrl.setText("https://api.groq.com/openai/v1")
            binding.etModelName.setText("llama-3.3-70b-versatile")
        }
        binding.chipOllama.setOnClickListener {
            binding.etBaseUrl.setText("http://localhost:11434/v1")
            binding.etModelName.setText("llama3.2")
        }
    }

    private fun saveSettings() {
        val baseUrl    = binding.etBaseUrl.text?.toString()?.trim() ?: ""
        val apiKey     = binding.etApiKey.text?.toString()?.trim() ?: ""
        val modelName  = binding.etModelName.text?.toString()?.trim() ?: ""
        val maxTokens  = binding.etMaxTokens.text?.toString()?.trim() ?: ""
        val temperature = binding.etTemperature.text?.toString()?.trim() ?: ""
        val maxSteps   = binding.etMaxSteps.text?.toString()?.trim() ?: ""

        if (baseUrl.isBlank()) { binding.tilBaseUrl.error = "Required"; return }
        if (modelName.isBlank()) { binding.tilModelName.error = "Required"; return }

        lifecycleScope.launch {
            appConfig.save(baseUrl, apiKey, modelName, maxTokens, temperature, maxSteps)
            Toast.makeText(this@SettingsActivity, "✅ Settings saved!", Toast.LENGTH_SHORT).show()

            val isFirstLaunch = intent.getBooleanExtra(EXTRA_FIRST_LAUNCH, false)
            if (isFirstLaunch) {
                startActivity(Intent(this@SettingsActivity, ChatActivity::class.java))
                finish()
            } else {
                finish()
            }
        }
    }

    private fun testApiConnection() {
        val baseUrl  = binding.etBaseUrl.text?.toString()?.trim() ?: ""
        val apiKey   = binding.etApiKey.text?.toString()?.trim() ?: ""
        val model    = binding.etModelName.text?.toString()?.trim() ?: ""
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            Toast.makeText(this, "Enter Base URL and API Key first", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnTestConnection.isEnabled = false
        binding.tvTestResult.text = "Testing..."
        lifecycleScope.launch {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val body = """{"model":"$model","messages":[{"role":"user","content":"Hi"}],"max_tokens":10}"""
                val request = okhttp3.Request.Builder()
                    .url("$baseUrl/chat/completions")
                    .post(okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"), body))
                    .header("Authorization", "Bearer $apiKey")
                    .build()
                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    client.newCall(request).execute()
                }
                if (response.isSuccessful) {
                    binding.tvTestResult.text = "✅ Connection successful!"
                    binding.tvTestResult.setTextColor(getColor(com.qyvos.app.R.color.success_green))
                } else {
                    binding.tvTestResult.text = "❌ HTTP ${response.code()}: ${response.message()}"
                    binding.tvTestResult.setTextColor(getColor(com.qyvos.app.R.color.error_red))
                }
            } catch (e: Exception) {
                binding.tvTestResult.text = "❌ ${e.message}"
                binding.tvTestResult.setTextColor(getColor(com.qyvos.app.R.color.error_red))
            } finally {
                binding.btnTestConnection.isEnabled = true
            }
        }
    }

    private fun resetToDefaults() {
        binding.etBaseUrl.setText(AppConfig.DEFAULT_BASE_URL)
        binding.etModelName.setText(AppConfig.DEFAULT_MODEL)
        binding.etMaxTokens.setText(AppConfig.DEFAULT_MAX_TOKENS)
        binding.etTemperature.setText(AppConfig.DEFAULT_TEMPERATURE)
        binding.etMaxSteps.setText(AppConfig.DEFAULT_MAX_STEPS)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
