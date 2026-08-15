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
import com.phpbg.easysync.db.AppDatabaseFactory
import com.phpbg.easysync.mediastore.MediaStoreService
import com.phpbg.easysync.settings.SettingsDataStore
import com.phpbg.easysync.util.toDavFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SyncStatus {
    SYNCED,
    NOT_SYNCED,
    ERROR
}

data class LocalFileItem(
    /**
     * Relative pathname, plain string, identical to the one stored in the file and error tables
     */
    val pathname: String,
    val status: SyncStatus,
    val errorMessage: String?
)

class LocalFilesViewModel(application: Application) : AndroidViewModel(application) {
    private val mediaStoreService = MediaStoreService(getApplication())
    private val settingsDataStore = SettingsDataStore(getApplication())
    private val db = AppDatabaseFactory.create(getApplication())
    private val fileDao = db.fileDao()
    private val errorDao = db.errorDao()

    private val _files = MutableLiveData<List<LocalFileItem>>()
    val files: LiveData<List<LocalFileItem>> get() = _files

    fun load() {
        viewModelScope.launch {
            _files.postValue(buildList())
        }
    }

    private suspend fun buildList(): List<LocalFileItem> = withContext(Dispatchers.IO) {
        val pathExclusions = settingsDataStore.getSettings().pathExclusions
        val syncedPathnames = fileDao.getAllPathnames().toHashSet()
        val errorsByPathname = errorDao.getAllList().associate { it.path to it.message }

        mediaStoreService.getAllFiles(pathExclusions)
            .sortedByDescending { it.dateModified }
            .map { mediaStoreFile ->
                val pathname = mediaStoreFile.toDavFile().getPath()
                val errorMessage = errorsByPathname[pathname]
                val status = when {
                    errorMessage != null -> SyncStatus.ERROR
                    syncedPathnames.contains(pathname) -> SyncStatus.SYNCED
                    else -> SyncStatus.NOT_SYNCED
                }
                LocalFileItem(pathname, status, errorMessage)
            }
            .sortedBy { it.errorMessage == null }
    }
}
