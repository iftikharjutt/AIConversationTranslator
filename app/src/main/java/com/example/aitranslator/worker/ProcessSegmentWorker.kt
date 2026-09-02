package com.example.aitranslator.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.aitranslator.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ProcessSegmentWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val processor: SegmentProcessor
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val segmentId = inputData.getLong(Constants.WORKER_SEGMENT_ID_KEY, -1L)
        val conversationId = inputData.getLong(Constants.WORKER_CONVERSATION_ID_KEY, -1L)
        if (segmentId == -1L || conversationId == -1L) return Result.failure()

        return when (processor.process(segmentId, conversationId)) {
            SegmentProcessor.ProcessingResult.SUCCESS -> Result.success()
            SegmentProcessor.ProcessingResult.RETRY -> Result.retry()
            SegmentProcessor.ProcessingResult.FAILED -> Result.failure()
        }
    }

    companion object {
        fun isTransientError(throwable: Throwable?): Boolean {
            if (throwable == null) return false
            val message = throwable.message.orEmpty().lowercase()
            if (message.contains("401") || message.contains("403") ||
                (message.contains("invalid") && message.contains("key")) ||
                message.contains("unauthorized") || message.contains("forbidden") ||
                message.contains("404") || throwable is IllegalArgumentException) return false
            return throwable is java.io.IOException ||
                message.contains("429") || message.contains("rate limit") ||
                message.contains("quota") || message.contains("too many requests") ||
                message.contains("500") || message.contains("503") ||
                message.contains("timeout") || message.contains("connect") ||
                message.contains("socket") || message.contains("reset")
        }
    }
}
