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

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.phpbg.easysync.R
import com.phpbg.easysync.settings.Settings
import com.phpbg.easysync.ui.components.Title
import com.phpbg.easysync.ui.theme.MyApplicationTheme

class DavSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewModel: DavSettingsViewModel by viewModels()
        viewModel.load()

        setContent {
            MyApplicationTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    val uiState = viewModel.uiState.collectAsState()

                    if (uiState.value.davConnected == true) {
                        finish()
                    }

                    Preferences(
                        uiState = uiState.value,
                        stateChangeHandler = viewModel::stateChangeHandler,
                        saveHandler = viewModel::save
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Preferences(
    uiState: DavSettingsUiState,
    stateChangeHandler: (settings: Settings) -> Unit,
    saveHandler: () -> Unit
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Title(text = stringResource(R.string.dav_settings_title))
        Spacer(modifier = Modifier.height(8.dp))
        if (uiState.hasFilesSynced == true) {
            Text(text = "Synchronization already started. Changing URL or path is highly discouraged and will lead to data loss. You should only change login/password", color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }
        TextField(
            value = uiState.settings.url,
            onValueChange = { stateChangeHandler(uiState.settings.copy(url = it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            placeholder = { Text(text = "Url") },
            label = { Text(text = "Url") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
                keyboardType = KeyboardType.Uri
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (uiState.settings.url.startsWith("http://", true)) {
            Text(text = stringResource(R.string.dav_settings_url_insecure), color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }
        TextField(value = uiState.settings.username,
            onValueChange = { stateChangeHandler(uiState.settings.copy(username = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(text = stringResource(R.string.dav_settings_username)) },
            label = { Text(text = stringResource(R.string.dav_settings_username)) })
        Spacer(modifier = Modifier.height(8.dp))
        TextField(value = uiState.settings.password,
            onValueChange = { stateChangeHandler(uiState.settings.copy(password = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(text = stringResource(R.string.dav_settings_password)) },
            label = { Text(text = stringResource(R.string.dav_settings_password)) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility
                else Icons.Filled.VisibilityOff

                val description =
                    if (passwordVisible) stringResource(R.string.dav_settings_hide_password) else stringResource(
                        R.string.dav_settings_show_password
                    )

                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, description)
                }
            })
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = uiState.settings.davPath,
            onValueChange = { stateChangeHandler(uiState.settings.copy(davPath = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(text = stringResource(R.string.dav_settings_remote_path)) },
            label = { Text(text = stringResource(R.string.dav_settings_remote_path)) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
                keyboardType = KeyboardType.Uri
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (uiState.showRemoteNotEmpty) {
            Text(text = stringResource(R.string.dav_settings_remote_not_empty), color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Button(onClick = saveHandler, enabled = !uiState.ongoingIO) {
            if (uiState.ongoingIO) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.outline)
            } else {
                val text = if (uiState.showRemoteNotEmpty) {R.string.dav_settings_save_anyway} else {R.string.dav_settings_save}
                Text(text = stringResource(text))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (uiState.davError != null) {
            Text(text = "Error: ${uiState.davError}", color = MaterialTheme.colorScheme.error)
        }
    }
}


@Preview(name = "Light Mode", showBackground = true)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = false)
@Preview(name = "FR", showBackground = true, locale = "fr")
@Composable
private fun PreferencesPreview() {
    MyApplicationTheme {
        Preferences(uiState = DavSettingsUiState(), stateChangeHandler = { }, saveHandler = { })
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Composable
private fun PreferencesPreviewWithCircularIndicator() {
    MyApplicationTheme {
        Preferences(uiState = DavSettingsUiState(ongoingIO = true), stateChangeHandler = { }, saveHandler = { })
    }
}