package com.qyvos.app.ui.execution

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.qyvos.app.R
import com.qyvos.app.data.models.LogLevel
import com.qyvos.app.engine.AgentOutput
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExecutionPanelAdapter : RecyclerView.Adapter<ExecutionPanelAdapter.LogViewHolder>() {

    private val logs = mutableListOf<AgentOutput>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun addLog(output: AgentOutput) {
        logs.add(output)
        if (logs.size > 500) logs.removeAt(0)
        notifyItemInserted(logs.size - 1)
    }

    fun clearLogs() {
        logs.clear()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = logs.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder =
        LogViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_execution_log, parent, false))

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position])
    }

    inner class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTime:    TextView = view.findViewById(R.id.tvTime)
        private val tvLevel:   TextView = view.findViewById(R.id.tvLevel)
        private val tvMessage: TextView = view.findViewById(R.id.tvMessage)

        fun bind(output: AgentOutput) {
            tvTime.text = timeFormat.format(Date(System.currentTimeMillis()))

            val (levelText, levelColor) = when (output.logLevel) {
                LogLevel.DEBUG       -> "DBG" to R.color.log_debug
                LogLevel.INFO        -> "INF" to R.color.log_info
                LogLevel.WARNING     -> "WRN" to R.color.log_warning
                LogLevel.ERROR       -> "ERR" to R.color.log_error
                LogLevel.STEP        -> "STP" to R.color.log_step
                LogLevel.TOOL_CALL   -> "TOOL" to R.color.log_tool
                LogLevel.TOOL_RESULT -> "RES" to R.color.log_result
                LogLevel.THINKING    -> "THK" to R.color.log_thinking
            }
            tvLevel.text = levelText
            tvLevel.setTextColor(ContextCompat.getColor(itemView.context, levelColor))

            val prefix = output.toolName?.let { "[${it}] " } ?: ""
            val stepPrefix = if (output.step > 0) "Step ${output.step}: " else ""
            tvMessage.text = "$stepPrefix$prefix${output.content}"

            tvMessage.setTextColor(ContextCompat.getColor(
                itemView.context,
                if (output.logLevel == LogLevel.ERROR) R.color.log_error else R.color.terminal_text
            ))
        }
    }
}
