/*
 * MIT License
 *
 * Copyright (c) 2026 Samuel CHEMLA
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
import android.util.Log
import com.phpbg.easysync.Permissions
import com.phpbg.easysync.dav.NotFoundExeption
import com.phpbg.easysync.dav.RootPath
import com.phpbg.easysync.dav.WebDavService
import com.phpbg.easysync.db.ErrorDao
import com.phpbg.easysync.db.FileDao
import com.phpbg.easysync.mediastore.MediaStoreFile
import com.phpbg.easysync.mediastore.MediaStoreService
import com.phpbg.easysync.settings.ConflictStrategy
import com.phpbg.easysync.settings.Settings
import com.phpbg.easysync.settings.SettingsDataStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class SyncServiceTest {

    private val fileDao: FileDao = mock()
    private val errorDao: ErrorDao = mock()
    private val mediaStoreService: MediaStoreService = mock()
    private val permissions: Permissions = mock()
    private val settingsDatastore: SettingsDataStore = mock()
    private val webDavService: WebDavService = mock()
    private val context: Context = mock()

    private lateinit var syncService: SyncService
    private lateinit var mockedLog: MockedStatic<Log>

    @Before
    fun setup() {
        mockedLog = mockStatic(Log::class.java)
        syncService = SyncService(
            fileDao,
            errorDao,
            mediaStoreService,
            permissions,
            settingsDatastore,
            webDavService
        )
    }

    @After
    fun tearDown() {
        mockedLog.close()
    }

    @Test
    fun `syncOne should skip if file is excluded`() = runTest {
        val relativePath = "ExcludedDir/file.jpg"
        val mediaStoreFile = MediaStoreFile(
            id = 1L,
            displayName = "file.jpg",
            relativePath = relativePath,
            absolutePath = "/storage/emulated/0/$relativePath",
            dateModified = Instant.now(),
            isTrashed = false,
        )
        val settings = Settings(pathExclusions = setOf(relativePath))
        whenever(settingsDatastore.getSettings()).doReturn(settings)

        syncService.syncOne(mediaStoreFile, skipIfInDb = false)

        verify(fileDao, never()).findById(any())
    }

    @Test(expected = MissingPermissionException::class)
    fun `syncAll should throw exception if permissions are not granted`() = runTest {
        whenever(permissions.areMandatoryGranted(context)).doReturn(false)

        syncService.syncAll(context, immediate = false)
    }

    @Test
    fun `syncOne should not call resync if skipIfInDb is true and file exists`() = runTest {
        val relativePath = "DCIM/Camera/"
        val mediaStoreFile = MediaStoreFile(
            id = 1L,
            displayName = "photo.jpg",
            relativePath = relativePath,
            absolutePath = "/storage/emulated/0/DCIM/Camera/photo.jpg",
            dateModified = Instant.now(),
            isTrashed = false,
        )
        val settings = Settings(pathExclusions = emptySet())
        whenever(settingsDatastore.getSettings()).doReturn(settings)
        val dbFile = com.phpbg.easysync.db.File(
            pathname = "/DCIM/Camera/photo.jpg",
            localPathname = "/storage/emulated/0/DCIM/Camera/photo.jpg",
            id = 1L,
            etag = "etag",
            localDateChanged = null,
            remoteDateChanged = null,
            isCollection = false
        )
        whenever(fileDao.findById(1L)).doReturn(dbFile)

        syncService.syncOne(mediaStoreFile, skipIfInDb = true)

        verify(fileDao).findById(1L)
        verify(webDavService, never()).getPropertiesFromParentCache(any())
    }

    @Test
    fun `resyncFile should delete from DB and local when deleted on remote`() = runTest {
        val dbFile = com.phpbg.easysync.db.File(
            pathname = "/DCIM/photo.jpg",
            localPathname = "/storage/emulated/0/DCIM/photo.jpg",
            id = 1L,
            etag = "etag",
            localDateChanged = null,
            remoteDateChanged = null,
            isCollection = false
        )
        val mediaStoreFile = MediaStoreFile(
            id = 1L,
            displayName = "photo.jpg",
            relativePath = "DCIM/",
            absolutePath = "/storage/emulated/0/DCIM/photo.jpg",
            dateModified = Instant.now(),
            isTrashed = false,
        )
        val settings = Settings(pathExclusions = emptySet())
        whenever(settingsDatastore.getSettings()).doReturn(settings)
        whenever(webDavService.getPropertiesFromParentCache(any())).doAnswer {
            throw NotFoundExeption(
                ""
            )
        }
        whenever(mediaStoreService.getById(1L)).doReturn(mediaStoreFile)
        whenever(fileDao.findByName(dbFile.pathname)).doReturn(dbFile)

        syncService.resyncFile(dbFile.pathname)

        verify(mediaStoreService).deleteFile(mediaStoreFile)
        verify(fileDao).delete(dbFile)
    }

    @Test
    fun `resyncFile should delete from DB and remote when deleted on local`() = runTest {
        val dbFile = com.phpbg.easysync.db.File(
            pathname = "/DCIM/photo.jpg",
            localPathname = "/storage/emulated/0/DCIM/photo.jpg",
            id = 1L,
            etag = "etag",
            localDateChanged = null,
            remoteDateChanged = null,
            isCollection = false
        )
        val settings = Settings(pathExclusions = emptySet())
        whenever(settingsDatastore.getSettings()).doReturn(settings)
        val remoteResource: com.phpbg.easysync.dav.Resource = mock()
        whenever(webDavService.getPropertiesFromParentCache(any())).doReturn(remoteResource)
        whenever(mediaStoreService.getById(1L)).doReturn(null)
        whenever(fileDao.findByName(dbFile.pathname)).doReturn(dbFile)

        syncService.resyncFile(dbFile.pathname)

        verify(webDavService).delete(any())
        verify(fileDao).delete(dbFile)
    }

    @Test
    fun `resyncFile should do nothing when files are identical`() = runTest {
        val now = Instant.now()
        val dbFile = com.phpbg.easysync.db.File(
            pathname = "/DCIM/photo.jpg",
            localPathname = "/storage/emulated/0/DCIM/photo.jpg",
            id = 1L,
            etag = "etag1",
            localDateChanged = now,
            remoteDateChanged = now,
            isCollection = false
        )
        val mediaStoreFile = MediaStoreFile(
            id = 1L,
            displayName = "photo.jpg",
            relativePath = "DCIM/",
            absolutePath = "/storage/emulated/0/DCIM/photo.jpg",
            dateModified = now,
            isTrashed = false,
        )
        val remoteResource = com.phpbg.easysync.dav.Resource(
            rootPath = mock(),
            href = "/remote.php/dav/files/user/DCIM/photo.jpg",
            creationdate = null,
            getlastmodified = now,
            isCollection = false,
            getetag = "etag1",
            getcontentlength = "100",
            getcontenttype = "image/jpeg"
        )
        val settings = Settings(pathExclusions = emptySet())
        whenever(settingsDatastore.getSettings()).doReturn(settings)
        whenever(webDavService.getPropertiesFromParentCache(any())).doReturn(remoteResource)
        whenever(mediaStoreService.getById(1L)).doReturn(mediaStoreFile)
        whenever(fileDao.findByName(dbFile.pathname)).doReturn(dbFile)

        syncService.resyncFile(dbFile.pathname)

        verify(webDavService, never()).put(any(), any())
        verify(webDavService, never()).download(any(), any())
    }

    @Test
    fun `resyncFile should upload when local changed`() = runTest {
        val oldDate = Instant.now().minusSeconds(100)
        val newDate = Instant.now()
        val dbFile = com.phpbg.easysync.db.File(
            pathname = "/DCIM/photo.jpg",
            localPathname = "/storage/emulated/0/DCIM/photo.jpg",
            id = 1L,
            etag = "etag1",
            localDateChanged = oldDate,
            remoteDateChanged = oldDate,
            isCollection = false
        )
        val mediaStoreFile = MediaStoreFile(
            id = 1L,
            displayName = "photo.jpg",
            relativePath = "DCIM/",
            absolutePath = "/storage/emulated/0/DCIM/photo.jpg",
            dateModified = newDate,
            isTrashed = false,
        )
        val remoteResource = com.phpbg.easysync.dav.Resource(
            rootPath = mock(),
            href = "/remote.php/dav/files/user/DCIM/photo.jpg",
            creationdate = null,
            getlastmodified = oldDate,
            isCollection = false,
            getetag = "etag1",
            getcontentlength = "100",
            getcontenttype = "image/jpeg"
        )
        val settings = Settings(pathExclusions = emptySet())
        whenever(settingsDatastore.getSettings()).doReturn(settings)
        whenever(webDavService.getPropertiesFromParentCache(any())).doReturn(remoteResource)
        whenever(webDavService.getProperties(any<com.phpbg.easysync.dav.File>())).doReturn(
            remoteResource
        )
        whenever(mediaStoreService.getById(1L)).doReturn(mediaStoreFile)
        whenever(fileDao.findByName(dbFile.pathname)).doReturn(dbFile)

        syncService.resyncFile(dbFile.pathname)

        verify(webDavService).put(any(), any())
        verify(fileDao).updateFile(any())
    }

    @Test
    fun `resyncFile should upload when conflict and strategy is KEEP_LOCAL`() = runTest {
        val oldDate = Instant.now().minusSeconds(200)
        val localDate = Instant.now().minusSeconds(100)
        val remoteDate = Instant.now()
        val dbFile = com.phpbg.easysync.db.File(
            pathname = "/DCIM/photo.jpg",
            localPathname = "/storage/emulated/0/DCIM/photo.jpg",
            id = 1L,
            etag = "old_etag",
            localDateChanged = oldDate,
            remoteDateChanged = oldDate,
            isCollection = false
        )
        val mediaStoreFile = MediaStoreFile(
            id = 1L,
            displayName = "photo.jpg",
            relativePath = "DCIM/",
            absolutePath = "/storage/emulated/0/DCIM/photo.jpg",
            dateModified = localDate,
            isTrashed = false,
        )
        val remoteResource = com.phpbg.easysync.dav.Resource(
            rootPath = mock(),
            href = "/remote.php/dav/files/user/DCIM/photo.jpg",
            creationdate = null,
            getlastmodified = remoteDate,
            isCollection = false,
            getetag = "new_etag",
            getcontentlength = "200",
            getcontenttype = "image/jpeg"
        )
        val settings =
            Settings(pathExclusions = emptySet(), conflictStrategy = ConflictStrategy.KEEP_LOCAL)
        whenever(settingsDatastore.getSettings()).doReturn(settings)
        whenever(webDavService.getPropertiesFromParentCache(any())).doReturn(remoteResource)
        whenever(webDavService.getProperties(any<com.phpbg.easysync.dav.File>())).doReturn(
            remoteResource
        )
        whenever(mediaStoreService.getById(1L)).doReturn(mediaStoreFile)
        whenever(fileDao.findByName(dbFile.pathname)).doReturn(dbFile)

        syncService.resyncFile(dbFile.pathname)

        verify(webDavService).put(any(), any())
        verify(fileDao).updateFile(any())
    }

    @Test
    fun `resyncFile should download when conflict and strategy is KEEP_REMOTE`() = runTest {
        val oldDate = Instant.now().minusSeconds(200)
        val localDate = Instant.now().minusSeconds(100)
        val remoteDate = Instant.now()
        val dbFile = com.phpbg.easysync.db.File(
            pathname = "/DCIM/photo.jpg",
            localPathname = "/storage/emulated/0/DCIM/photo.jpg",
            id = 1L,
            etag = "old_etag",
            localDateChanged = oldDate,
            remoteDateChanged = oldDate,
            isCollection = false
        )
        val mediaStoreFile = MediaStoreFile(
            id = 1L,
            displayName = "photo.jpg",
            relativePath = "DCIM/",
            absolutePath = "/storage/emulated/0/DCIM/photo.jpg",
            dateModified = localDate,
            isTrashed = false,
        )
        val remoteResource = com.phpbg.easysync.dav.Resource(
            rootPath = RootPath("http://foo/remote.php/dav/files/user"),
            href = "/remote.php/dav/files/user/DCIM/photo.jpg",
            creationdate = null,
            getlastmodified = null,
            isCollection = false,
            getetag = "new_etag",
            getcontentlength = "200",
            getcontenttype = "image/jpeg"
        )
        val settings =
            Settings(pathExclusions = emptySet(), conflictStrategy = ConflictStrategy.KEEP_REMOTE)
        whenever(settingsDatastore.getSettings()).doReturn(settings)
        whenever(webDavService.getPropertiesFromParentCache(any())).doReturn(remoteResource)

        whenever(mediaStoreService.getById(1L)).doReturn(mediaStoreFile)
        whenever(fileDao.findByName(dbFile.pathname)).doReturn(dbFile)

        val updatedMediaStoreFile = mediaStoreFile.copy(dateModified = remoteDate)
        whenever(mediaStoreService.notifyUpdatedFile(any())).doReturn(mock())
        whenever(mediaStoreService.getOneByUri(any())).doReturn(updatedMediaStoreFile)

        mockStatic(java.nio.file.Files::class.java).use {
            syncService.resyncFile(dbFile.pathname)
        }

        verify(webDavService).download(any(), any())
        verify(fileDao).updateFile(any())
    }

    @Test
    fun `resyncFile should do nothing when conflict and strategy is IGNORE`() = runTest {
        val oldDate = Instant.now().minusSeconds(200)
        val localDate = Instant.now().minusSeconds(100)
        val remoteDate = Instant.now()
        val dbFile = com.phpbg.easysync.db.File(
            pathname = "/DCIM/photo.jpg",
            localPathname = "/storage/emulated/0/DCIM/photo.jpg",
            id = 1L,
            etag = "old_etag",
            localDateChanged = oldDate,
            remoteDateChanged = oldDate,
            isCollection = false
        )
        val mediaStoreFile = MediaStoreFile(
            id = 1L,
            displayName = "photo.jpg",
            relativePath = "DCIM/",
            absolutePath = "/storage/emulated/0/DCIM/photo.jpg",
            dateModified = localDate,
            isTrashed = false,
        )
        val remoteResource = com.phpbg.easysync.dav.Resource(
            rootPath = mock(),
            href = "/remote.php/dav/files/user/DCIM/photo.jpg",
            creationdate = null,
            getlastmodified = remoteDate,
            isCollection = false,
            getetag = "new_etag",
            getcontentlength = "200",
            getcontenttype = "image/jpeg"
        )
        val settings =
            Settings(pathExclusions = emptySet(), conflictStrategy = ConflictStrategy.IGNORE)
        whenever(settingsDatastore.getSettings()).doReturn(settings)
        whenever(webDavService.getPropertiesFromParentCache(any())).doReturn(remoteResource)
        whenever(mediaStoreService.getById(1L)).doReturn(mediaStoreFile)
        whenever(fileDao.findByName(dbFile.pathname)).doReturn(dbFile)

        syncService.resyncFile(dbFile.pathname)

        verify(webDavService, never()).put(any(), any())
        verify(webDavService, never()).download(any(), any())
    }

    @Test
    fun `handleSyncResource should skip if directory is excluded`() = runTest {
        val settings = Settings(pathExclusions = setOf("ExcludedDir/"))
        whenever(settingsDatastore.getSettings()).doReturn(settings)
        val davFile = com.phpbg.easysync.dav.File("/ExcludedDir/file.jpg")

        syncService.handleSyncResource(davFile)

        verify(fileDao, never()).findByName(any())
    }
}
