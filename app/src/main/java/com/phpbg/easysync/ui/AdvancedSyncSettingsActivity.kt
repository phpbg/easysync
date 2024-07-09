/*
 * MIT License
 *
 * Copyright (c) 2024 Samuel CHEMLA
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

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.phpbg.easysync.R
import com.phpbg.easysync.ui.components.StdText
import com.phpbg.easysync.ui.components.SwitchSetting
import com.phpbg.easysync.ui.components.Title
import com.phpbg.easysync.ui.theme.EasySyncTheme

class AdvancedSyncSettingsActivity : ComponentActivity() {

    private val viewModel: AdvancedSyncSettingsViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasySyncTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState = viewModel.advancedSyncSettingsUiState.observeAsState()
                    Main(
                        uiState = uiState.value ?: AdvancedSyncSettingsUiState(paths = listOf()),
                        toggleExclusionHandler = viewModel::toggleExclusion
                    )
                }
            }
        }
    }
}

@Composable
private fun Main(
    uiState: AdvancedSyncSettingsUiState,
    toggleExclusionHandler: (relativePath: String, activated: Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Title(text = stringResource(R.string.advanced_sync_settings_activity_title))
        Spacer(modifier = Modifier.height(16.dp))
        StdText(text = stringResource(R.string.advanced_sync_settings_activity_help))
        Spacer(modifier = Modifier.height(16.dp))
        uiState.paths.forEach { syncPath ->
            SwitchSetting(
                description = syncPath.relativePath,
                checked = syncPath.enabled
            ) { newState ->
                toggleExclusionHandler(syncPath.relativePath, newState)
            }
        }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = false)
@Composable
private fun MainPreview() {
    EasySyncTheme {
        Main(
            uiState = AdvancedSyncSettingsUiState(
                paths = listOf(
                    SyncPath(
                        relativePath = "/foo",
                        enabled = true
                    ),
                    SyncPath(relativePath = "/bar/baz", enabled = false),
                    SyncPath(relativePath = "/quuux", enabled = true)
                ),
            ),
            toggleExclusionHandler = { _, _ -> }
        )
    }
}