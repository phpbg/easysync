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

package com.phpbg.easysync.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.phpbg.easysync.Permissions
import com.phpbg.easysync.settings.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "PermissionsActivityViewModel"

class PermissionsViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(PermissionsUiState())
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()
    private val settingsDataStore = SettingsDataStore(getApplication())

    private var hibernationExclusionAsked = false
    private var batteryOptimizationAsked = false
    private var notificationPermissionAsked = false
    private var configurationLaunched = false
    private var welcomeDisplayed = false

    fun configurationLaunched() {
        configurationLaunched = true
    }

    fun hibernationExclusionAsked() {
        hibernationExclusionAsked = true
    }

    fun batteryOptimizationAsked() {
        batteryOptimizationAsked = true
    }

    fun notificationPermissionAsked() {
        notificationPermissionAsked = true
    }

    fun welcomeDisplayed() {
        welcomeDisplayed = true
    }

    suspend fun needConfiguration(): Boolean {
        return settingsDataStore.getSettings().url.isEmpty() && !configurationLaunched
    }

    /**
     * Return true if at least one permission screen is required to be displayed
     */
    suspend fun computeRequiredPermissions(): Boolean {
        val context = getApplication<Application>().applicationContext

        if (!welcomeDisplayed && needConfiguration()) {
            _uiState.update { uiState -> uiState.copy(displayWelcome = true) }
        } else {
            _uiState.update { uiState -> uiState.copy(displayWelcome = false) }
        }

        if (Permissions.needStorageManager()) {
            Log.d(TAG, "Need ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION")
            _uiState.update { uiState -> uiState.copy(displayFilesPermission = true) }
        } else {
            _uiState.update { uiState -> uiState.copy(displayFilesPermission = false) }
        }

        if (Permissions.needOldStoragePermission(context)) {
            Log.d(TAG, "Need old storage permissions")
            _uiState.update { uiState -> uiState.copy(displayOldStoragePermissions = true) }
        } else {
            _uiState.update { uiState -> uiState.copy(displayOldStoragePermissions = false) }
        }

        if (Permissions.needHibernationExclusion(context) && !hibernationExclusionAsked) {
            Log.d(TAG, "Need HIBERNATION EXCLUSION")
            _uiState.update { uiState -> uiState.copy(displayHibernationExclusion = true) }
        } else {
            _uiState.update { uiState -> uiState.copy(displayHibernationExclusion = false) }
        }

        if (Permissions.needIgnoringBatteryOptimizations(context) && !batteryOptimizationAsked) {
            Log.d(TAG, "Need ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
            _uiState.update { uiState -> uiState.copy(displayBatteryOptimization = true) }
        } else {
            _uiState.update { uiState -> uiState.copy(displayBatteryOptimization = false) }
        }

        if (Permissions.needNotificationPermission(context) && !notificationPermissionAsked) {
            _uiState.update { uiState -> uiState.copy(displayNotificationsPermissions = true) }
        } else {
            _uiState.update { uiState -> uiState.copy(displayNotificationsPermissions = false) }
        }

        return _uiState.value.displayWelcome
                || _uiState.value.displayFilesPermission
                || _uiState.value.displayOldStoragePermissions
                || _uiState.value.displayNotificationsPermissions
                || _uiState.value.displayBatteryOptimization
                || _uiState.value.displayHibernationExclusion
    }
}