package com.pankaj.netomi.hilt

import android.content.Context
import androidx.room.Room
import com.pankaj.netomi.data.database.ChatDatabase
import com.pankaj.netomi.data.database.ChatDao
import com.pankaj.netomi.data.database.ChatMessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ChatDatabase {
        return Room.databaseBuilder(
            context,
            ChatDatabase::class.java,
            "chat_database"
        ).build()
    }
    
    @Provides
    fun provideChatDao(database: ChatDatabase): ChatDao {
        return database.chatDao()
    }
    
    @Provides
    fun provideChatMessageDao(database: ChatDatabase): ChatMessageDao {
        return database.chatMessageDao()
    }
}
