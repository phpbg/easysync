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
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.phpbg.easysync.settings.ConflictStrategy
import com.phpbg.easysync.settings.Settings
import com.phpbg.easysync.settings.SettingsDataStore
import kotlinx.coroutines.launch

class SyncSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsDataStore = SettingsDataStore(getApplication())
    val settings: LiveData<Settings> get() = settingsDataStore.getSettingsAsFlow().asLiveData()

    fun syncOnCellularHandler(syncOnCellular: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setSyncOnCellular(syncOnCellular)
        }
    }

    fun syncOnBatteryHandler(syncOnBattery: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setSyncOnBattery(syncOnBattery)
        }
    }

    fun conflictStrategyHandler(conflictStrategyStr: String) {
        val conflictStrategy = ConflictStrategy.valueOf(conflictStrategyStr)
        viewModelScope.launch {
            settingsDataStore.setConflictStrategy(conflictStrategy)
        }
    }
}