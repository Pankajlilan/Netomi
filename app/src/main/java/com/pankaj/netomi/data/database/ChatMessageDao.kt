package com.pankaj.netomi.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pankaj.netomi.data.models.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    
    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesByChatId(chatId: String): Flow<List<ChatMessage>>
    
    @Query("SELECT * FROM chat_messages WHERE isSent = 0 ORDER BY timestamp ASC")
    suspend fun getUnsentMessages(): List<ChatMessage>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)
    
    @Update
    suspend fun updateMessage(message: ChatMessage)
    
    @Query("DELETE FROM chat_messages WHERE chatId = :chatId")
    suspend fun deleteMessagesByChatId(chatId: String)
    
    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessageForChat(chatId: String): ChatMessage?
}
