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

package com.phpbg.easysync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants.API_30
import androidx.core.content.UnusedAppRestrictionsConstants.API_30_BACKPORT
import androidx.core.content.UnusedAppRestrictionsConstants.API_31
import androidx.work.await


object Permissions {

    private val oldStoragePermissions: Array<String>

    init {
        val permissionsList = mutableListOf<String>()
        // NB: MANAGE_EXTERNAL_STORAGE is not listed here as it is handled with special ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
        // READ_MEDIA_IMAGES and READ_MEDIA_VIDEO are not required when MANAGE_EXTERNAL_STORAGE is granted
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            permissionsList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        oldStoragePermissions = permissionsList.toTypedArray()
    }

    fun needStorageManager(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()
    }

    fun needIgnoringBatteryOptimizations(context: Context): Boolean {
        // https://developer.android.com/reference/android/provider/Settings#ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        // https://developer.android.com/topic/performance/appstandby
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    suspend fun needHibernationExclusion(context: Context): Boolean {
        // https://developer.android.com/topic/performance/app-hibernation
        // https://source.android.com/docs/core/perf/hiber?hl=en
        return when (PackageManagerCompat.getUnusedAppRestrictionsStatus(context).await()) {
            API_30_BACKPORT, API_30, API_31 -> true
            else -> false
        }
    }

    fun needNotificationPermission(context: Context): Boolean {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isDenied(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ))
    }

    fun needOldStoragePermission(context: Context): Boolean {
        val perm = this.oldStoragePermissions.find {
            return isDenied(context, it)
        }
        return perm != null
    }

    fun areMandatoryGranted(context: Context): Boolean {
        return !needOldStoragePermission(context) && !needStorageManager()
    }

    suspend fun areOthersGranted(context: Context): Boolean {
        return ! needNotificationPermission(context) && ! needHibernationExclusion(context) && ! needIgnoringBatteryOptimizations(context)
    }

    fun getMissingOldStoragePermissions(context: Context): List<String> {
        return this.oldStoragePermissions
            .filter { isDenied(context, it) }
    }

    private fun isDenied(context: Context, permission: String): Boolean {
        return (ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_DENIED
                )
    }
}