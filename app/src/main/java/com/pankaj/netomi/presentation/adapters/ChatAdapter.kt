package com.pankaj.netomi.presentation.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pankaj.netomi.data.models.Chat
import com.pankaj.netomi.databinding.ItemChatBinding

class ChatAdapter(
    private val onChatClick: (Chat) -> Unit,
    private val onChatLongClick: (Chat) -> Unit,
    private val onSelectionChanged: (List<Chat>) -> Unit
) : ListAdapter<Chat, ChatAdapter.ChatViewHolder>(ChatDiffCallback()) {

    private var isSelectionMode = false
    private val selectedChats = mutableSetOf<String>()

    fun setSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        if (!enabled) {
            selectedChats.clear()
        }
        notifyDataSetChanged()
        onSelectionChanged(getSelectedChats())
    }

    fun getSelectedChats(): List<Chat> {
        return currentList.filter { selectedChats.contains(it.id) }
    }

    fun isInSelectionMode(): Boolean = isSelectionMode

    fun selectAll() {
        selectedChats.clear()
        selectedChats.addAll(currentList.map { it.id })
        notifyDataSetChanged()
        onSelectionChanged(getSelectedChats())
    }

    fun clearSelection() {
        selectedChats.clear()
        notifyDataSetChanged()
        onSelectionChanged(getSelectedChats())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChatViewHolder(private val binding: ItemChatBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(chat: Chat) {
            binding.chat = chat
            binding.isSelectionMode = isSelectionMode
            binding.isSelected = selectedChats.contains(chat.id)

            binding.clickListener = View.OnClickListener {
                if (isSelectionMode) {
                    toggleSelection(chat)
                } else {
                    onChatClick(chat)
                }
            }

            binding.longClickListener = View.OnLongClickListener {
                if (!isSelectionMode) {
                    // First enter selection mode, then select this item
                    onChatLongClick(chat)
                    // Add a small delay to ensure selection mode is set before selecting
                    binding.root.post {
                        toggleSelection(chat)
                    }
                }
                true
            }

            binding.executePendingBindings()
        }

        private fun toggleSelection(chat: Chat) {
            if (selectedChats.contains(chat.id)) {
                selectedChats.remove(chat.id)
            } else {
                selectedChats.add(chat.id)
            }
            // Update this specific item's binding immediately
            binding.isSelected = selectedChats.contains(chat.id)
            binding.executePendingBindings()

            // Notify the fragment about selection changes
            onSelectionChanged(getSelectedChats())
        }
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<Chat>() {
        override fun areItemsTheSame(oldItem: Chat, newItem: Chat): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Chat, newItem: Chat): Boolean {
            return oldItem == newItem
        }
    }
}
