package com.qyvos.app.ui.developer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.qyvos.app.R
import com.qyvos.app.databinding.ActivityDeveloperBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DeveloperActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeveloperBinding

    private companion object {
        const val DEV_NAME       = "SHS Shobuj (JD Vijay)"
        const val WEBMONEY_WMZ   = "Z430378899900"
        const val WEBMONEY_WMT   = "T202226490170"
        const val TELEGRAM_URL   = "https://t.me/aamoviesofficial"
        const val EMAIL_ADDRESS  = "The-JDdev.official@gmail.com"
        const val FACEBOOK_URL   = "https://www.facebook.com/itsshsshobuj"
        const val GITHUB_URL     = "https://github.com/The-JDdev/QYVOS"
        const val APP_VERSION    = "1.0.0"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeveloperBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Developer Hub"

        setupDevInfo()
        setupDonation()
        setupLinks()
    }

    private fun setupDevInfo() {
        binding.tvDevName.text     = DEV_NAME
        binding.tvAppVersion.text  = "QYVOS v$APP_VERSION"
        binding.tvAppTagline.text  = "Powered by OpenManus AI Engine"

        // Developer photo is set in layout via drawable
    }

    private fun setupDonation() {
        // WebMoney WMZ
        binding.tvWmzAddress.text = WEBMONEY_WMZ
        binding.btnCopyWmz.setOnClickListener {
            copyToClipboard("WebMoney WMZ", WEBMONEY_WMZ)
        }

        // WebMoney WMT
        binding.tvWmtAddress.text = WEBMONEY_WMT
        binding.btnCopyWmt.setOnClickListener {
            copyToClipboard("WebMoney WMT", WEBMONEY_WMT)
        }

        // bKash — user can paste their bKash number in settings
        binding.tvBkashLabel.text  = "bKash Donation"
        binding.tvBkashAddress.text = "Contact developer for bKash number"
        binding.btnCopyBkash.setOnClickListener {
            Toast.makeText(this, "Contact via Telegram or Email for bKash details", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupLinks() {
        binding.btnTelegram.setOnClickListener { openUrl(TELEGRAM_URL) }
        binding.btnEmail.setOnClickListener { openEmail() }
        binding.btnFacebook.setOnClickListener { openUrl(FACEBOOK_URL) }
        binding.btnGithub.setOnClickListener { openUrl(GITHUB_URL) }

        binding.tvTelegramHandle.text  = "@aamoviesofficial"
        binding.tvEmailAddress.text    = EMAIL_ADDRESS
        binding.tvFacebookHandle.text  = "facebook.com/itsshsshobuj"
        binding.tvGithubHandle.text    = "github.com/The-JDdev/QYVOS"
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open URL: $url", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openEmail() {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$EMAIL_ADDRESS")
                putExtra(Intent.EXTRA_SUBJECT, "QYVOS Inquiry")
            }
            startActivity(intent)
        } catch (e: Exception) {
            copyToClipboard("Email", EMAIL_ADDRESS)
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "✅ $label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
