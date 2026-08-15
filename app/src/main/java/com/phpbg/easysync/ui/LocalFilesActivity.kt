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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.phpbg.easysync.R
import com.phpbg.easysync.ui.components.Title
import com.phpbg.easysync.ui.theme.ThemeSurface
import kotlinx.coroutines.launch
import kotlin.math.roundToInt


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
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .scrollbar(listState)
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

@Composable
private fun Modifier.scrollbar(state: LazyListState): Modifier {
    var dragging by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (state.isScrollInProgress || dragging) 1f else 0f,
        animationSpec = tween(durationMillis = if (state.isScrollInProgress || dragging) 150 else 500),
        label = "scrollbarAlpha"
    )
    val color = MaterialTheme.colorScheme.outline
    val scope = rememberCoroutineScope()
    val hitWidth = 24.dp

    fun scrollToFraction(fraction: Float) {
        val totalItems = state.layoutInfo.totalItemsCount
        val visibleItems = state.layoutInfo.visibleItemsInfo.size
        val maxIndex = (totalItems - visibleItems).coerceAtLeast(0)
        val index = (fraction.coerceIn(0f, 1f) * maxIndex).roundToInt()
        scope.launch { state.scrollToItem(index) }
    }

    return this
        .pointerInput(Unit) {
            val hitPx = hitWidth.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.position.x < size.width - hitPx) return@awaitEachGesture
                dragging = true
                scrollToFraction(down.position.y / size.height)
                drag(down.id) { change ->
                    change.consume()
                    scrollToFraction(change.position.y / size.height)
                }
                dragging = false
            }
        }
        .drawWithContent {
            drawContent()
            val totalItems = state.layoutInfo.totalItemsCount
            val visibleItems = state.layoutInfo.visibleItemsInfo.size
            if (alpha > 0f && totalItems > visibleItems && visibleItems > 0) {
                val barHeight =
                    (size.height * visibleItems / totalItems).coerceAtLeast(24.dp.toPx())
                val barOffset = (size.height - barHeight) * state.firstVisibleItemIndex /
                        (totalItems - visibleItems)
                val widthPx = 4.dp.toPx()
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width - widthPx, barOffset),
                    size = Size(widthPx, barHeight),
                    alpha = alpha,
                    cornerRadius = CornerRadius(widthPx / 2, widthPx / 2)
                )
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
