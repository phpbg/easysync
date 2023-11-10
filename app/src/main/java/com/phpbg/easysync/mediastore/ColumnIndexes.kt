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

package com.phpbg.easysync.mediastore

import android.database.Cursor
import android.os.Build
import android.provider.MediaStore

data class ColumnIndexes(
    val idColumn: Int,
    val dateModifiedColumn: Int,
    val displayNameColumn: Int,
    val dataColumn: Int,
    val relativePathColumn: Int?,
    val isTrashed: Int?
) {
    companion object {
        val projection: Array<String>

        init {
            val tmpProjection = mutableListOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DATA,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                tmpProjection.add(MediaStore.Files.FileColumns.IS_TRASHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tmpProjection.add(MediaStore.Files.FileColumns.RELATIVE_PATH)
            }
            projection = tmpProjection.toTypedArray()
        }

        fun fromCursor(cursor: Cursor): ColumnIndexes {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return ColumnIndexes(
                    idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID),
                    dateModifiedColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED),
                    displayNameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME),
                    dataColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA),
                    relativePathColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH),
                    isTrashed = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.IS_TRASHED),
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return ColumnIndexes(
                    idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID),
                    dateModifiedColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED),
                    displayNameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME),
                    dataColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA),
                    relativePathColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH),
                    isTrashed = null
                )
            }
            return ColumnIndexes(
                idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID),
                dateModifiedColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED),
                displayNameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME),
                dataColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA),
                relativePathColumn = null,
                isTrashed = null
            )
        }
    }
}
