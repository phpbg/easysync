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

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AppSettingsAlt
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.phpbg.easysync.Permissions
import com.phpbg.easysync.R
import com.phpbg.easysync.ui.components.IconTextButtonScreen
import com.phpbg.easysync.ui.theme.ThemeSurface
import kotlinx.coroutines.launch

private const val TAG = "PermissionsActivity"

class PermissionsActivity : ComponentActivity() {
    private val activityLauncher: ActivityResultLauncher<Intent>
    private val requestPermissionLauncher: ActivityResultLauncher<Array<String>>
    private val viewModel: PermissionsViewModel by viewModels()

    init {
        activityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            Log.d(TAG, "RESULT: $it")
            computeOrRedirect()
        }
        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionResults ->
                for (entry in permissionResults.entries) {
                    val permission = entry.key
                    val isGranted = entry.value
                    if (isGranted) {
                        Log.d(TAG, "Permission $permission granted")
                    } else {
                        Log.d(TAG, "Permission $permission denied")
                    }
                }
                computeOrRedirect()
            }
    }

    override fun onResume() {
        super.onResume()
        computeOrRedirect()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ThemeSurface {
                val uiState = viewModel.uiState.collectAsState()
                if (uiState.value.displayWelcome) {
                    IconTextButtonScreen(
                        icon = Icons.Outlined.Sync,
                        text = stringResource(R.string.permissions_welcome),
                        buttonText = stringResource(R.string.permissions_next),
                        nextHandler = {
                            viewModel.welcomeDisplayed()
                            computeOrRedirect()
                        }
                    )
                    return@ThemeSurface
                }
                if (uiState.value.displayFilesPermission) {
                    IconTextButtonScreen(
                        icon = Icons.Outlined.PermMedia,
                        text = stringResource(R.string.permissions_files_text),
                        buttonText = stringResource(R.string.permissions_files_button),
                        nextHandler = {
                            //noinspection InlinedApi
                            val intent =
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.fromParts("package", packageName, null)
                            activityLauncher.launch(intent)
                        }
                    )
                    return@ThemeSurface
                }
                if (uiState.value.displayOldStoragePermissions) {
                    IconTextButtonScreen(
                        icon = Icons.Outlined.PermMedia,
                        text = stringResource(R.string.permissions_old_files_text),
                        buttonText = stringResource(R.string.permissions_old_files_button),
                        nextHandler = {
                            viewModel.notificationPermissionAsked()
                            val missingPermissions =
                                Permissions.getMissingOldStoragePermissions(this)
                            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
                        }
                    )
                    return@ThemeSurface
                }
                if (uiState.value.displayBatteryOptimization) {
                    IconTextButtonScreen(
                        icon = Icons.Outlined.BatterySaver,
                        text = stringResource(R.string.permissions_doze_text),
                        buttonText = stringResource(R.string.permissions_doze_button),
                        nextHandler = {
                            viewModel.batteryOptimizationAsked()
                            val intent =
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = Uri.fromParts("package", packageName, null)
                            activityLauncher.launch(intent)
                        }
                    )
                    return@ThemeSurface
                }
                if (uiState.value.displayNotificationsPermissions) {
                    IconTextButtonScreen(
                        icon = Icons.Outlined.Notifications,
                        text = stringResource(R.string.permissions_notifications_text),
                        buttonText = stringResource(R.string.permissions_notifications_button),
                        nextHandler = {
                            viewModel.notificationPermissionAsked()
                            //noinspection InlinedApi
                            requestPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                        }
                    )
                    return@ThemeSurface
                }
                if (uiState.value.displayHibernationExclusion) {
                    // Detailed messages are here: https://developer.android.com/topic/performance/app-hibernation
                    val text = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                        R.string.permissions_hibernation_text_11
                    } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                        R.string.permissions_hibernation_text_12
                    } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        R.string.permissions_hibernation_text_13
                    } else {
                        R.string.permissions_hibernation_text_15
                    }
                    IconTextButtonScreen(
                        icon = Icons.Outlined.AppSettingsAlt,
                        text = stringResource(text),
                        buttonText = stringResource(R.string.permissions_hibernation_button),
                        nextHandler = {
                            viewModel.hibernationExclusionAsked()
                            val intent = IntentCompat.createManageUnusedAppRestrictionsIntent(
                                this,
                                packageName
                            )
                            activityLauncher.launch(intent)
                        }
                    )
                    return@ThemeSurface
                }
            }
        }
    }

    private fun computeOrRedirect() {
        lifecycleScope.launch {
            if (!viewModel.computeRequiredPermissions()) {
                redirect()
            }
        }
    }

    private suspend fun redirect() {
        if (viewModel.needConfiguration()) {
            viewModel.configurationLaunched()
            startActivity(Intent(applicationContext, DavSettingsActivity::class.java))
        } else {
            finish()
        }
    }
}