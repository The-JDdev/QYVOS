package com.qyvos.app.ui.browser

import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.qyvos.app.databinding.ActivityBrowserViewerBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BrowserViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_READONLY = "readonly"
    }

    private lateinit var binding: ActivityBrowserViewerBinding

    // Expose for Python bridge to inject commands
    var onNavigateCallback: ((String) -> Unit)? = null
    var onPageLoadedCallback: ((String, String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val initialUrl = intent.getStringExtra(EXTRA_URL) ?: "about:blank"
        val isReadOnly = intent.getBooleanExtra(EXTRA_READONLY, false)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupWebView(isReadOnly)
        setupAddressBar(isReadOnly)

        binding.webView.loadUrl(initialUrl)
    }

    private fun setupWebView(isReadOnly: Boolean) {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    binding.progressBar.visibility = android.view.View.VISIBLE
                    binding.etUrl.setText(url)
                }
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.etUrl.setText(url)
                    supportActionBar?.title = view.title ?: url
                    // Capture page screenshot for God's Eye panel
                    view.evaluateJavascript("document.title") { title ->
                        onPageLoadedCallback?.invoke(url, title?.trim('"') ?: "")
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    binding.progressBar.progress = newProgress
                }
                override fun onReceivedTitle(view: WebView, title: String) {
                    supportActionBar?.title = title
                }
            }

            // Disable user interaction when in readonly/agent mode
            if (isReadOnly) {
                setOnTouchListener { _, _ -> true }
            }
        }
    }

    private fun setupAddressBar(isReadOnly: Boolean) {
        if (isReadOnly) {
            binding.etUrl.isEnabled = false
            binding.tvAgentMode.visibility = android.view.View.VISIBLE
        } else {
            binding.btnGo.setOnClickListener {
                val url = binding.etUrl.text?.toString()?.trim() ?: return@setOnClickListener
                val fullUrl = if (url.startsWith("http")) url else "https://$url"
                binding.webView.loadUrl(fullUrl)
            }
        }
        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) binding.webView.goBack()
        }
        binding.btnForward.setOnClickListener {
            if (binding.webView.canGoForward()) binding.webView.goForward()
        }
        binding.btnRefresh.setOnClickListener {
            binding.webView.reload()
        }
    }

    /** Called by the Python bridge to navigate to a URL */
    fun navigateTo(url: String) {
        runOnUiThread { binding.webView.loadUrl(url) }
    }

    /** Execute JavaScript in the WebView context */
    fun executeScript(script: String, callback: (String) -> Unit) {
        runOnUiThread {
            binding.webView.evaluateJavascript(script) { result -> callback(result ?: "") }
        }
    }

    /** Take a screenshot of the current WebView */
    fun captureScreenshot(): android.graphics.Bitmap? {
        binding.webView.isDrawingCacheEnabled = true
        return binding.webView.drawingCache
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) binding.webView.goBack()
        else super.onBackPressed()
    }
}
