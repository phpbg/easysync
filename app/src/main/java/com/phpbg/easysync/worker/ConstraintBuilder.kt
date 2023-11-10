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

package com.phpbg.easysync.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import com.phpbg.easysync.settings.SettingsDataStore

class ConstraintBuilder {
    companion object {
        suspend fun getFullSyncConstraints(context: Context, immediate: Boolean): Constraints.Builder {
            val constraintsBuilder = Constraints.Builder()
            if (immediate) {
                constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
            } else {
                val settingsDataStore = SettingsDataStore(context)
                val settings = settingsDataStore.getSettings()
                constraintsBuilder.setRequiresCharging(!settings.syncOnBattery)
                if (settings.syncOnCellular) {
                    constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
                } else {
                    constraintsBuilder.setRequiredNetworkType(NetworkType.UNMETERED)
                }
            }
            return constraintsBuilder
        }
    }
}