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

package com.phpbg.easysync.settings

import android.content.Context
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

private val Context.dataStore by dataStore(
    fileName = "settings.json", serializer = SettingsSerializer(
        CryptoManager()
    )
)

class SettingsDataStore constructor(context: Context) {
    private val dataSource = context.dataStore

    suspend fun getSettings(): Settings {
        return dataSource.data.first()
    }

    fun getSettingsAsFlow(): Flow<Settings> {
        return dataSource.data
    }

    suspend fun setSettings(settings: Settings) {
        dataSource.updateData {
            settings
        }
    }

    suspend fun setSyncOnCellular(syncOnCellular: Boolean) {
        dataSource.updateData { currentSettings ->
            currentSettings.copy(syncOnCellular = syncOnCellular)
        }
    }

    suspend fun setSyncOnBattery(syncOnBattery: Boolean) {
        dataSource.updateData { currentSettings ->
            currentSettings.copy(syncOnBattery = syncOnBattery)
        }
    }

    suspend fun setConflictStrategy(conflictStrategy: ConflictStrategy) {
        dataSource.updateData { currentSettings ->
            currentSettings.copy(conflictStrategy = conflictStrategy)
        }
    }
}