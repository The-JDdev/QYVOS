package com.qyvos.app.ui.oauth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.qyvos.app.databinding.ActivityOauthCallbackBinding
import com.qyvos.app.security.TokenVault
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject

@AndroidEntryPoint
class OAuthCallbackActivity : AppCompatActivity() {

    @Inject lateinit var tokenVault: TokenVault

    private lateinit var binding: ActivityOauthCallbackBinding
    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOauthCallbackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleDeepLink(it) }
    }

    private fun handleDeepLink(intent: Intent) {
        val data: Uri? = intent.data
        if (data == null || data.scheme != "qyvos") {
            finish()
            return
        }

        val code  = data.getQueryParameter("code")
        val state = data.getQueryParameter("state")
        val error = data.getQueryParameter("error")

        when {
            error != null -> {
                binding.tvStatus.text = "❌ OAuth Error: $error"
                Toast.makeText(this, "OAuth failed: $error", Toast.LENGTH_LONG).show()
                finish()
            }
            code != null -> {
                binding.tvStatus.text = "Exchanging code for token..."
                val provider = data.host ?: "unknown"
                exchangeCodeForToken(provider, code, state)
            }
            else -> {
                binding.tvStatus.text = "Invalid callback"
                finish()
            }
        }
    }

    private fun exchangeCodeForToken(provider: String, code: String, state: String?) {
        lifecycleScope.launch {
            binding.progressBar.visibility = android.view.View.VISIBLE
            try {
                when (provider) {
                    "github" -> exchangeGitHubToken(code)
                    else     -> {
                        binding.tvStatus.text = "Unknown OAuth provider: $provider"
                        finish()
                    }
                }
            } catch (e: Exception) {
                binding.tvStatus.text = "❌ Token exchange failed: ${e.message}"
                Toast.makeText(this@OAuthCallbackActivity, "Auth failed: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }

    private suspend fun exchangeGitHubToken(code: String) {
        val clientId     = "YOUR_GITHUB_CLIENT_ID"
        val clientSecret = "YOUR_GITHUB_CLIENT_SECRET"

        val requestBody = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("code", code)
            .add("redirect_uri", "qyvos://oauth/callback")
            .build()

        val request = Request.Builder()
            .url("https://github.com/login/oauth/access_token")
            .post(requestBody)
            .header("Accept", "application/json")
            .build()

        val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
        val body = response.body()?.string() ?: throw Exception("Empty response")
        val json = JSONObject(body)

        val accessToken = json.optString("access_token")
        if (accessToken.isNotBlank()) {
            tokenVault.storeToken(TokenVault.KEY_GITHUB_TOKEN, accessToken)
            binding.tvStatus.text = "✅ GitHub connected successfully!"
            Toast.makeText(this@OAuthCallbackActivity, "✅ GitHub authorized!", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
        } else {
            val err = json.optString("error_description", "Unknown error")
            throw Exception(err)
        }
        finish()
    }
}
