package com.pankaj.netomi.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val chatId: String,
    val content: String,
    val isFromBot: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isSent: Boolean = false,
    val isDelivered: Boolean = false,
    val isRead: Boolean = false
)
