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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.phpbg.easysync.dav.CollectionPath
import com.phpbg.easysync.dav.WebDavService
import com.phpbg.easysync.mediastore.MediaStoreService
import com.phpbg.easysync.settings.SettingsDataStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

class AdvancedSyncSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val mediaStoreService = MediaStoreService(getApplication())
    private val settingsDataStore = SettingsDataStore(getApplication())

    private val _advancedSyncSettingsUiState = MutableLiveData<AdvancedSyncSettingsUiState>()
    val advancedSyncSettingsUiState: LiveData<AdvancedSyncSettingsUiState> get() = _advancedSyncSettingsUiState

    fun load() {
        viewModelScope.launch {
            var error: String? = null
            val settings = settingsDataStore.getSettings()
            val (paths, davPaths) = awaitAll(
                async { mediaStoreService.getAllPaths() },
                async {
                    try {
                        val webDavService = WebDavService.create(settingsDataStore.getSettings())
                        webDavService.getAllCollections(CollectionPath("/"))
                    } catch (e: Exception) {
                        error = "WebDav error: ${e.message ?: "unknown webdav error"}"
                        setOf()
                    }
                }
            )
            val syncPaths = (paths + davPaths).toSortedSet().map {
                SyncPath(
                    relativePath = it,
                    enabled = !settings.pathExclusions.contains(it)
                )
            }
            _advancedSyncSettingsUiState.postValue(
                AdvancedSyncSettingsUiState(
                    paths = syncPaths,
                    errorMsg = error
                )
            )
        }
    }

    fun toggleExclusion(relativePath: String, activated: Boolean) {
        viewModelScope.launch {
            _advancedSyncSettingsUiState.value?.let { uiState ->
                val updatedList = uiState.paths.map {
                    // Update enabled status for path and sub-paths
                    if (it.relativePath.startsWith(relativePath)) {
                        it.copy(enabled = activated)
                    } else {
                        it
                    }
                }
                _advancedSyncSettingsUiState.postValue(uiState.copy(paths = updatedList))

                // Update settings.exclusionPath
                uiState.paths.filter { it.relativePath.startsWith(relativePath) }
                    .forEach { subPath ->
                        settingsDataStore.updateExclusionPath(
                            subPath.relativePath, !activated
                        )
                    }
            }
        }
    }
}
