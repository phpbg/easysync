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
import android.os.Environment
import android.util.Log
import com.phpbg.easysync.Notifications
import com.phpbg.easysync.Permissions
import com.phpbg.easysync.R
import com.phpbg.easysync.dav.CollectionPath
import com.phpbg.easysync.dav.NotFoundExeption
import com.phpbg.easysync.dav.Resource
import com.phpbg.easysync.dav.WebDavService
import com.phpbg.easysync.db.AppDatabaseFactory
import com.phpbg.easysync.db.ErrorDao
import com.phpbg.easysync.db.File
import com.phpbg.easysync.db.FileDao
import com.phpbg.easysync.mediastore.MediaStoreFile
import com.phpbg.easysync.mediastore.MediaStoreService
import com.phpbg.easysync.settings.ConflictStrategy
import com.phpbg.easysync.settings.SettingsDataStore
import com.phpbg.easysync.showNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.time.Instant

private const val TAG = "SyncService"

class SyncService(
    private val fileDao: FileDao,
    private val errorDao: ErrorDao,
    private val mediaStoreService: MediaStoreService,
    private val permissions: Permissions,
    private val settingsDatastore: SettingsDataStore,
    private val webDavService: WebDavService
) {

    private suspend fun getConflictStrategy(): ConflictStrategy {
        val settings = settingsDatastore.getSettings()
        return settings.conflictStrategy
    }

    private suspend fun getPathExclusions(): Set<String> {
        val settings = settingsDatastore.getSettings()
        return settings.pathExclusions
    }

    suspend fun syncOne(mediaStoreFile: MediaStoreFile, skipIfInDb: Boolean) {
        Log.d(TAG, "SyncOne $mediaStoreFile")
        if (getPathExclusions().contains(mediaStoreFile.relativePath)) {
            Log.d(TAG, "SyncOne skipped, file is excluded $mediaStoreFile")
            return
        }
        val dbFile = fileDao.findById(mediaStoreFile.id)
        if (dbFile != null) {
            if (skipIfInDb) {
                Log.d(TAG, "SyncOne resync skipped $mediaStoreFile")
            } else {
                Log.d(TAG, "SyncOne resync $mediaStoreFile")
                resyncFile(dbFile)
            }
        } else {
            Log.d(TAG, "SyncOne initial sync $mediaStoreFile")
            handleInitialMediastoreFileSync(mediaStoreFile)
        }
    }

    suspend fun syncAll(context: Context, immediate: Boolean) {
        Log.d(TAG, "SyncAll immediate $immediate")
        if (!permissions.areMandatoryGranted(context)) {
            throw MissingPermissionException()
        }
        withContext(Dispatchers.IO) {
            // Remove previous errors
            errorDao.deleteAll()

            // Resync already synced files
            fileDao.getAllPathnames().forEach {
                DbFileSyncWorker.enqueue(context, it, immediate)
            }

            // Sync only new files from local
            mediaStoreService.getAllIds(getPathExclusions()).subtract(fileDao.getAllids().toSet()).forEach {
                MediastoreIdSyncWorker.enqueue(
                    context,
                    it,
                    immediate = immediate,
                    skipIfInDb = true
                )
            }

            // Sync new files from distant
            DavSyncWorker.enqueue(context, "/", immediate = immediate, isCollection = true)
        }
    }

    suspend fun resyncFile(filePath: String) {
        val dbFile = fileDao.findByName(filePath) ?: throw Exception("File not found: $filePath")
        resyncFile(dbFile)
    }

    private suspend fun resyncFile(dbFile: File) {
        Log.d(TAG, "Resync file $dbFile")
        val davFilePath = com.phpbg.easysync.dav.File(dbFile.pathname)

        if (getPathExclusions().contains(davFilePath.getPathNoLeading())) {
            Log.d(TAG, "File is excluded, remove from database $dbFile")
            fileDao.delete(dbFile)
            return
        }

        val remoteFile = try {
            webDavService.getPropertiesFromParentCache(davFilePath)
        } catch (e: NotFoundExeption) {
            null
        }

        val mediaStoreFile = mediaStoreService.getById(dbFile.id)
        Log.d(TAG, "Mediastorefile: $mediaStoreFile")
        if (remoteFile == null) {
            Log.d(TAG, "Tracked file deleted on remote: " + dbFile.pathname)
            if (mediaStoreFile != null) {
                mediaStoreService.deleteFile(mediaStoreFile)
            } else {
                withContext(Dispatchers.IO) {
                    if (localFileOrDirExists(dbFile.localPathname)) {
                        Files.delete(Paths.get(dbFile.localPathname))
                    }
                }
            }
            fileDao.delete(dbFile)
            return
        }

        if (mediaStoreFile == null || mediaStoreFile.isTrashed) {
            Log.d(TAG, "Tracked file deleted on local: " + dbFile.pathname)
            try {
                if (dbFile.isCollection) {
                    webDavService.deleteCollectionIfEmpty(CollectionPath(davFilePath.getPath()))
                } else {
                    webDavService.delete(davFilePath)
                }
            } catch (e: NotFoundExeption) {
                Log.d(TAG, "Already deleted on remote: " + dbFile.pathname)
            }
            fileDao.delete(dbFile)
            return
        }

        if (dbFile.isCollection) {
            Log.d(TAG, "Collection: " + dbFile.pathname)
            return
        }

        val localDateModified = mediaStoreFile.dateModified
        val localIdentical =
            dbFile.localDateChanged != null && dbFile.localDateChanged == localDateModified
        val remoteIdentical =
            (dbFile.etag != null && dbFile.etag == remoteFile.getetag) || (dbFile.remoteDateChanged != null && dbFile.remoteDateChanged == remoteFile.getlastmodified)

        val mediaStoreDavFilePath = getDavFileFromMediastoreFile(mediaStoreFile)
        if (dbFile.pathname != mediaStoreDavFilePath.getPath()) {
            Log.d(
                TAG,
                "Tracked file moved on local, local identical: ${localIdentical}. ${dbFile.pathname} -> ${mediaStoreDavFilePath.getPath()}"
            )
            // Since we are changing our primary key, delete & reinsert
            fileDao.delete(dbFile)
            if (localIdentical) {
                webDavService.mkcolRecursive(CollectionPath(mediaStoreFile.relativePath))
                webDavService.move(davFilePath, mediaStoreDavFilePath)
                val newDavFileProperties = webDavService.getProperties(mediaStoreDavFilePath)
                val newDbFile = createDbFile(mediaStoreFile, newDavFileProperties, isCollection = false)
                fileDao.insertAll(newDbFile)
            } else {
                uploadFile(mediaStoreFile, mediaStoreDavFilePath, null)
                try {
                    webDavService.delete(davFilePath)
                } catch (e: NotFoundExeption) {
                    Log.d(TAG, "Already deleted on remote: " + dbFile.pathname)
                }
            }
            return
        }

        if (localIdentical && remoteIdentical) {
            Log.d(TAG, "Files identical: " + dbFile.pathname)
            resyncMetadata(mediaStoreFile, dbFile)
            return
        }
        if (!localIdentical && !remoteIdentical) {
            Log.d(TAG, "Conflict: file changed on both local and remote: " + dbFile.pathname)
            when (getConflictStrategy()) {
                ConflictStrategy.KEEP_LOCAL -> {
                    Log.d(TAG, "Upload: " + dbFile.pathname)
                    uploadFile(mediaStoreFile, davFilePath, dbFile)
                }

                ConflictStrategy.KEEP_REMOTE -> {
                    Log.d(TAG, "Download: " + dbFile.pathname)
                    downloadExistingFile(
                        dbFile, remoteFile
                    )
                }

                ConflictStrategy.IGNORE -> return
            }
            return
        }
        if (!localIdentical) {
            Log.d(TAG, "Local changed: " + dbFile.pathname)
            uploadFile(mediaStoreFile, davFilePath, dbFile)
            return
        }
        Log.d(TAG, "Remote changed: " + dbFile.pathname)
        downloadExistingFile(
            dbFile, remoteFile
        )
    }

    private suspend fun downloadExistingFile(
        dbFile: File, davResource: Resource
    ) {
        val newMediaStoreImage = download(davResource, dbFile.localPathname)
            ?: throw Exception("File not found after downloading its updated copy")
        val newDbFile =
            createDbFile(newMediaStoreImage, davResource, isCollection = dbFile.isCollection)
        fileDao.updateFile(newDbFile)
    }

    private fun createDbFile(
        mediaStoreFile: MediaStoreFile,
        davResource: Resource,
        isCollection: Boolean
    ): File {
        val filePath = getDavFileFromMediastoreFile(mediaStoreFile)
        return File(
            pathname = filePath.getPath(),
            localPathname = mediaStoreFile.absolutePath,
            id = mediaStoreFile.id,
            etag = davResource.getetag,
            localDateChanged = mediaStoreFile.dateModified,
            remoteDateChanged = davResource.getlastmodified,
            isCollection = isCollection
        )
    }

    private suspend fun download(
        davResource: Resource,
        absoluteLocalPath: String
    ): MediaStoreFile? {
        val davFile = com.phpbg.easysync.dav.File(davResource)
        webDavService.download(davFile, absoluteLocalPath)

        // Restore file attributes
        withContext(Dispatchers.IO) {
            val absoluteLocalPathNio = Paths.get(absoluteLocalPath)
            if (davResource.creationdate != null) {
                Files.setAttribute(
                    absoluteLocalPathNio,
                    "creationTime",
                    FileTime.from(davResource.creationdate)
                )
            }
            if (davResource.getlastmodified != null) {
                Files.setAttribute(
                    absoluteLocalPathNio,
                    "lastModifiedTime",
                    FileTime.from(davResource.getlastmodified)
                )
            }
        }

        // NB: beware that uri (e.g. content://media/external_primary/images/media/1000000191) may differ from mediaStoreFile.uri (e.g. content://media/external/images/media/1000000191)
        val uri = mediaStoreService.notifyUpdatedFile(absoluteLocalPath)
        return mediaStoreService.getOneByUri(uri)
    }

    suspend fun handleSyncResource(path: com.phpbg.easysync.dav.File) {
        val pathStr = path.getPath()
        if (getPathExclusions().contains(path.getPathNoLeading())) {
            Log.d(TAG, "DAV file excluded, skipping $pathStr")
            return
        }
        val dbFile = fileDao.findByName(pathStr)
        val localPath =
            Environment.getExternalStorageDirectory().canonicalPath + pathStr
        if (dbFile == null && !localFileOrDirExists(localPath)) {
            Log.d(TAG, "DAV file only on remote, download $pathStr")
            val resource = webDavService.getProperties(path)
            downloadNewFile(resource, localPath)
            return
        }
        Log.d(TAG, "DAV file already synchronized: $pathStr")
    }

    private suspend fun downloadNewFile(
        resource: Resource,
        absoluteLocalPath: String
    ) {
        val mediaStoreFile = download(resource, absoluteLocalPath)
        if (mediaStoreFile == null) {
            // Won't further sync if not in mediaStore
            Log.w(TAG, "Won't sync as not in mediaStore: ${resource.relativeHref}")
            return
        }

        val newDbFile = createDbFile(mediaStoreFile, resource, isCollection = false)
        fileDao.insertAll(newDbFile)
    }

    private fun getDavFileFromMediastoreFile(mediaStoreFile: MediaStoreFile): com.phpbg.easysync.dav.File {
        return com.phpbg.easysync.dav.File(
            CollectionPath(mediaStoreFile.relativePath),
            mediaStoreFile.displayName
        )
    }

    private suspend fun handleInitialMediastoreFileSync(
        mediaStoreFile: MediaStoreFile
    ) {
        Log.d(TAG, "Initial mediastore sync: ${mediaStoreFile.relativePath}")
        val davFilePath = getDavFileFromMediastoreFile(mediaStoreFile)
        val dbFile = fileDao.findByName(davFilePath.getPath())
        if (dbFile != null) {
            // Files already synchronized are already taken care of
            return
        }
        if (mediaStoreFile.isTrashed) {
            Log.d(TAG, "File is trashed, aborting: ${mediaStoreFile.relativePath}")
            return
        }
        var davFileProperties: Resource? = null
        try {
            davFileProperties = webDavService.getProperties(davFilePath)
        } catch (err: NotFoundExeption) {
            // file not present on remote
        }
        if (davFileProperties == null) {
            Log.d(TAG, "First synchronization: ${davFilePath.getPath()}")
            uploadFile(mediaStoreFile, davFilePath, null)
            return
        }
        if (isCollection(mediaStoreFile)) {
            Log.d(TAG, "Collection already exists on both side: ${davFilePath.getPath()}")
            val newDbFile = createDbFile(mediaStoreFile, davFileProperties, isCollection = true)
            fileDao.insertAll(newDbFile)
            return
        }
        Log.d(
            TAG,
            "Conflict: file present on both sides but not synced: ${davFilePath.getPath()}"
        )
        when (getConflictStrategy()) {
            ConflictStrategy.KEEP_LOCAL -> {
                Log.d(TAG, "Upload: ${davFilePath.getPath()}")
                uploadFile(mediaStoreFile, davFilePath, null)
            }

            ConflictStrategy.KEEP_REMOTE -> {
                Log.d(TAG, "Download: ${davFilePath.getPath()}")
                downloadNewFile(
                    davFileProperties,
                    mediaStoreFile.absolutePath
                )
            }

            ConflictStrategy.IGNORE -> return
        }
    }

    private suspend fun resyncMetadata(mediaStoreFile: MediaStoreFile, dbFile: File) {
        // Make sure we keep absolutePath in sync, in case it changes (it happens sometimes, don't know exaclty why)
        if (mediaStoreFile.absolutePath != dbFile.localPathname) {
            Log.d(
                TAG,
                "File path changed, resyncing: ${mediaStoreFile.absolutePath} - ${dbFile.localPathname}"
            )
            val newDbFile = dbFile.copy(
                localPathname = mediaStoreFile.absolutePath
            )
            fileDao.updateFile(newDbFile)
        }
    }

    private fun isCollection(file: MediaStoreFile): Boolean {
        val path = Paths.get(file.absolutePath)
        return Files.isDirectory(path)
    }

    private suspend fun uploadFile(
        mediaStoreFile: MediaStoreFile,
        davFilePath: com.phpbg.easysync.dav.File,
        oldDbFile: File?
    ) {
        val isCollection = isCollection(mediaStoreFile)
        if (isCollection) {
            webDavService.mkcolRecursive(CollectionPath(davFilePath.getPath()))
        } else {
            webDavService.mkcolRecursive(CollectionPath(mediaStoreFile.relativePath))
            webDavService.put(mediaStoreFile.absolutePath, davFilePath)
        }
        val newDavFileProperties = webDavService.getProperties(davFilePath)
        val newDbFile = createDbFile(mediaStoreFile, newDavFileProperties, isCollection)
        if (oldDbFile == null) {
            fileDao.insertAll(newDbFile)
        } else {
            fileDao.updateFile(newDbFile)
        }
    }

    private fun localFileOrDirExists(filePath: String): Boolean {
        val path = Paths.get(filePath)
        when {
            Files.isDirectory(path) -> {
                return true
            }

            Files.exists(path) -> {
                return true
            }

            Files.notExists(path) -> {
                return false
            }

            else -> {
                throw Exception("Missing permission")
            }
        }
    }

    suspend fun handleWorkerException(context: Context, e: Exception, path: String) {
        errorDao.insertAll(
            com.phpbg.easysync.db.Error(
                createdDate = Instant.now(),
                path = path,
                message = e.message ?: e.stackTraceToString()
            )
        )
        showErrorNotification(context)
    }

    private fun showErrorNotification(context: Context) {
        val title = context.getString(R.string.notification_sync_error_title)
        val text = context.getString(R.string.notification_sync_error_text)
        val notificationId = Notifications.MISSING_PERMISSIONS
        showNotification(context, title, text, notificationId)
    }

    companion object {
        @Volatile
        private var instance: SyncService? = null
        private val mutex = Mutex()

        suspend fun getInstance(context: Context): SyncService {
            if (instance == null) {
                mutex.withLock {
                    if (instance == null) {
                        instance = create(context)
                    }
                }
            }
            return instance!!
        }

        private suspend fun create(context: Context): SyncService {
            val mediaStoreService = MediaStoreService(context)
            val db = AppDatabaseFactory.create(context)
            val fileDao = db.fileDao()
            val errorDao = db.errorDao()
            val permissions = Permissions
            val settingsDatastore = SettingsDataStore(context)
            val webDavService = WebDavService.getInstance(settingsDatastore.getSettingsAsFlow())
            return SyncService(
                fileDao,
                errorDao,
                mediaStoreService,
                permissions,
                settingsDatastore,
                webDavService
            )
        }
    }
}