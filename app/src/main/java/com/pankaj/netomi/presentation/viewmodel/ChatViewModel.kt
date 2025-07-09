@file:OptIn(ExperimentalCoroutinesApi::class)

package com.pankaj.netomi.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.pankaj.netomi.data.models.Chat
import com.pankaj.netomi.data.models.ChatMessage
import com.pankaj.netomi.data.repository.ChatRepository
import com.pankaj.netomi.data.work.MessageRetryWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val workManager: WorkManager
) : ViewModel() {
    
    companion object {
        private const val TAG = "ChatViewModel"
    }
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()
    
    // Add navigation event for new chat creation
    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()
    
    val chats: StateFlow<List<Chat>> = chatRepository.getAllChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val isNetworkConnected: StateFlow<Boolean> = chatRepository.isNetworkConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    val messages: StateFlow<List<ChatMessage>> = selectedChatId
        .filterNotNull()
        .flatMapLatest { chatId ->
            Log.d(TAG, "Loading messages for chat: $chatId")
            chatRepository.getMessagesByChatId(chatId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    init {
        Log.d(TAG, "ChatViewModel initialized")
        connectToSocket()
        observeIncomingMessages()
        observeNetworkChanges()
    }
    
    private fun connectToSocket() {
        Log.d(TAG, "Connecting to socket from ViewModel")
        chatRepository.connectSocket()
    }
    
    private fun observeIncomingMessages() {
        Log.d(TAG, "Setting up message observer")
        viewModelScope.launch {
            chatRepository.messageReceived.collect { message ->
                Log.d(TAG, "Message received in ViewModel: '$message'")
                
                // Get or create a chat for incoming messages
                val currentChatId = _selectedChatId.value ?: run {
                    Log.d(TAG, "No chat selected, creating new chat for incoming message")
                    val newChat = chatRepository.createNewChat("PieSocket Chat")
                    _selectedChatId.value = newChat.id
                    newChat.id
                }
                
                Log.d(TAG, "Processing incoming message for chat: $currentChatId")
                
                // Save the received message to database and display it
                chatRepository.receiveMessage(message, currentChatId)
                Log.d(TAG, "Incoming message processed and saved: '$message'")
            }
        }
    }
    
    private fun observeNetworkChanges() {
        viewModelScope.launch {
            isNetworkConnected.collect { isConnected ->
                Log.d(TAG, "Network connectivity changed: $isConnected")
                if (isConnected) {
                    Log.d(TAG, "Network is back online - reconnecting WebSocket and retrying messages")
                    // Reconnect WebSocket when network comes back online
                    chatRepository.connectSocket()
                    // Schedule retry for queued messages
                    scheduleMessageRetry()
                } else {
                    Log.d(TAG, "Network disconnected")
                    // Optionally disconnect socket to clean up resources
                    chatRepository.disconnectSocket()
                }
            }
        }
    }
    
    private suspend fun createDemoChat(): String {
        val chat = chatRepository.createNewChat("Chatbot ${System.currentTimeMillis()}")
        return chat.id
    }
    
    fun sendMessage(content: String) {
        viewModelScope.launch {
            val currentChatId = _selectedChatId.value
            if (currentChatId != null) {
                val success = chatRepository.sendMessage(content, currentChatId)
                if (!success) {
                    _uiState.value = _uiState.value.copy(
                        error = "Message queued for retry when connection is restored"
                    )
                }
                
                // Simulate bot response after a delay
                kotlinx.coroutines.delay(1000)
                simulateBotResponse(currentChatId, content)
            }
        }
    }
    
    private suspend fun simulateBotResponse(chatId: String, userMessage: String) {
        val botResponses = listOf(
            "That's interesting! Tell me more.",
            "I understand. How can I help you with that?",
            "Thanks for sharing that information.",
            "I'm here to help. What would you like to know?",
            "That's a great question. Let me think about that."
        )
        
        val response = botResponses.random()
        chatRepository.receiveMessage(response, chatId)
    }
    
    fun selectChat(chatId: String) {
        _selectedChatId.value = chatId
        viewModelScope.launch {
            chatRepository.markChatAsRead(chatId)
        }
    }
    
    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            try {
                chatRepository.deleteChat(chatId)
                Log.d(TAG, "Chat deleted successfully: $chatId")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting chat: $chatId", e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to delete chat"
                )
            }
        }
    }
    
    fun deleteSelectedChats(chats: List<Chat>) {
        viewModelScope.launch {
            try {
                val chatIds = chats.map { it.id }
                chatRepository.deleteChats(chatIds)
                Log.d(TAG, "Successfully deleted ${chats.size} chats")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting chats", e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to delete chats"
                )
            }
        }
    }
    
    fun createNewChat() {
        viewModelScope.launch {
            val chat = chatRepository.createNewChat("New Chat ${System.currentTimeMillis()}")
            _selectedChatId.value = chat.id
            // Emit navigation event to navigate to the new chat screen with both ID and title
            _navigationEvent.emit(NavigationEvent.NavigateToChat(chat.id, chat.title))
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun clearAllChats() {
        viewModelScope.launch {
            chatRepository.clearAllChats()
        }
    }
    
    private fun scheduleMessageRetry() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val retryRequest = OneTimeWorkRequestBuilder<MessageRetryWorker>()
            .setConstraints(constraints)
            .build()
        
        workManager.enqueue(retryRequest)
    }
    
    override fun onCleared() {
        super.onCleared()
        chatRepository.disconnectSocket()
        // Clear all chats when app is closed as per requirement
        viewModelScope.launch {
            chatRepository.clearAllChats()
        }
    }
}

data class ChatUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class NavigationEvent {
    data class NavigateToChat(val chatId: String, val title: String) : NavigationEvent()
}
