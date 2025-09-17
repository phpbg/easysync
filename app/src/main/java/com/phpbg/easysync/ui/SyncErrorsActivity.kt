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
import androidx.compose.material3.ListItem
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
import com.phpbg.easysync.ui.theme.ThemeSurface
import java.time.Instant


class SyncErrorsActivity : ComponentActivity() {

    private val viewModel: SyncErrorsViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ThemeSurface {
                val errors = viewModel.errors.observeAsState()
                SynchronizationErrors(errors.value)
            }
        }
    }
}

@Composable
fun SynchronizationErrors(errors: List<Error>?, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (errors.isNullOrEmpty()) {
            item {
                Title(text = stringResource(R.string.sync_errors_activity_title))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.sync_errors_activity_no_error),
                    modifier = modifier
                )
            }
        } else {
            item {
                Title(text = stringResource(R.string.sync_errors_activity_title))
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(errors) { error ->
                ListItem(
                    headlineContent = { Text(error.path) },
                    supportingContent = { Text(error.message) },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SynchronizationErrorsEmptyPreview() {
    ThemeSurface {
        SynchronizationErrors(errors = null)
    }
}

@Preview(showBackground = true)
@Composable
fun SynchronizationErrorsPreview() {
    ThemeSurface {
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