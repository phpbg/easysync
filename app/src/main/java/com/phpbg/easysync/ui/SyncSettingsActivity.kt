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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.stringResource
import com.phpbg.easysync.R
import com.phpbg.easysync.settings.ConflictStrategy
import com.phpbg.easysync.ui.components.Description
import com.phpbg.easysync.ui.components.RadioGroup
import com.phpbg.easysync.ui.components.StdText
import com.phpbg.easysync.ui.components.SwitchSetting
import com.phpbg.easysync.ui.components.Title
import com.phpbg.easysync.ui.theme.MyApplicationTheme

class SyncSettingsActivity : ComponentActivity() {

    private val viewModel: SyncSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyApplicationTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val settings = viewModel.settings.observeAsState()
                    Main(
                        syncOnCellular = settings.value?.syncOnCellular ?: false,
                        syncOnCellularHandler = viewModel::syncOnCellularHandler,
                        syncOnBattery = settings.value?.syncOnBattery ?: false,
                        syncOnBatteryHandler = viewModel::syncOnBatteryHandler,
                        conflictStrategy = settings.value?.conflictStrategy
                            ?: ConflictStrategy.IGNORE,
                        conflictStrategyHandler = viewModel::conflictStrategyHandler,
                    )
                }
            }
        }
    }
}

@Composable
private fun Main(
    syncOnCellular: Boolean,
    syncOnCellularHandler: (Boolean) -> Unit,
    syncOnBattery: Boolean,
    syncOnBatteryHandler: (Boolean) -> Unit,
    conflictStrategy: ConflictStrategy,
    conflictStrategyHandler: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Title(text = stringResource(R.string.sync_settings_title))
        Spacer(modifier = Modifier.height(16.dp))
        SwitchSetting(
            title = "Sync on cellular",
            description = "Allow scheduled synchronization on cellular network",
            checked = syncOnCellular,
            onCheckedChange = syncOnCellularHandler
        )
        SwitchSetting(
            title = "Sync on battery",
            description = "Allow scheduled synchronization on battery",
            checked = syncOnBattery,
            onCheckedChange = syncOnBatteryHandler
        )
        Spacer(modifier = Modifier.height(16.dp))
        StdText("Conflicts handling")
        Description("If a file is modified on both side:")
        val options = mapOf(
            ConflictStrategy.KEEP_LOCAL.name to "Keep local copy (overwrite remote copy)",
            ConflictStrategy.IGNORE.name to "Ignore file",
            ConflictStrategy.KEEP_REMOTE.name to "Keep remote copy (overwrite local copy)"
        )
        RadioGroup(options, selected = conflictStrategy.name, onClick = conflictStrategyHandler)
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = false)
@Composable
private fun MainPreview() {
    MyApplicationTheme {
        Main(
            syncOnBattery = false,
            syncOnBatteryHandler = {},
            syncOnCellular = false,
            syncOnCellularHandler = {},
            conflictStrategy = ConflictStrategy.IGNORE,
            conflictStrategyHandler = {}
        )
    }
}