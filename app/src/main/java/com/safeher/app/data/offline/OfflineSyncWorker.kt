package com.safeher.app.data.offline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class OfflineSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        OfflineSyncRepository.get(applicationContext).syncPending()
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}