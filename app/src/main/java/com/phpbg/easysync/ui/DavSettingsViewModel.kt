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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.phpbg.easysync.dav.CollectionPath
import com.phpbg.easysync.dav.IOException
import com.phpbg.easysync.dav.MisconfigurationException
import com.phpbg.easysync.dav.NotFoundExeption
import com.phpbg.easysync.dav.UnauthorizedExeption
import com.phpbg.easysync.dav.WebDavService
import com.phpbg.easysync.db.AppDatabaseFactory
import com.phpbg.easysync.settings.Settings
import com.phpbg.easysync.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class DavSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsDataStore = SettingsDataStore(getApplication())
    private val _uiState = MutableStateFlow(DavSettingsUiState())
    val uiState: StateFlow<DavSettingsUiState> = _uiState.asStateFlow()
    private val db = AppDatabaseFactory.create(getApplication())
    private val fileDao = db.fileDao()
    private val syncedFileCount get() = fileDao.count()
    private val syncedFileCountObserver = Observer<Int> { value ->
        _uiState.update { uiState -> uiState.copy(hasFilesSynced = value > 0) }
    }

    init {
        syncedFileCount.observeForever(syncedFileCountObserver)
    }

    override fun onCleared() {
        super.onCleared()
        syncedFileCount.removeObserver(syncedFileCountObserver)
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            stateChangeHandler(settingsDataStore.getSettings())
        }
    }

    fun stateChangeHandler(settings: Settings) {
        _uiState.update { uiState -> uiState.copy(settings = settings, showRemoteNotEmpty = false) }
    }

    fun save() {
        _uiState.update { uiState ->
            uiState.copy(
                ongoingIO = true,
                davConnected = null,
                davError = null
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Try settings first
                val dummyWebDavService = WebDavService.create(uiState.value.settings)
                val files = dummyWebDavService.getChildren(CollectionPath("/"))
                if (!files.isEmpty() && !uiState.value.showRemoteNotEmpty) {
                    _uiState.update { uiState ->
                        uiState.copy(
                            showRemoteNotEmpty = true
                        )
                    }
                    return@launch
                }

                // At this point settings are valid and we can connect
                settingsDataStore.setSettings(uiState.value.settings)
                _uiState.update { uiState -> uiState.copy(davConnected = true, ongoingIO = false) }
            } catch (e: MisconfigurationException) {
                _uiState.update { uiState ->
                    uiState.copy(
                        davError = "Invalid DAV settings",
                    )
                }
            } catch (e: UnauthorizedExeption) {
                _uiState.update { uiState ->
                    uiState.copy(
                        davError = "Unauthorized (wrong login/password?)",
                    )
                }
            } catch (e: NotFoundExeption) {
                _uiState.update { uiState ->
                    uiState.copy(
                        davError = "Not found : ${e.message}. Make sure your URL is valid, and that the DAV Path exists",
                    )
                }
            } catch (e: IOException) {
                val message =
                    if (e.isNextcloudWrongUrl()) "Method Not Allowed. If using nextcloud, use full URL, generally ending with: /remote.php/dav/files/${uiState.value.settings.username}" else e.message
                _uiState.update { uiState ->
                    uiState.copy(
                        davError = message,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { uiState ->
                    uiState.copy(
                        davError = e.message,
                    )
                }
            } finally {
                _uiState.update { uiState ->
                    uiState.copy(
                        ongoingIO = false
                    )
                }
            }

        }
    }
}