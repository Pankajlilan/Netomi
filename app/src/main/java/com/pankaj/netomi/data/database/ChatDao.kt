package com.pankaj.netomi.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pankaj.netomi.data.models.Chat
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    
    @Query("SELECT * FROM chats WHERE isActive = 1 ORDER BY lastMessageTime DESC")
    fun getAllActiveChats(): Flow<List<Chat>>
    
    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChatById(chatId: String): Chat?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: Chat)
    
    @Update
    suspend fun updateChat(chat: Chat)
    
    @Query("DELETE FROM chats")
    suspend fun deleteAllChats()
    
    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChatById(chatId: String)
    
    @Query("DELETE FROM chats WHERE id IN (:chatIds)")
    suspend fun deleteChatsByIds(chatIds: List<String>)
    
    @Query("UPDATE chats SET unreadCount = 0 WHERE id = :chatId")
    suspend fun markChatAsRead(chatId: String)
}
