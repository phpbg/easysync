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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.phpbg.easysync.mediastore.MediaStoreService
import com.phpbg.easysync.mediastore.URIS

private const val TAG = "FileDetectWorker"

/**
 * Detect file changes
 * see https://developer.android.com/reference/android/app/job/JobInfo.Builder.html#addTriggerContentUri(android.app.job.JobInfo.TriggerContentUri)
 * see https://stackoverflow.com/questions/53581969/how-to-detect-new-photos-with-workmanager
 */
class FileDetectWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        try {
            _doWork()
        } catch (e: Exception) {
            Log.w(TAG, e)
        }
        enqueue(this.applicationContext)
        return Result.success()
    }

    private suspend fun _doWork() {
        // Logic from https://developer.android.com/reference/android/app/job/JobInfo.Builder.html#addTriggerContentUri(android.app.job.JobInfo.TriggerContentUri)
        // Did we trigger due to a content change?
        if (this.triggeredContentAuthorities.isEmpty()) {
            return
        }

        if (this.triggeredContentUris.isEmpty()) {
            // We don't have any details about URIs (because too many changed at once),
            // so just note that we need to do a full rescan.
            Log.d(TAG, "Full scan needed")
            FullSyncWorker.enqueueKeep(this.applicationContext)
            return
        }

        val mediaStoreService = MediaStoreService(this.applicationContext)
        for (uri in this.triggeredContentUris) {
            if ((uri.path?.count { it == '/' } ?: 0) <= 1) {
                // Workaround when deleting local files on android API 26: uri is: "content://media/external", which is invalid and will throw IllegalException
                Log.d(TAG, "Sync requested for invalid URI: $uri, replaced with full sync")
                FullSyncWorker.enqueueKeep(this.applicationContext)
            } else {
                Log.d(TAG, "Sync requested for $uri")
                val files = mediaStoreService.getByUri(uri)
                if (files.isEmpty()) {
                    Log.d(TAG, "No entry in mediastore for $uri")
                }
                for (file in files) {
                    Log.d(TAG, "Enqueue $file")
                    MediastoreIdSyncWorker.enqueue(
                        this.applicationContext,
                        file.id,
                        immediate = false,
                        skipIfInDb = false
                    )
                }
            }
        }
    }

    companion object {
        fun enqueue(context: Context) {
            Log.d(TAG, "Enqueue file detect")
            val constraintsBuilder = Constraints.Builder()
            URIS.forEach { constraintsBuilder.addContentUriTrigger(it, true) }
            val detectWorkRequest =
                OneTimeWorkRequestBuilder<FileDetectWorker>()
                    .setConstraints(constraintsBuilder.build())
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, detectWorkRequest)
        }
    }
}