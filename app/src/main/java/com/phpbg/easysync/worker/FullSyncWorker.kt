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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.phpbg.easysync.MyApp.Companion.isTrialExpired
import com.phpbg.easysync.Notifications
import com.phpbg.easysync.R
import com.phpbg.easysync.dav.MisconfigurationException
import com.phpbg.easysync.settings.SettingsDataStore
import com.phpbg.easysync.ui.MainActivity
import java.util.concurrent.TimeUnit

private const val TAG = "FullSyncWorker"
private const val IMMEDIATE_KEY = "immediate"

class FullSyncWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager

    override suspend fun doWork(): Result {
        if (isTrialExpired(this.applicationContext)) {
            showTrialExpiredNotification()
            return Result.success()
        }
        val immediate = inputData.getBoolean(IMMEDIATE_KEY, false)
        return try {
            val syncService = SyncService.getInstance(this.applicationContext)
            syncService.syncAll(this.applicationContext, immediate)
            if (immediate) {
                enqueueUpdateExisting(this.applicationContext)
            }
            Result.success()
        } catch (e: MissingPermissionException) {
            showMissingPermissionsNotification()
            Result.failure()
        } catch (e: MisconfigurationException) {
            Log.d(TAG, "Cannot create DAV client")
            Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Error while running fullsync attempt: $runAttemptCount")
            Log.e(TAG, e.toString())
            return if (runAttemptCount < 3) {
                // We don't mind too many errors: full sync is scheduled and will run later
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun showTrialExpiredNotification() {
        val title = "Synchronization failed" // TODO translations
        val text = "Please buy the full version to allow synchronization" // TODO translation
        val notificationId = Notifications.TRIAL_EXPIRED
        showNotification(title, text, notificationId)
    }

    private fun showMissingPermissionsNotification() {
        val title = applicationContext.getString(R.string.notification_missing_permissions_title)
        val text = applicationContext.getString(R.string.notification_missing_permissions_text)
        val notificationId = Notifications.MISSING_PERMISSIONS
        showNotification(title, text, notificationId)
    }

    private fun showNotification(title: String, text: String, notificationId: Notifications) {
        val id = applicationContext.getString(R.string.notification_channel_id)

        val intent = Intent(this.applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this.applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        createChannel(id)
        val notification = Notification.Builder(applicationContext, id)
            .setContentTitle(title)
            .setContentText(text)
            .setTicker(title)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId.id, notification)
    }

    private fun createChannel(channelId: String) {
        val channel = NotificationChannel(
            channelId,
            "Synchronization status",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    companion object {

        suspend fun enqueueKeep(context: Context) {
            enqueueScheduled(context, ExistingPeriodicWorkPolicy.KEEP, false)
        }

        suspend fun enqueueUpdateExisting(context: Context) {
            enqueueScheduled(context, ExistingPeriodicWorkPolicy.UPDATE, false)
        }

        suspend fun enqueueImmediate(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WorkersConstants.TAG)
            enqueueScheduled(context, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, true)
        }

        private suspend fun enqueueScheduled(
            context: Context,
            existingPeriodicWorkPolicy: ExistingPeriodicWorkPolicy,
            immediate: Boolean
        ) {
            Log.d(TAG, "Enqueue immediate: $immediate, policy: $existingPeriodicWorkPolicy")
            val settingsDataStore = SettingsDataStore(context)
            val settings = settingsDataStore.getSettings()

            val data = Data.Builder()
                .putBoolean(IMMEDIATE_KEY, immediate)
                .build()

            val workRequest =
                PeriodicWorkRequestBuilder<FullSyncWorker>(settings.syncIntervalMinutes, TimeUnit.MINUTES)
                    .setInputData(data)
                    .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    TAG,
                    existingPeriodicWorkPolicy, workRequest
                )
        }

        fun getLiveData(context: Context): LiveData<MutableList<WorkInfo>> {
            return WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(TAG)
        }

        fun getAllLiveData(context: Context): LiveData<MutableList<WorkInfo>> {
            return WorkManager.getInstance(context).getWorkInfosByTagLiveData(WorkersConstants.TAG)
        }
    }
}