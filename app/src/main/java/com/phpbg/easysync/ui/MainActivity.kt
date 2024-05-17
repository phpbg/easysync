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

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.State
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import com.phpbg.easysync.Permissions
import com.phpbg.easysync.R
import com.phpbg.easysync.ui.components.PrimaryTextLarge
import com.phpbg.easysync.ui.components.StatusTitle
import com.phpbg.easysync.ui.components.StatusTitleClickable
import com.phpbg.easysync.ui.components.StdText
import com.phpbg.easysync.ui.components.Title
import com.phpbg.easysync.ui.theme.EasySyncTheme
import kotlinx.coroutines.launch
import kotlin.math.round


class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val hasOptionalPermissions = mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        if (Permissions.areMandatoryGranted(applicationContext)) {
            viewModel.load()
            lifecycleScope.launch {
                hasOptionalPermissions.value = Permissions.areOthersGranted(applicationContext)
            }
        } else {
            val myIntent = Intent(applicationContext, PermissionsActivity::class.java)
            startActivity(myIntent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            EasySyncTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val workerState = viewModel.workInfosList.observeAsState()
                    val syncedCount = viewModel.syncedFileCount.observeAsState()
                    val localCount = viewModel.localFilesCount.observeAsState()
                    val jobCount = viewModel.jobCount.observeAsState()
                    val syncronizationErrorCount = viewModel.synchronizationErrorCount.observeAsState()
                    Main(
                        workerState = workerState.value,
                        fullSyncNowHandler = viewModel::fullSyncNowHandler,
                        syncedCount = syncedCount.value ?: -1,
                        localCount = localCount.value ?: -1,
                        jobCount = jobCount.value ?: -1,
                        showDavStatus = viewModel.showDavStatus,
                        isDavLoading = viewModel.isDavLoading,
                        isDavConnected = viewModel.isDavConnected,
                        isTrial = viewModel.isTrial,
                        hasOptionalPermissions = hasOptionalPermissions,
                        trialRemainingDays = viewModel.trialRemainingDays,
                        syncronizationErrorCount = syncronizationErrorCount.value ?: -1,
                    )
                }
            }
        }
    }
}

fun formatCounter(number: Int): String {
    return if (number == -1) "-" else number.toString()
}

