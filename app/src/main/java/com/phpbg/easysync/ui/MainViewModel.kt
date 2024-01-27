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
import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.phpbg.easysync.MyApp
import com.phpbg.easysync.dav.CollectionPath
import com.phpbg.easysync.dav.MisconfigurationException
import com.phpbg.easysync.dav.WebDavService
import com.phpbg.easysync.db.AppDatabaseFactory
import com.phpbg.easysync.mediastore.MediaStoreService
import com.phpbg.easysync.settings.SettingsDataStore
import com.phpbg.easysync.worker.FileDetectWorker
import com.phpbg.easysync.worker.FullSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.Exception

private const val TAG = "MainActivityViewModel"

/**
 * Convenience extension method to register a [ContentObserver] given a lambda.
 */
private fun ContentResolver.registerObserver(
    uri: Uri,
    observer: (selfChange: Boolean) -> Unit
): ContentObserver {
    val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            observer(selfChange)
        }
    }
    registerContentObserver(uri, true, contentObserver)
    return contentObserver
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _localFilesCount = MutableLiveData<Int>()
    val localFilesCount: LiveData<Int> get() = _localFilesCount
    private var contentObserver: ContentObserver? = null
    private val mediaStoreService = MediaStoreService(getApplication())

    private val settingsDataStore = SettingsDataStore(getApplication())

    private val db = AppDatabaseFactory.create(getApplication())
    private val fileDao = db.fileDao()
    val syncedFileCount get() = fileDao.count()

    val showDavStatus = mutableStateOf(false)
    val isDavConnected = mutableStateOf(false)
    val isDavLoading = mutableStateOf(false)

    val isTrial = mutableStateOf(false)
    val trialRemainingDays = mutableIntStateOf(0)

    val workInfosList = FullSyncWorker.getLiveData(this.getApplication()).map { x ->
        if (x.isEmpty()) {
            return@map null
        }
        val workInfo = x.first()
        return@map workInfo.state
    }

    val jobCount = FullSyncWorker.getAllLiveData(this.getApplication()).map { x ->
        return@map x.filter {
            it.state in arrayOf(
                WorkInfo.State.RUNNING,
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.BLOCKED
            )
        }
            .count()
    }

    fun load() {
        viewModelScope.launch {
            // Make sure workers are enqueued
            val settings = settingsDataStore.getSettings()
            if (settings.url.isNotEmpty()) {
                FileDetectWorker.enqueue(getApplication())
                FullSyncWorker.enqueueKeep(getApplication())
                showDavStatus.value = true
            }
        }
        viewModelScope.launch {
            loadImages()
        }
        viewModelScope.launch {
            loadDav()
        }

        isTrial.value = MyApp.isTrial()
        trialRemainingDays.intValue = MyApp.getTrialRemainingDays(getApplication())
    }

    private fun loadImages() {
        Log.i(TAG, "load images")
        viewModelScope.launch {
            _localFilesCount.postValue(mediaStoreService.countAll())

            if (contentObserver == null) {
                contentObserver = getApplication<Application>().contentResolver.registerObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                ) {
                    loadImages()
                }
            }
        }
    }

    private fun loadDav() {
        isDavLoading.value = true
        isDavConnected.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val webDavService = WebDavService.getInstance(settingsDataStore.getSettingsAsFlow())
                webDavService.getProperties(CollectionPath("/"))
                isDavConnected.value = true
            } catch (e:MisconfigurationException) {
                Log.d(TAG, "Cannot create DAV client")
            } catch (e: Exception) {
                Log.e(TAG, e.stackTraceToString())
            } finally {
                isDavLoading.value = false
            }
        }
    }

    fun fullSyncNowHandler() {
        viewModelScope.launch {
            FullSyncWorker.enqueueImmediate(getApplication())
        }
    }
}