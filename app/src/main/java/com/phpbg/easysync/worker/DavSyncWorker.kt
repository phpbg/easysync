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
import com.phpbg.easysync.dav.CollectionPath
import com.phpbg.easysync.dav.File
import com.phpbg.easysync.dav.NotFoundExeption
import com.phpbg.easysync.dav.WebDavService
import com.phpbg.easysync.db.AppDatabaseFactory
import com.phpbg.easysync.settings.SettingsDataStore
import com.phpbg.easysync.worker.WorkersConstants.MAX_RUN_ATTEMPTS
import java.util.concurrent.CancellationException

private const val TAG = "DavSyncWorker"
private const val DATA_DAV_PATH_KEY = "dav_path"
private const val DATA_DAV_IS_COLLECTION = "dav_collection"
private const val DATA_IMMEDIATE_KEY = "dav_immediate"

class DavSyncWorker(appContext: Context, workerParams: WorkerParameters) :
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
        val path = inputData.getString(DATA_DAV_PATH_KEY)
        val isCollection = inputData.getBoolean(DATA_DAV_IS_COLLECTION, false)
        val immediate = inputData.getBoolean(DATA_IMMEDIATE_KEY, false)
        if (path == null) {
            Log.w(TAG, "Syncing null path")
            return Result.success()
        }

        Log.d(TAG, "Syncing $path immediate: $immediate")
        val settingsDataStore = SettingsDataStore(this.applicationContext)
        val webDavService = WebDavService.getInstance(settingsDataStore.getSettingsAsFlow())
        val syncService = SyncService.getInstance(this.applicationContext)
        val db = AppDatabaseFactory.create(this.applicationContext)
        val fileDao = db.fileDao()
        try {
            if (isCollection) {
                try {
                    val collectionPath = CollectionPath(path)
                    val resources = webDavService.list(collectionPath)
                    val dbPaths = fileDao.getDirectChildrenStartingWithPathname(collectionPath.getPath()).toSet()
                    resources.forEach {
                        // Enqueue collection or new files only
                        if ((it.isCollection && it.relativeHref.getPath() != collectionPath.getPath()) || (!it.isCollection && !dbPaths.contains(it.relativeHref.getPath()))) {
                            Log.d(TAG, "Enqueue $it")
                            enqueue(
                                this.applicationContext, it.relativeHref.getPath(),
                                immediate = immediate,
                                isCollection = it.isCollection
                            )
                        } else {
                            Log.d(TAG, "No need to enqueue $it")
                        }
                    }
                } catch (e: NotFoundExeption) {
                    Log.d(TAG, "Not found anymore: $path")
                }
            } else {
                syncService.handleSyncResource(File(path))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error while syncing with remote: $path attempt: $runAttemptCount")
            Log.e(TAG, e.toString())
            Log.e(TAG, e.stackTraceToString())
            return if (runAttemptCount < MAX_RUN_ATTEMPTS) {
                // We don't mind too many errors: file will be synced anyway with periodic full sync worker
                Result.retry()
            } else {
                syncService.handleWorkerException(applicationContext, e, path)
                Result.failure()
            }
        }

        return Result.success()
    }

    companion object {
        suspend fun enqueue(
            context: Context,
            path: String,
            immediate: Boolean,
            isCollection: Boolean
        ) {
            Log.d(TAG, "Enqueue $path immediate:$immediate")
            val constraints = ConstraintBuilder.getFullSyncConstraints(context, immediate).build()
            val data = Data.Builder()
                .putString(DATA_DAV_PATH_KEY, path)
                .putBoolean(DATA_DAV_IS_COLLECTION, isCollection)
                .putBoolean(DATA_IMMEDIATE_KEY, immediate)
                .build()
            val syncWorkRequest =
                OneTimeWorkRequestBuilder<DavSyncWorker>()
                    .setInputData(data)
                    .setConstraints(constraints)
                    .addTag(WorkersConstants.TAG)
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("DAV_$path", ExistingWorkPolicy.REPLACE, syncWorkRequest)
        }
    }
}