package com.pankaj.netomi.data.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class QueuedMessage(
    val message: String,
    val chatId: String,
    val timestamp: Long = System.currentTimeMillis(),
    var retryCount: Int = 0
)

@Singleton
class SocketService @Inject constructor() {
    
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    
    // Coroutine scope for retry operations
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Keep track of sent messages to filter them out when received
    private val sentMessages = mutableSetOf<String>()
    
    // Message queue for offline functionality
    private val messageQueue = mutableListOf<QueuedMessage>()
    private val maxRetryAttempts = 3
    private val retryDelayMs = 2000L
    
    // Flag to simulate offline mode (for testing)
    private var isOfflineSimulated = false
    
    // Connection management
    private var isConnecting = false
    private var shouldReconnect = true
    private val reconnectDelayMs = 5000L
    private val maxReconnectAttempts = 5
    private var reconnectAttempts = 0
    
    // Use replay buffer to ensure messages aren't lost when no subscribers are active
    private val _messageReceived = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 10
    )
    val messageReceived: SharedFlow<String> = _messageReceived.asSharedFlow()
    
    private val _connectionStatus = MutableSharedFlow<Boolean>(
        replay = 1,
        extraBufferCapacity = 1
    )
    val connectionStatus: SharedFlow<Boolean> = _connectionStatus.asSharedFlow()
    
    // Queue status for monitoring
    private val _queueStatus = MutableSharedFlow<Int>(
        replay = 1,
        extraBufferCapacity = 1
    )
    val queueStatus: SharedFlow<Int> = _queueStatus.asSharedFlow()

    companion object {
        private const val TAG = "SocketService"
    }
    
    // Method to simulate offline mode for testing
    fun setOfflineMode(offline: Boolean) {
        isOfflineSimulated = offline
        Log.d(TAG, "Offline mode simulated: $offline")
        if (!offline && isConnected()) {
            // When coming back online, try to send queued messages
            processQueuedMessages()
        }
    }
    
    fun getQueuedMessageCount(): Int = messageQueue.size
    
    fun connect() {
        if (isConnecting) {
            Log.d(TAG, "Connection already in progress, skipping...")
            return // Prevent multiple concurrent connections
        }
        
        // Reset reconnect attempts when manually calling connect
        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.d(TAG, "Resetting reconnect attempts for manual connection")
            reconnectAttempts = 0
        }
        
        isConnecting = true
        
        try {
            Log.d(TAG, "Attempting to connect to pie.host WebSocket...")
            val request = Request.Builder()
                .url("wss://s14920.nyc1.piesocket.com/v3/1?api_key=AnR9h1nPCnkKRS0Ee1lPQEieRGdgEFY2MDGA9yRF&notify_self=1")
                .build()
            
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket connection opened successfully")
                    Log.d(TAG, "Response: ${response.message}")
                    _connectionStatus.tryEmit(true)
                    isConnecting = false
                    reconnectAttempts = 0 // Reset reconnect attempts on successful connection
                    
                    // Immediately process queued messages on successful connection
                    processQueuedMessages()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "Raw message received from pie.host: $text")
                    
                    // Handle different message formats from pie.host
                    val processedMessage = processIncomingMessage(text)
                    
                    // Only emit if it's not a message we sent ourselves
                    if (processedMessage != null && !isSelfSentMessage(processedMessage)) {
                        Log.d(TAG, "Processing external message: $processedMessage")
                        val emitResult = _messageReceived.tryEmit(processedMessage)
                        Log.d(TAG, "Message emit result: $emitResult")
                    } else {
                        Log.d(TAG, "Ignoring self-sent or null message: $processedMessage")
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val message = bytes.utf8()
                    Log.d(TAG, "Binary message received: $message")
                    _messageReceived.tryEmit(message)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closing: code=$code, reason=$reason")
                    _connectionStatus.tryEmit(false)
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: code=$code, reason=$reason")
                    _connectionStatus.tryEmit(false)
                    isConnecting = false
                    
                    // Attempt to reconnect if enabled and not manually closed
                    if (shouldReconnect && reconnectAttempts < maxReconnectAttempts && reason != "Client closing") {
                        reconnectAttempts++
                        Log.d(TAG, "Reconnecting in ${reconnectDelayMs / 1000} seconds... (Attempt $reconnectAttempts of $maxReconnectAttempts)")
                        serviceScope.launch {
                            delay(reconnectDelayMs)
                            connect() // Re-attempt connection
                        }
                    } else {
                        Log.w(TAG, "Not reconnecting: shouldReconnect=$shouldReconnect, attempts=$reconnectAttempts/$maxReconnectAttempts, reason=$reason")
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure: ${t.message}", t)
                    Log.e(TAG, "Response: ${response?.message}")
                    _connectionStatus.tryEmit(false)
                    isConnecting = false
                    
                    // Check if it's a DNS/network error
                    val isDnsError = t.message?.contains("Unable to resolve host") == true ||
                                    t.message?.contains("No address associated with hostname") == true ||
                                    t.message?.contains("UnknownHostException") == true
                    
                    if (isDnsError) {
                        Log.w(TAG, "DNS resolution failed - will retry when network improves")
                        // For DNS errors, increase the delay before retry
                        val dnsRetryDelay = reconnectDelayMs * 2
                        
                        if (shouldReconnect && reconnectAttempts < maxReconnectAttempts) {
                            reconnectAttempts++
                            Log.d(TAG, "DNS retry in ${dnsRetryDelay / 1000} seconds... (Attempt $reconnectAttempts of $maxReconnectAttempts)")
                            serviceScope.launch {
                                delay(dnsRetryDelay)
                                connect() // Re-attempt connection
                            }
                        }
                    } else {
                        // Regular connection failure
                        if (shouldReconnect && reconnectAttempts < maxReconnectAttempts) {
                            reconnectAttempts++
                            Log.d(TAG, "Reconnecting in ${reconnectDelayMs / 1000} seconds... (Attempt $reconnectAttempts of $maxReconnectAttempts)")
                            serviceScope.launch {
                                delay(reconnectDelayMs)
                                connect() // Re-attempt connection
                            }
                        }
                    }
                    
                    if (reconnectAttempts >= maxReconnectAttempts) {
                        Log.w(TAG, "Max reconnect attempts reached. Will try again when network status changes.")
                    }
                }
            }
            
            webSocket = client.newWebSocket(request, listener)
            Log.d(TAG, "WebSocket request initiated")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to WebSocket", e)
            e.printStackTrace()
            _connectionStatus.tryEmit(false)
            isConnecting = false
        }
    }
    
    private fun processIncomingMessage(rawMessage: String): String? {
        return try {
            // Check if it's JSON
            if (rawMessage.trim().startsWith("{") || rawMessage.trim().startsWith("[")) {
                val jsonObject = JSONObject(rawMessage)
                Log.d(TAG, "Parsed JSON object: $jsonObject")
                
                when {
                    jsonObject.has("data") -> {
                        val data = jsonObject.getJSONObject("data")
                        Log.d(TAG, "Found data object: $data")
                        data.optString("message", data.toString())
                    }
                    jsonObject.has("message") -> {
                        val msg = jsonObject.getString("message")
                        Log.d(TAG, "Found direct message: $msg")
                        msg
                    }
                    jsonObject.has("event") -> {
                        // Handle pie.host event messages
                        val event = jsonObject.getString("event")
                        Log.d(TAG, "Received event: $event")
                        if (event == "new_message" && jsonObject.has("data")) {
                            val data = jsonObject.getJSONObject("data")
                            data.optString("message", data.toString())
                        } else {
                            null // Ignore system events
                        }
                    }
                    else -> {
                        Log.d(TAG, "Unknown JSON format, treating as plain text")
                        rawMessage
                    }
                }
            } else {
                // It's plain text from pie.host
                Log.d(TAG, "Plain text message detected: $rawMessage")
                rawMessage
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message: $rawMessage", e)
            // If parsing fails, treat as plain text
            rawMessage
        }
    }
    
    private fun isSelfSentMessage(message: String): Boolean {
        // Check if this message was recently sent by us
        val wasSelfSent = sentMessages.contains(message)
        if (wasSelfSent) {
            Log.d(TAG, "Filtering out self-sent message: $message")
            // Remove from set to prevent memory leaks
            sentMessages.remove(message)
        }
        return wasSelfSent
    }
    
    fun sendMessage(message: String, chatId: String) {
        // Check if offline mode is simulated
        if (isOfflineSimulated) {
            Log.d(TAG, "Offline mode simulated - queuing message: $message")
            queueMessageForSending(message, chatId)
            return
        }
        
        webSocket?.let { socket ->
            try {
                Log.d(TAG, "Sending message: $message for chatId: $chatId")
                
                // Track the message content to filter it out when received
                sentMessages.add(message)
                
                // Send as plain text to pie.host (they handle plain text better)
                Log.d(TAG, "Sending plain text: $message")
                val result = socket.send(message)
                Log.d(TAG, "Send result: $result")
                
                if (!result) {
                    // If send failed, queue the message
                    Log.w(TAG, "Failed to send message, queuing for retry: $message")
                    sentMessages.remove(message) // Remove from sent tracking since it failed
                    queueMessageForSending(message, chatId)
                } else {
                    // Clean up old sent messages to prevent memory issues
                    cleanupOldMessages()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message", e)
                // Remove from tracking if send failed
                sentMessages.remove(message)
                // Queue the message for retry
                queueMessageForSending(message, chatId)
            }
        } ?: run {
            Log.w(TAG, "WebSocket is null, cannot send message")
            // If WebSocket is not connected, queue the message for later
            queueMessageForSending(message, chatId)
        }
    }
    
    private fun queueMessageForSending(message: String, chatId: String) {
        // Add to queue
        messageQueue.add(QueuedMessage(message, chatId))
        Log.d(TAG, "Message queued for sending: $message")
        
        // Emit queue status
        _queueStatus.tryEmit(messageQueue.size)
        
        // If offline, no need to retry immediately
        if (isOfflineSimulated) return
        
        // Retry sending the message after a delay
        serviceScope.launch {
            delay(retryDelayMs)
            processQueuedMessages()
        }
    }
    
    private fun processQueuedMessages() {
        // Process the queue in a separate coroutine
        serviceScope.launch {
            for (queuedMessage in messageQueue.toList()) {
                // Check if we've exceeded the max retry attempts
                if (queuedMessage.retryCount >= maxRetryAttempts) {
                    Log.w(TAG, "Max retry attempts reached for message: ${queuedMessage.message}")
                    // Remove from queue
                    messageQueue.remove(queuedMessage)
                    // Emit updated queue status
                    _queueStatus.tryEmit(messageQueue.size)
                    continue
                }
                
                // Try to send the message
                Log.d(TAG, "Retrying to send message: ${queuedMessage.message}")
                val result = webSocket?.send(queuedMessage.message) ?: false
                
                if (result) {
                    Log.d(TAG, "Message sent successfully: ${queuedMessage.message}")
                    // Remove from queue
                    messageQueue.remove(queuedMessage)
                } else {
                    Log.e(TAG, "Failed to send message, will retry: ${queuedMessage.message}")
                    // Increment retry count and schedule next retry
                    queuedMessage.retryCount++
                    delay(retryDelayMs)
                }
                
                // Emit updated queue status
                _queueStatus.tryEmit(messageQueue.size)
            }
        }
    }
    
    private fun cleanupOldMessages() {
        if (sentMessages.size > 50) {
            val iterator = sentMessages.iterator()
            repeat(10) {
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
    }
    
    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket...")
        webSocket?.close(1000, "Client closing")
        webSocket = null
        sentMessages.clear()
        messageQueue.clear()
    }
    
    fun isConnected(): Boolean {
        return webSocket != null
    }
}
