package com.qyvos.app.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.qyvos.app.data.AppConfig
import com.qyvos.app.databinding.ActivitySplashBinding
import com.qyvos.app.ui.chat.ChatActivity
import com.qyvos.app.ui.settings.SettingsActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject lateinit var appConfig: AppConfig

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            // Play logo animation
            binding.lottieAnimation.playAnimation()
            delay(2200)

            val apiKey = appConfig.apiKey.first()
            if (apiKey.isBlank()) {
                // First launch — go to settings to configure
                startActivity(Intent(this@SplashActivity, SettingsActivity::class.java).apply {
                    putExtra(SettingsActivity.EXTRA_FIRST_LAUNCH, true)
                })
            } else {
                startActivity(Intent(this@SplashActivity, ChatActivity::class.java))
            }
            finish()
        }
    }
}
