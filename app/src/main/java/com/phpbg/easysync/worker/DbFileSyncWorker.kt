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
import com.phpbg.easysync.worker.WorkersConstants.MAX_RUN_ATTEMPTS
import java.util.concurrent.CancellationException

private const val TAG = "DbFileSyncWorker"
private const val DATA_DB_PATH_KEY = "db_path"

class DbFileSyncWorker(appContext: Context, workerParams: WorkerParameters) :
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
        val path = inputData.getString(DATA_DB_PATH_KEY)
        if (path == null) {
            Log.w(TAG, "Syncing null path")
            return Result.success()
        }

        Log.d(TAG, "Syncing $path")
        val syncService = SyncService.getInstance(this.applicationContext)
        return try {
            syncService.resyncFile(path)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error while syncing with remote path: $path attempt: $runAttemptCount")
            Log.e(TAG, e.toString())
            if (runAttemptCount < MAX_RUN_ATTEMPTS) {
                // We don't mind too many errors: file will be synced anyway with periodic full sync worker
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        suspend fun enqueue(
            context: Context,
            path: String,
            immediate: Boolean,
        ) {
            Log.d(TAG, "Enqueue $path immediate:$immediate")
            val constraints = ConstraintBuilder.getFullSyncConstraints(context, immediate).build()
            val data = Data.Builder()
                .putString(DATA_DB_PATH_KEY, path)
                .build()
            val syncWorkRequest =
                OneTimeWorkRequestBuilder<DbFileSyncWorker>()
                    .setInputData(data)
                    .setConstraints(constraints)
                    .addTag(WorkersConstants.TAG)
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("DB_$path", ExistingWorkPolicy.REPLACE, syncWorkRequest)
        }
    }
}