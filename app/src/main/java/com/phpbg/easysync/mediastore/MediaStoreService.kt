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

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val TAG = "MediaStoreService"

val URIS = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    arrayOf(
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    )
} else {
    arrayOf(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    )
})

class MediaStoreService(private val context: Context) {
    /**
     * Return all unique files to be synced
     * Note that a file may be present multiple collections at once.
     * E.g. a donwloaded image will be present both in Downloads and Images
     * For this reasons we merge all IDs in a Set
     */
    suspend fun getAllIds(): Set<Long> {
        return URIS
            .map { queryIds(it) }
            .reduce { acc, longs -> acc.union(longs) }
    }

    /**
     * Count all unique files to be synced
     */
    suspend fun countAll(): Int {
        return getAllIds().size
    }

    private suspend fun queryIds(uri: Uri): Set<Long> {
        val res = withContext(Dispatchers.IO) {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Files.FileColumns._ID),
                null,
                null,
                null
            )?.use { cursor ->
                val results = HashSet<Long>(cursor.count)
                val idColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                while (cursor.moveToNext()) {
                    results.add(cursor.getLong(idColumnIndex))
                }
                return@withContext results
            }
        }
        return res ?: setOf()
    }

    suspend fun getAllPaths(): Set<String> {
        return URIS
            .map { queryPaths(it) }
            .reduce { acc, paths -> acc.union(paths) }
    }

    private suspend fun queryPaths(uri: Uri): Set<String> {
        val res = withContext(Dispatchers.IO) {
            val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(
                    MediaStore.Files.FileColumns.RELATIVE_PATH,
                    MediaStore.Files.FileColumns.DATA,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                )
            } else {
                arrayOf(
                    MediaStore.Files.FileColumns.DATA,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                )
            }
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val results = HashSet<String>(cursor.count)
                while (cursor.moveToNext()) {
                    val absolutePath =
                        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA))
                    val displayName =
                        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME))
                    val relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH))
                    } else {
                        guessRelativePath(absolutePath, displayName)
                    }
                    results.add(relativePath)
                }
                return@withContext results
            }
        }
        return res ?: setOf()
    }

    suspend fun deleteFile(file: MediaStoreFile) {
        withContext(Dispatchers.IO) {
            context.contentResolver.delete(idToUri(file.id), null, null)
        }
    }

    /**
     * Note that Uri returned by scanner may differ from URI when querying Mediastore
     * Example: on android 12 and 13, for the same file:
     *   scanner returns: content://media/external_primary/file/1000000083
     *   mediastore returns: content://media/external_primary/downloads/1000000083
     *   both URIs are valid
     */
    suspend fun notifyUpdatedFile(path: String): Uri {
        return suspendCoroutine { cont ->
            val onScanCompleted = { _: String, uri: Uri ->
                cont.resume(uri)
            }
            MediaScannerConnection.scanFile(
                context,
                arrayOf(path),
                null,
                onScanCompleted
            )
        }
    }

    suspend fun getOneByUri(uri: Uri): MediaStoreFile? {
        var image: MediaStoreFile? = null
        withContext(Dispatchers.IO) {
            context.contentResolver.query(
                uri,
                ColumnIndexes.projection,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.count > 1) {
                    throw Error("Expecting none or one, found many")
                }
                if (cursor.moveToFirst()) {
                    val columnIndexes = ColumnIndexes.fromCursor(cursor)
                    image = getFromCursor(cursor, columnIndexes)
                }
            }
        }
        return image
    }

    suspend fun getByUri(uri: Uri): List<MediaStoreFile> {
        val images = mutableListOf<MediaStoreFile>()
        withContext(Dispatchers.IO) {
            context.contentResolver.query(
                uri,
                ColumnIndexes.projection,
                null,
                null,
                null
            )?.use { cursor ->
                val columnIndexes = ColumnIndexes.fromCursor(cursor)
                while (cursor.moveToNext()) {
                    val image = getFromCursor(cursor, columnIndexes)
                    Log.d(TAG, "$image")
                    images += image
                }
            }
        }
        return images
    }

    suspend fun getById(id: Long): MediaStoreFile? {
        // https://developer.android.com/training/data-storage/shared/media#storage-volume
        // The VOLUME_EXTERNAL volume provides a view of all shared storage volumes on the device.
        // We should be able to find any file by ID using this volume
        return getOneByUri(idToUri(id))
    }

    private fun getVolumesPath(): Set<String> {
        val storageManager =
            context.getSystemService(ComponentActivity.STORAGE_SERVICE) as StorageManager
        val volumes = storageManager.javaClass
            .getMethod("getVolumePaths", *arrayOfNulls(0))
            .invoke(storageManager, *arrayOfNulls(0)) as Array<String>
        return setOf<String>(Environment.getExternalStorageDirectory().canonicalPath).plus(volumes)
    }

    private fun guessRelativePath(absolutePath: String, displayName: String): String {
        val rootPath = getVolumesPath().find { absolutePath.startsWith(it) }
            ?: throw Exception("Unable to determine relative path for file $absolutePath")
        // Compute relative path on sdk where relative path is not supported
        if (!absolutePath.endsWith(displayName)) {
            throw Exception("Unable to determine relative path for file $absolutePath - $displayName")
        }
        // Relative path should looks like DCIM/ (no leading /, no filename)
        return absolutePath.substringAfter(rootPath).substringBefore(displayName).trimStart('/')
    }

    private fun getFromCursor(
        cursor: Cursor,
        columnIndexes: ColumnIndexes,
    ): MediaStoreFile {
        val id = cursor.getLong(columnIndexes.idColumn)
        val dateModified = Instant.ofEpochSecond(cursor.getLong(columnIndexes.dateModifiedColumn))
        val displayName = cursor.getString(columnIndexes.displayNameColumn)
        val absolutePath = cursor.getString(columnIndexes.dataColumn)
        val isTrashed =
            if (columnIndexes.isTrashed == null) false else cursor.getInt(columnIndexes.isTrashed) == 1

        val relativePath = if (columnIndexes.relativePathColumn == null) {
            guessRelativePath(absolutePath, displayName)
        } else {
            cursor.getString(columnIndexes.relativePathColumn)
        }

        return MediaStoreFile(
            id,
            displayName,
            dateModified,
            absolutePath,
            relativePath,
            isTrashed
        )
    }

    companion object {
        @SuppressLint("InlinedApi")
        fun idToUri(id: Long): Uri {
            return MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL, id)
        }
    }
}