package com.pankaj.netomi.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "chats")
data class Chat(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val lastMessage: String = "",
    val lastMessageTime: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val isActive: Boolean = true
)
