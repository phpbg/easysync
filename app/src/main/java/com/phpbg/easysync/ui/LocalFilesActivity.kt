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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.phpbg.easysync.R
import com.phpbg.easysync.ui.components.Title
import com.phpbg.easysync.ui.theme.ThemeSurface


class LocalFilesActivity : ComponentActivity() {

    private val viewModel: LocalFilesViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ThemeSurface {
                val files = viewModel.files.observeAsState()
                LocalFiles(files.value)
            }
        }
    }
}

@Composable
fun LocalFiles(files: List<LocalFileItem>?, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Title(text = stringResource(R.string.local_files_activity_title))
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (files == null) {
            item {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.outline)
            }
        } else if (files.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.local_files_activity_no_file),
                    modifier = modifier
                )
            }
        } else {
            items(files, key = { it.pathname }) { file ->
                ListItem(
                    headlineContent = { Text(file.pathname) },
                    supportingContent = {
                        Text(
                            file.errorMessage ?: stringResource(statusLabel(file.status))
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = statusIcon(file.status),
                            contentDescription = stringResource(statusLabel(file.status)),
                            tint = statusColor(file.status)
                        )
                    },
                )
            }
        }
    }
}

private fun statusIcon(status: SyncStatus): ImageVector = when (status) {
    SyncStatus.SYNCED -> Icons.Default.CheckCircle
    SyncStatus.NOT_SYNCED -> Icons.Default.CloudOff
    SyncStatus.ERROR -> Icons.Default.Warning
}

private fun statusColor(status: SyncStatus): Color = when (status) {
    SyncStatus.SYNCED -> Color.Green
    SyncStatus.NOT_SYNCED -> Color.Gray
    SyncStatus.ERROR -> Color.Yellow
}

private fun statusLabel(status: SyncStatus): Int = when (status) {
    SyncStatus.SYNCED -> R.string.local_files_status_synced
    SyncStatus.NOT_SYNCED -> R.string.local_files_status_not_synced
    SyncStatus.ERROR -> R.string.local_files_status_error
}

@Preview(showBackground = true)
@Composable
fun LocalFilesEmptyPreview() {
    ThemeSurface {
        LocalFiles(files = emptyList())
    }
}

@Preview(showBackground = true)
@Composable
fun LocalFilesPreview() {
    ThemeSurface {
        LocalFiles(
            files = listOf(
                LocalFileItem("/DCIM/Camera/IMG_0001.jpg", SyncStatus.SYNCED, null),
                LocalFileItem("/DCIM/Camera/IMG_0002.jpg", SyncStatus.NOT_SYNCED, null),
                LocalFileItem(
                    "/Download/report.pdf",
                    SyncStatus.ERROR,
                    "Connection timed out"
                ),
            )
        )
    }
}
