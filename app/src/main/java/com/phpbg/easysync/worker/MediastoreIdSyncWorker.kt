/*
 * MIT License
 *
 * Copyright (c) 2023 Samuel CHEMLA
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.phpbg.easysync.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.phpbg.easysync.mediastore.MediaStoreService

private const val TAG = "MediastoreIdSyncWorker"
private const val DATA_ID_KEY = "id"
private const val SKIP_IF_IN_DB_KEY = "db"

class MediastoreIdSyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        WorkersSemaphore.acquire()
        try {
            return if (WorkersSemaphore.isConnected(applicationContext)) _doWork() else Result.retry()
        } finally {
            WorkersSemaphore.release()
        }
    }

    private suspend fun _doWork(): Result {
        val id = inputData.getLong(DATA_ID_KEY, -1)
        val skipIfInDb = inputData.getBoolean(SKIP_IF_IN_DB_KEY, false)
        Log.d(TAG, "Syncing $id - skip if in DB: $skipIfInDb")
        val syncService = SyncService.getInstance(this.applicationContext)
        val mediaStoreService = MediaStoreService(this.applicationContext)
        val file = mediaStoreService.getById(id)
        if (file== null) {
            Log.d(TAG, "No entry in mediastore for $id")
            return Result.success()
        }

        Log.d(TAG, "Syncing $file")
        return try {
            syncService.syncOne(file, skipIfInDb)
            Result.success()
        } catch (e: Exception) {
            Log.i(TAG, "Error while syncing: $file attempt:$runAttemptCount")
            Log.e(TAG, e.toString())
            Log.d(TAG, e.stackTraceToString())
            if (runAttemptCount < WorkersConstants.MAX_RUN_ATTEMPTS) {
                // We don't mind too many errors: file will be synced anyway with periodic full sync worker
                Result.retry()
            } else {
                syncService.handleWorkerException(applicationContext, e, file.relativePath)
                Result.failure()
            }
        }
    }

    companion object {
        suspend fun enqueue(
            context: Context,
            id: Long,
            immediate: Boolean,
            skipIfInDb: Boolean
        ) {
            Log.d(TAG, "Enqueue $id immediate:$immediate skipIfInDb:$skipIfInDb")
            val constraints = ConstraintBuilder.getFullSyncConstraints(context, immediate).build()
            val data = Data.Builder()
                .putLong(DATA_ID_KEY, id)
                .putBoolean(SKIP_IF_IN_DB_KEY, skipIfInDb)
                .build()
            val syncWorkRequest =
                OneTimeWorkRequestBuilder<MediastoreIdSyncWorker>()
                    .setInputData(data)
                    .setConstraints(constraints)
                    .addTag(WorkersConstants.TAG)
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("ID_$id", ExistingWorkPolicy.REPLACE, syncWorkRequest)
        }
    }
}