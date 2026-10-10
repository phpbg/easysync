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
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.phpbg.easysync.R
import com.phpbg.easysync.settings.Settings
import com.phpbg.easysync.ui.components.Title
import com.phpbg.easysync.ui.theme.ThemeSurface

class DavSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel: DavSettingsViewModel by viewModels()
        viewModel.load()

        setContent {
            ThemeSurface {
                val uiState = viewModel.uiState.collectAsState()

                if (uiState.value.davConnected == true) {
                    finish()
                }

                Preferences(
                    uiState = uiState.value,
                    stateChangeHandler = viewModel::stateChangeHandler,
                    saveHandler = viewModel::save,
                    openPickerHandler = viewModel::openPathPicker,
                )

                if (uiState.value.pickerVisible) {
                    DavPathPickerDialog(
                        uiState = uiState.value,
                        onBrowse = viewModel::browsePickerPath,
                        onNavigateUp = viewModel::pickerNavigateUp,
                        onSelect = viewModel::selectPickerPath,
                        onDismiss = viewModel::closePathPicker,
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
    saveHandler: () -> Unit,
    openPickerHandler: () -> Unit,
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
            Text(
                text = "Synchronization already started. Changing URL or path is highly discouraged and will lead to data loss. You should only change login/password",
                color = MaterialTheme.colorScheme.error
            )
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
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (uiState.settings.url.startsWith("http://", true)) {
            Text(
                text = stringResource(R.string.dav_settings_url_insecure),
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        TextField(
            value = uiState.settings.username,
            onValueChange = { stateChangeHandler(uiState.settings.copy(username = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(text = stringResource(R.string.dav_settings_username)) },
            label = { Text(text = stringResource(R.string.dav_settings_username)) })
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = uiState.settings.password,
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
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri
            ),
            trailingIcon = {
                IconButton(
                    onClick = openPickerHandler,
                    enabled = uiState.settings.url.isNotBlank() && !uiState.ongoingIO
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.dav_settings_browse_remote_path)
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (uiState.showRemoteNotEmpty) {
            Text(
                text = stringResource(R.string.dav_settings_remote_not_empty),
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Button(onClick = saveHandler, enabled = !uiState.ongoingIO) {
            if (uiState.ongoingIO) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.outline)
            } else {
                val text = if (uiState.showRemoteNotEmpty) {
                    R.string.dav_settings_save_anyway
                } else {
                    R.string.dav_settings_save
                }
                Text(text = stringResource(text))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (uiState.davError != null) {
            Text(
                text = stringResource(R.string.dav_settings_error, uiState.davError),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}


@Composable
private fun DavPathPickerDialog(
    uiState: DavSettingsUiState,
    onBrowse: (path: String) -> Unit,
    onNavigateUp: () -> Unit,
    onSelect: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = stringResource(R.string.dav_settings_picker_title))
                Text(
                    text = uiState.pickerPath,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                if (uiState.pickerPath != "/") {
                    item {
                        PickerRow(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            label = "..",
                            contentDescription = stringResource(R.string.dav_settings_picker_parent),
                            enabled = !uiState.pickerLoading,
                            onClick = onNavigateUp,
                        )
                    }
                }
                if (uiState.pickerLoading) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (uiState.pickerError != null) {
                    item {
                        Text(
                            text = stringResource(R.string.dav_settings_error, uiState.pickerError),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else if (uiState.pickerFolders.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.dav_settings_picker_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(uiState.pickerFolders) { folder ->
                        PickerRow(
                            icon = Icons.Filled.Folder,
                            label = folder.trimEnd('/').substringAfterLast('/'),
                            contentDescription = null,
                            enabled = true,
                            onClick = { onBrowse(folder) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSelect,
                enabled = !uiState.pickerLoading && uiState.pickerError == null
            ) {
                Text(text = stringResource(R.string.dav_settings_picker_select))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dav_settings_picker_cancel))
            }
        },
    )
}

@Composable
private fun PickerRow(
    icon: ImageVector,
    label: String,
    contentDescription: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}


@Preview(name = "Light Mode", showBackground = true)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = false)
@Preview(name = "FR", showBackground = true, locale = "fr")
@Composable
private fun PreferencesPreview() {
    ThemeSurface {
        Preferences(
            uiState = DavSettingsUiState(),
            stateChangeHandler = { },
            saveHandler = { },
            openPickerHandler = { })
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Composable
private fun PreferencesPreviewWithCircularIndicator() {
    ThemeSurface {
        Preferences(
            uiState = DavSettingsUiState(ongoingIO = true),
            stateChangeHandler = { },
            saveHandler = { },
            openPickerHandler = { })
    }
}

@Preview(name = "Picker", showBackground = true)
@Composable
private fun DavPathPickerDialogPreview() {
    ThemeSurface {
        DavPathPickerDialog(
            uiState = DavSettingsUiState(
                pickerVisible = true,
                pickerPath = "/Photos/",
                pickerFolders = listOf("/Photos/2023/", "/Photos/2024/", "/Photos/Camera/")
            ),
            onBrowse = { },
            onNavigateUp = { },
            onSelect = { },
            onDismiss = { })
    }
}