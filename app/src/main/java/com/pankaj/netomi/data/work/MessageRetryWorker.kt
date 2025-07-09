package com.pankaj.netomi.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pankaj.netomi.data.repository.ChatRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MessageRetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val chatRepository: ChatRepository
) : CoroutineWorker(context, workerParams) {
    
    override suspend fun doWork(): Result {
        return try {
            chatRepository.retryUnsentMessages()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
