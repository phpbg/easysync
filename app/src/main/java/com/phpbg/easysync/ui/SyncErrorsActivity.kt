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
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.phpbg.easysync.R
import com.phpbg.easysync.db.Error
import com.phpbg.easysync.ui.components.Title
import com.phpbg.easysync.ui.theme.EasySyncTheme
import java.time.Instant


class SyncErrorsActivity : ComponentActivity() {

    private val viewModel: SyncErrorsViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasySyncTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    val errors = viewModel.errors.observeAsState()
                    SynchronizationErrors(errors.value)
                }
            }
        }
    }
}

@Composable
fun SynchronizationErrors(errors: List<Error>?, modifier: Modifier = Modifier) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Title(text = stringResource(R.string.sync_errors_activity_title))
        Spacer(modifier = Modifier.height(8.dp))
        if (errors.isNullOrEmpty()) {
            Text(
                text = stringResource(R.string.sync_errors_activity_no_error), modifier = modifier
            )
        } else {
            errors.forEach {
                ListItem(
                    headlineContent = { Text(it.path) },
                    supportingContent = { Text(it.message) },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SynchronizationErrorsEmptyPreview() {
    EasySyncTheme {
        SynchronizationErrors(errors = null)
    }
}

@Preview(showBackground = true)
@Composable
fun SynchronizationErrorsPreview() {
    EasySyncTheme {
        SynchronizationErrors(
            errors = listOf(
                Error(
                    id = 0, createdDate = Instant.now(), message = "Foo", path = "/foo/bar/baz/0"
                ), Error(
                    id = 1, createdDate = Instant.now(), message = "Foo", path = "/foo/bar/baz/1"
                ), Error(
                    id = 2, createdDate = Instant.now(), message = "Foo", path = "/foo/bar/baz/2"
                )
            )
        )
    }
}