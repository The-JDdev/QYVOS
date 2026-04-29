package com.qyvos.app.ui.chat

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.qyvos.app.R
import com.qyvos.app.databinding.ActivityChatBinding
import com.qyvos.app.data.models.LogLevel
import com.qyvos.app.ui.developer.DeveloperActivity
import com.qyvos.app.ui.settings.SettingsActivity
import com.qyvos.app.ui.execution.ExecutionPanelAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()

    private lateinit var messageAdapter: MessageAdapter
    private lateinit var executionAdapter: ExecutionPanelAdapter

    private var isExecutionPanelExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupMessageList()
        setupExecutionPanel()
        setupInputArea()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnDeveloper.setOnClickListener {
            startActivity(Intent(this, DeveloperActivity::class.java))
        }
        binding.btnNewChat.setOnClickListener {
            viewModel.createNewSession()
        }
        binding.btnClear.setOnClickListener {
            viewModel.clearCurrentSession()
        }
    }

    private fun setupMessageList() {
        messageAdapter = MessageAdapter()
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).also {
                it.stackFromEnd = true
            }
            adapter = messageAdapter
            itemAnimator = null
        }
    }

    private fun setupExecutionPanel() {
        executionAdapter = ExecutionPanelAdapter()
        binding.rvExecutionLogs.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).also {
                it.stackFromEnd = true
            }
            adapter = executionAdapter
        }

        binding.btnTogglePanel.setOnClickListener {
            toggleExecutionPanel()
        }

        binding.btnExpandPanel.setOnClickListener {
            toggleExecutionPanel()
        }

        // Initially collapsed
        binding.executionPanelContainer.visibility = View.GONE
    }

    private fun toggleExecutionPanel() {
        isExecutionPanelExpanded = !isExecutionPanelExpanded
        if (isExecutionPanelExpanded) {
            binding.executionPanelContainer.visibility = View.VISIBLE
            binding.executionPanelContainer.startAnimation(
                AnimationUtils.loadAnimation(this, R.anim.slide_down)
            )
            binding.btnTogglePanel.setImageResource(R.drawable.ic_panel_collapse)
        } else {
            binding.executionPanelContainer.startAnimation(
                AnimationUtils.loadAnimation(this, R.anim.slide_up)
            )
            binding.executionPanelContainer.visibility = View.GONE
            binding.btnTogglePanel.setImageResource(R.drawable.ic_panel_expand)
        }
    }

    private fun setupInputArea() {
        binding.btnSend.setOnClickListener {
            val input = binding.etInput.text?.toString()?.trim() ?: return@setOnClickListener
            if (input.isNotBlank()) {
                viewModel.sendMessage(input)
                binding.etInput.setText("")
            }
        }

        binding.btnCancel.setOnClickListener {
            viewModel.cancelCurrentTask()
            Toast.makeText(this, "Task cancelled", Toast.LENGTH_SHORT).show()
        }

        binding.btnVoice.setOnClickListener {
            Toast.makeText(this, "Voice input coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                messageAdapter.submitList(state.messages)
                if (state.messages.isNotEmpty()) {
                    binding.rvMessages.smoothScrollToPosition(state.messages.size - 1)
                }
                binding.inputLayout.isEnabled = state.inputEnabled
                binding.btnSend.isEnabled = state.inputEnabled
                binding.btnCancel.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                binding.progressIndicator.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                binding.tvStatus.text = when {
                    state.isLoading    -> "Agent is thinking..."
                    state.error != null -> "Error: ${state.error}"
                    else                -> "Ready"
                }
                state.error?.let {
                    Toast.makeText(this@ChatActivity, it, Toast.LENGTH_LONG).show()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.executionLogs.collectLatest { output ->
                executionAdapter.addLog(output)
                binding.rvExecutionLogs.scrollToPosition(executionAdapter.itemCount - 1)
                // Auto-show panel when activity starts
                if (!isExecutionPanelExpanded && output.logLevel in listOf(
                        LogLevel.STEP, LogLevel.TOOL_CALL, LogLevel.THINKING
                    )) {
                    binding.badgeActivity.visibility = View.VISIBLE
                }
                // Update browser URL if provided
                output.browserUrl?.let { url ->
                    binding.tvBrowserUrl.text = url
                }
            }
        }

        lifecycleScope.launch {
            viewModel.streamingMessage.collectLatest { streaming ->
                if (streaming != null) {
                    binding.tvStreamingIndicator.text = streaming.take(120)
                    binding.tvStreamingIndicator.visibility = View.VISIBLE
                } else {
                    binding.tvStreamingIndicator.visibility = View.GONE
                }
            }
        }

        lifecycleScope.launch {
            viewModel.browserUrl.collectLatest { url ->
                if (url != null) {
                    binding.tvBrowserUrl.text = url
                    binding.browserUrlBar.visibility = View.VISIBLE
                }
            }
        }
    }
}
