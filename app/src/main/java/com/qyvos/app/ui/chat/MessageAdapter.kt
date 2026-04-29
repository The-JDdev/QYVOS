package com.qyvos.app.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.qyvos.app.R
import com.qyvos.app.data.models.Message
import com.qyvos.app.data.models.MessageRole
import com.qyvos.app.data.models.MessageType
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin

class MessageAdapter : ListAdapter<Message, RecyclerView.ViewHolder>(MessageDiff()) {

    companion object {
        private const val VIEW_USER      = 0
        private const val VIEW_ASSISTANT = 1
        private const val VIEW_TOOL      = 2
        private const val VIEW_ERROR     = 3
    }

    private lateinit var markwon: Markwon

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        markwon = Markwon.builder(recyclerView.context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(recyclerView.context))
            .usePlugin(TaskListPlugin.create(recyclerView.context))
            .build()
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position).role) {
        MessageRole.USER      -> VIEW_USER
        MessageRole.ASSISTANT -> VIEW_ASSISTANT
        MessageRole.TOOL      -> VIEW_TOOL
        MessageRole.SYSTEM    -> VIEW_ASSISTANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_USER      -> UserViewHolder(inflater.inflate(R.layout.item_message_user, parent, false))
            VIEW_TOOL      -> ToolViewHolder(inflater.inflate(R.layout.item_message_tool, parent, false))
            VIEW_ERROR     -> ErrorViewHolder(inflater.inflate(R.layout.item_message_error, parent, false))
            else           -> AssistantViewHolder(inflater.inflate(R.layout.item_message_assistant, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is UserViewHolder      -> holder.bind(message)
            is AssistantViewHolder -> holder.bind(message, markwon)
            is ToolViewHolder      -> holder.bind(message)
            is ErrorViewHolder     -> holder.bind(message)
        }
    }

    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvContent: TextView = view.findViewById(R.id.tvContent)
        fun bind(message: Message) {
            tvContent.text = message.content
        }
    }

    inner class AssistantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvContent: TextView = view.findViewById(R.id.tvContent)
        private val tvThinking: TextView = view.findViewById(R.id.tvThinking)
        private val thinkingCard: MaterialCardView = view.findViewById(R.id.cardThinking)
        fun bind(message: Message, markwon: Markwon) {
            markwon.setMarkdown(tvContent, message.content)
            if (message.thinking != null) {
                thinkingCard.visibility = View.VISIBLE
                tvThinking.text = message.thinking
            } else {
                thinkingCard.visibility = View.GONE
            }
        }
    }

    inner class ToolViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvToolName: TextView = view.findViewById(R.id.tvToolName)
        private val tvContent: TextView  = view.findViewById(R.id.tvContent)
        fun bind(message: Message) {
            tvToolName.text = "🔧 ${message.toolName ?: "Tool"}"
            tvContent.text = message.content.take(500)
        }
    }

    inner class ErrorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvContent: TextView = view.findViewById(R.id.tvContent)
        fun bind(message: Message) {
            tvContent.text = message.content
        }
    }

    class MessageDiff : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(old: Message, new: Message) = old.id == new.id
        override fun areContentsTheSame(old: Message, new: Message) = old == new
    }
}