@Composable
private fun Main(
    fullSyncNowHandler: () -> Unit,
    workerState: WorkInfo.State?,
    syncedCount: Int,
    localCount: Int,
    jobCount: Int,
    showDavStatus: State<Boolean>,
    isDavLoading: State<Boolean>,
    isDavConnected: State<Boolean>,
    isTrial: State<Boolean>,
    hasOptionalPermissions: State<Boolean>,
    trialRemainingDays: IntState,
    syncronizationErrorCount: Int,
) {
    val mContext = LocalContext.current
    val syncEnabled = workerState == null || workerState != WorkInfo.State.RUNNING

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Title(text = stringResource(R.string.flavored_app_name))

        val davSettingsHandler = fun(_: Int) {
            val myIntent = Intent(mContext, DavSettingsActivity::class.java)
            mContext.startActivity(myIntent)
        }
        val davSettingsTitle = stringResource(R.string.dav_settings_title)
        if (!showDavStatus.value) {
            StatusTitleClickable(
                title = stringResource(R.string.home_dav_status_not_configured),
                actionTitle = davSettingsTitle,
                statusColor = Color.Gray,
                statusIcon = Icons.Default.Settings,
                clickHandler = davSettingsHandler,
            )
        } else if (isDavLoading.value) {
            StatusTitleClickable(
                title = stringResource(R.string.home_dav_status_loading),
                actionTitle = davSettingsTitle,
                statusColor = Color.Gray,
                statusIcon = Icons.Default.Schedule,
                clickHandler = davSettingsHandler,
            )
        } else if (isDavConnected.value) {
            StatusTitleClickable(
                title = stringResource(R.string.home_dav_status_connected),
                actionTitle = davSettingsTitle,
                statusColor = Color.Green,
                statusIcon = Icons.Default.CheckCircle,
                clickHandler = davSettingsHandler,
            )
        } else {
            StatusTitleClickable(
                title = stringResource(R.string.home_dav_status_not_connected),
                actionTitle = davSettingsTitle,
                statusColor = Color.Red,
                statusIcon = Icons.Default.Cancel,
                clickHandler = davSettingsHandler,
            )
        }


        if (hasOptionalPermissions.value) {
            StatusTitle(
                title = stringResource(R.string.home_permissions_granted),
                statusColor = Color.Green,
                statusIcon = Icons.Default.CheckCircle
            )
        } else {
            StatusTitleClickable(
                title = stringResource(R.string.home_permissions_missing),
                actionTitle = stringResource(R.string.home_permissions_action_fix),
                statusColor = Color.Yellow,
                statusIcon = Icons.Default.Warning,
                clickHandler = {
                    val myIntent = Intent(mContext, PermissionsActivity::class.java)
                    mContext.startActivity(myIntent)
                }
            )
        }

        StatusTitleClickable(
            title = null,
            actionTitle = stringResource(R.string.sync_settings_title),
            statusColor = Color.Gray,
            statusIcon = Icons.Default.Settings,
            clickHandler = {
                val myIntent = Intent(mContext, SyncSettingsActivity::class.java)
                mContext.startActivity(myIntent)
            },
        )

        StatusTitleClickable(
            title = null,
            actionTitle = stringResource(R.string.about),
            statusColor = Color.Gray,
            statusIcon = Icons.Default.Help,
            clickHandler = {
                val i = Intent(Intent.ACTION_VIEW)
                i.data = Uri.parse("https://github.com/phpbg/easysync#easysync")
                mContext.startActivity(i)
            },
        )

        if (syncronizationErrorCount > 0) {
            StatusTitleClickable(
                title = null,
                actionTitle = stringResource(R.string.sync_errors_activity_title),
                statusColor = Color.Red,
                statusIcon = Icons.Default.Help,
                clickHandler = {
                    val myIntent = Intent(mContext, SyncErrorsActivity::class.java)
                    mContext.startActivity(myIntent)
                },
            )
        }

        if (isTrial.value) {
            val msg =
                if (trialRemainingDays.intValue == 0) stringResource(R.string.home_trial_over) else pluralStringResource(
                    R.plurals.home_trial_days_left,
                    trialRemainingDays.intValue,
                    trialRemainingDays.intValue
                )
            StatusTitleClickable(
                title = null,
                actionTitle = msg,
                statusColor = Color.Gray,
                statusIcon = Icons.Default.Info,
                clickHandler = {
                    try {
                        mContext.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=com.phpbg.easysync")
                            )
                        )
                    } catch (e: ActivityNotFoundException) {
                        mContext.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=com.phpbg.easysync")
                            )
                        )
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        val syncedPercent = if (localCount > 0 && syncedCount >= 0) {
            round(100.0 * syncedCount / localCount).toInt()
        } else if (syncedCount == 0) {
            0
        } else {
            -1
        }
        val maxJobs = maxOf(localCount, syncedCount, jobCount)
        val jobCountPercent =
            if (jobCount == -1) -1 else round(100.0 * (maxJobs - jobCount) / maxJobs).toInt()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = MaterialTheme.colorScheme.surfaceVariant),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val modifier = Modifier.height(32.dp)
            Column(
                modifier = Modifier
                    .padding(1.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.End
            ) {
                Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                    PrimaryTextLarge(text = formatCounter(localCount))
                }
                Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                    PrimaryTextLarge(text = formatCounter(syncedPercent) + "%")
                }
                Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                    if (jobCount == 0) {
                        PrimaryTextLarge(text = stringResource(R.string.home_files_not_running_prefix))
                    } else {
                        PrimaryTextLarge(text = formatCounter(jobCountPercent) + "%")
                    }
                }
            }
            Column(
                modifier = Modifier
                    .padding(1.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                    StdText(text = stringResource(R.string.home_files_local))
                }
                Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                    StdText(text = stringResource(R.string.home_files_synced))
                }
                Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                    if (jobCount == 0) {
                        StdText(text = stringResource(R.string.home_sync_running))
                    } else {
                        StdText(text = stringResource(R.string.home_sync_syncing))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Sync requested",
                        duration = SnackbarDuration.Short
                    )
                }
                fullSyncNowHandler()
            }, enabled = syncEnabled) {
                Text(
                    text = stringResource(R.string.home_action_sync_now),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }

    // Floating bottom
    Column(
        Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Bottom
    ) {
        Row() {
            SnackbarHost(hostState = snackbarHostState)
        }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = false)
@Composable
private fun MainPreview() {
    EasySyncTheme {
        Main(
            fullSyncNowHandler = {},
            workerState = WorkInfo.State.RUNNING,
            syncedCount = 10000,
            localCount = 100,
            jobCount = -1,
            showDavStatus = remember { mutableStateOf(true) },
            isDavLoading = remember { mutableStateOf(false) },
            isDavConnected = remember { mutableStateOf(true) },
            isTrial = remember { mutableStateOf(false) },
            hasOptionalPermissions = remember { mutableStateOf(false) },
            trialRemainingDays = remember { mutableIntStateOf(0) },
            syncronizationErrorCount = 0
        )
    }
}

@Preview(name = "Trial Mode", showBackground = false)
@Composable
private fun MainPreviewTrial() {
    EasySyncTheme {
        Main(
            fullSyncNowHandler = {},
            workerState = WorkInfo.State.RUNNING,
            syncedCount = 10000,
            localCount = 100,
            jobCount = -1,
            showDavStatus = remember { mutableStateOf(true) },
            isDavLoading = remember { mutableStateOf(false) },
            isDavConnected = remember { mutableStateOf(true) },
            isTrial = remember { mutableStateOf(true) },
            hasOptionalPermissions = remember { mutableStateOf(false) },
            trialRemainingDays = remember { mutableIntStateOf(28) },
            syncronizationErrorCount = 10
        )
    }
}