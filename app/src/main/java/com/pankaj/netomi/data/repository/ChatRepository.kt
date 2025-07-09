package com.pankaj.netomi.data.repository

import android.util.Log
import com.pankaj.netomi.data.database.ChatDao
import com.pankaj.netomi.data.database.ChatMessageDao
import com.pankaj.netomi.data.models.Chat
import com.pankaj.netomi.data.models.ChatMessage
import com.pankaj.netomi.data.network.NetworkConnectivityManager
import com.pankaj.netomi.data.network.SocketService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val chatMessageDao: ChatMessageDao,
    private val socketService: SocketService,
    private val networkConnectivityManager: NetworkConnectivityManager
) {
    
    companion object {
        private const val TAG = "ChatRepository"
    }
    
    fun getAllChats(): Flow<List<Chat>> = chatDao.getAllActiveChats()
    
    fun getMessagesByChatId(chatId: String): Flow<List<ChatMessage>> =
        chatMessageDao.getMessagesByChatId(chatId)
    
    val isNetworkConnected: Flow<Boolean> = networkConnectivityManager.isConnected
    val messageReceived: Flow<String> = socketService.messageReceived
    val connectionStatus: Flow<Boolean> = socketService.connectionStatus
    
    fun connectSocket() {
        Log.d(TAG, "Connecting to WebSocket...")
        socketService.connect()
    }
    
    fun disconnectSocket() {
        Log.d(TAG, "Disconnecting WebSocket...")
        socketService.disconnect()
    }
    
    suspend fun sendMessage(content: String, chatId: String): Boolean {
        Log.d(TAG, "Sending message: '$content' to chat: $chatId")
        val message = ChatMessage(
            chatId = chatId,
            content = content,
            isFromBot = false,
            timestamp = System.currentTimeMillis(),
            isSent = networkConnectivityManager.isConnected.first() && socketService.isConnected()
        )
        
        chatMessageDao.insertMessage(message)
        Log.d(TAG, "Message saved to database: ${message.id}")
        
        // Update chat with latest message
        updateChatWithLastMessage(chatId, content)
        
        return if (message.isSent) {
            socketService.sendMessage(content, chatId)
            Log.d(TAG, "Message sent via WebSocket")
            true
        } else {
            Log.w(TAG, "Message queued - no connection")
            false
        }
    }
    
    suspend fun receiveMessage(content: String, chatId: String) {
        Log.d(TAG, "Receiving message: '$content' for chat: $chatId")
        val message = ChatMessage(
            chatId = chatId,
            content = content,
            isFromBot = true,
            timestamp = System.currentTimeMillis(),
            isSent = true,
            isDelivered = true
        )
        
        chatMessageDao.insertMessage(message)
        Log.d(TAG, "Received message saved to database: ${message.id}")
        
        // Update chat with latest message and increment unread count
        val chat = chatDao.getChatById(chatId)
        chat?.let {
            val updatedChat = it.copy(
                lastMessage = content,
                lastMessageTime = System.currentTimeMillis(),
                unreadCount = it.unreadCount + 1
            )
            chatDao.updateChat(updatedChat)
            Log.d(TAG, "Chat updated with received message")
        } ?: Log.w(TAG, "Chat not found for ID: $chatId")
    }
    
    private suspend fun updateChatWithLastMessage(chatId: String, content: String) {
        val chat = chatDao.getChatById(chatId)
        chat?.let {
            val updatedChat = it.copy(
                lastMessage = content,
                lastMessageTime = System.currentTimeMillis()
            )
            chatDao.updateChat(updatedChat)
        }
    }
    
    suspend fun retryUnsentMessages() {
        if (networkConnectivityManager.isConnected.first() && socketService.isConnected()) {
            val unsentMessages = chatMessageDao.getUnsentMessages()
            unsentMessages.forEach { message ->
                socketService.sendMessage(message.content, message.chatId)
                val updatedMessage = message.copy(isSent = true)
                chatMessageDao.updateMessage(updatedMessage)
            }
        }
    }
    
    suspend fun createNewChat(title: String): Chat {
        val chat = Chat(
            title = title,
            lastMessage = "",
            lastMessageTime = System.currentTimeMillis()
        )
        chatDao.insertChat(chat)
        return chat
    }
    
    suspend fun markChatAsRead(chatId: String) {
        chatDao.markChatAsRead(chatId)
    }
    
    suspend fun clearAllChats() {
        chatDao.deleteAllChats()
    }
    
    suspend fun deleteChat(chatId: String) {
        Log.d(TAG, "Deleting chat: $chatId")
        // First delete all messages associated with this chat
        chatMessageDao.deleteMessagesByChatId(chatId)
        // Then delete the chat itself
        chatDao.deleteChatById(chatId)
        Log.d(TAG, "Chat deleted successfully: $chatId")
    }
    
    suspend fun deleteChats(chatIds: List<String>) {
        Log.d(TAG, "Deleting ${chatIds.size} chats")
        // Delete all messages for these chats
        chatIds.forEach { chatId ->
            chatMessageDao.deleteMessagesByChatId(chatId)
        }
        // Delete the chats
        chatDao.deleteChatsByIds(chatIds)
        Log.d(TAG, "Successfully deleted ${chatIds.size} chats")
    }
}
