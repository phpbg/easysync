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

package com.phpbg.easysync.dav

import android.util.Log
import com.burgstaller.okhttp.AuthenticationCacheInterceptor
import com.burgstaller.okhttp.CachingAuthenticatorDecorator
import com.burgstaller.okhttp.DefaultRequestCacheKeyProvider
import com.burgstaller.okhttp.DispatchingAuthenticator
import com.burgstaller.okhttp.basic.BasicAuthenticator
import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.net.UrlEscapers
import com.phpbg.easysync.BuildConfig
import com.phpbg.easysync.settings.Settings
import com.phpbg.easysync.util.ParametrizedMutex
import com.phpbg.easysync.util.TTLHashSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import ru.gildor.coroutines.okhttp.await
import java.io.File
import java.io.Reader
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
import java.time.format.DateTimeParseException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val TAG = "WebDavService"

class WebDavService(
    private var rootUrl: RootPath,
    private var httpClient: OkHttpClient,
) {
    // Mutex to avoid concurrent creation of same directories
    private val mkcolPMutex = ParametrizedMutex<String>()

    // Non concurrent safe cache to reduce dav load, save exists() requests for 30s
    private val existsCache = TTLHashSet<String>(30000)

    private val listPMutex = ParametrizedMutex<String>()
    private val listCache: Cache<String, List<Resource>> = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(30, TimeUnit.SECONDS)
        .build()

    fun updateFromSettings(settings: Settings) {
        rootUrl = RootPath(settings.url).concat(CollectionPath(settings.davPath))
        httpClient = createHttpClient(settings)
    }

    //https://learn.microsoft.com/en-us/previous-versions/office/developer/exchange-server-2003/aa142923(v=exchg.65)
    //https://datatracker.ietf.org/doc/html/rfc4918
    //https://www.qed42.com/insights/coe/drupal/using-curl-commands-webdav
    private suspend fun propfind(path: String, depth: Int = 1): ArrayList<Resource> {
        val request = Request.Builder()
            .url(path)
            .method("PROPFIND", null)
            .header("Depth", depth.toString())
            .build()

        val response = httpClient.newCall(request).await()
        if (!response.isSuccessful) {
            if (response.code == 404) throw NotFoundExeption(path)
            if (response.code == 401) throw UnauthorizedExeption(response.message)
            throw IOException("Unexpected code $response", response)
        }
        val body = response.body!!.charStream()
        return parsePropfind(body, rootUrl)
    }

    suspend fun list(path: CollectionPath): List<Resource> {
        val fullPath = rootUrl.canonicalUrl + percentEncodePath(path.getPathNoLeading())
        return propfind(fullPath, 1)
    }

    suspend fun getChildren(path: CollectionPath): List<Resource> {
        return list(path).filter { it.relativeHref.getPath() != path.getPath() }
    }

    suspend fun listCached(path: CollectionPath): List<Resource> {
        return listPMutex.withLock(path.getPath()) {
            var res = listCache.getIfPresent(path.getPath())
            if (res == null) {
                res = list(path)
                listCache.put(path.getPath(), res)
            }
            return@withLock res
        }
    }

    suspend fun getProperties(path: com.phpbg.easysync.dav.Path): Resource {
        val fullPath = rootUrl.canonicalUrl + percentEncodePath(path.getPathNoLeading())
        return propfind(fullPath, 0).first()
    }

    suspend fun getPropertiesFromParentCache(path: com.phpbg.easysync.dav.Path): Resource {
        val parentPath = path.getParent()
        val resources = listCached(parentPath)
        val resourcePath = path.getPath()
        return resources.find { it.relativeHref.getPath() == resourcePath } ?: getProperties(path)
    }

    /**
     * Test if a collection or a file exists
     */
    private suspend fun exists(relativeDest: com.phpbg.easysync.dav.Path): Boolean {
        val pathNoLeading = relativeDest.getPathNoLeading()
        return try {
            val path = rootUrl.canonicalUrl + percentEncodePath(pathNoLeading)
            propfind(path, 0)
            true
        } catch (err: NotFoundExeption) {
            false
        }
    }

    suspend fun deleteCollectionIfEmpty(path: CollectionPath) {
        val resources = list(path)
        if (resources.size > 1) {
            throw NotEmptyException("Cannot delete ${path.getPath()}: not empty")
        }
        if (resources.none { it.relativeHref.getPath() != path.getPath() }) {
            delete(path)
        } else {
            throw NotEmptyException("Cannot delete ${path.getPath()}: not empty")
        }
    }

    suspend fun delete(fileOrCollection: com.phpbg.easysync.dav.Path) {
        val path = rootUrl.canonicalUrl + percentEncodePath(fileOrCollection.getPathNoLeading())
        val request = Request.Builder()
            .url(path)
            .method("DELETE", null)
            .build()

        mkcolPMutex.withLock(fileOrCollection.getPathNoLeading()) {
            existsCache.remove(fileOrCollection.getPathNoLeading())
            val response = httpClient.newCall(request).await()
            if (!response.isSuccessful) {
                if (response.code == 404) throw NotFoundExeption(path)
                throw IOException("Unexpected code $response", response)
            }
        }
    }

    /**
     * Create a collection at an **existing** destination (no recursive creation)
     */
    private suspend fun mkcol(collectionPath: CollectionPath) {
        val request = Request.Builder()
            .url(rootUrl.canonicalUrl + percentEncodePath(collectionPath.getPathNoLeading()))
            .method("MKCOL", null)
            .build()

        val response = httpClient.newCall(request).await()
        if (!response.isSuccessful) throw IOException("Unexpected code $response", response)
    }

    /**
     * Create a collection (recursively if needed)
     */
    suspend fun mkcolRecursive(collectionPath: CollectionPath) {
        var currentPath = ""
        collectionPath.getPathNoLeading().split("/").forEach {
            currentPath = "$currentPath/$it"
            val path = CollectionPath(currentPath)
            mkcolPMutex.withLock(path.getPathNoLeading()) {
                if (existsCache.contains(path.getPathNoLeading())) {
                    return@withLock
                }
                if (exists(path)) {
                    existsCache.add(path.getPathNoLeading())
                } else {
                    mkcol(path)
                    existsCache.add(path.getPathNoLeading())
                }
            }
        }
    }

    suspend fun move(
        src: com.phpbg.easysync.dav.Path,
        dst: com.phpbg.easysync.dav.Path
    ) {
        val request = Request.Builder()
            .url(rootUrl.canonicalUrl + percentEncodePath(src.getPathNoLeading()))
            .method("MOVE", null)
            .addHeader("Overwrite", "F") // fail on overwrite
            .addHeader(
                "Destination",
                rootUrl.canonicalUrl + percentEncodePath(dst.getPathNoLeading())
            )
            .build()

        val response = httpClient.newCall(request).await()
        if (!response.isSuccessful) throw IOException("Unexpected code $response", response)
    }

    /**
     * Upload a file in an **existing** collection
     * @return Etag if any
     */
    suspend fun put(localFilePath: String, file: com.phpbg.easysync.dav.File): String? {
        val filePath = Paths.get(localFilePath)
        val fileAttrs = withContext(Dispatchers.IO) {
            try {
                return@withContext Files.readAttributes(
                    filePath,
                    "creationTime,lastModifiedTime"
                ).mapValues { (it.value as FileTime).to(TimeUnit.SECONDS).toString() }
            } catch (e: Exception) {
                Log.w(TAG, e)
                return@withContext null
            }
        }
        val localFile = File(localFilePath)
        val requestBuilder = Request.Builder()
            .url(rootUrl.canonicalUrl + percentEncodePath(file.getPathNoLeading()))
            .put(localFile.asRequestBody())

        if (fileAttrs != null) {
            // Set creation and modification date, own|nextcloud specific header
            // https://docs.nextcloud.com/server/latest/developer_manual/client_apis/WebDAV/basic.html#special-headers
            requestBuilder
                .header("X-OC-MTime", fileAttrs["lastModifiedTime"]!!)
                .header("X-OC-CTime", fileAttrs["creationTime"]!!)
        }

        val request = requestBuilder.build()
        val response = httpClient.newCall(request).await()
        if (!response.isSuccessful) throw IOException("Unexpected code $response", response)
        return response.headers["etag"]
    }

    private fun createIntermediateDirectories(filePath: String) {
        val parentDirectoryPath: Path = Paths.get(filePath).parent
        @Suppress("SENSELESS_COMPARISON")
        if (parentDirectoryPath != null && !Files.exists(parentDirectoryPath)) {
            Files.createDirectories(parentDirectoryPath)
        }
    }

    suspend fun download(remoteFile: com.phpbg.easysync.dav.File, localFilePath: String) {
        createIntermediateDirectories(localFilePath)
        val request = Request.Builder()
            .url(rootUrl.canonicalUrl + percentEncodePath(remoteFile.getPathNoLeading()))
            .get()
            .build()

        val response = httpClient.newCall(request).await()
        if (!response.isSuccessful) throw IOException("Unexpected code $response", response)
        val responseBody =
            response.body ?: throw IllegalStateException("Response doesn't contain a file")
        responseBody.byteStream().use { input ->
            Files.copy(input, Paths.get(localFilePath), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        @Volatile
        private var instance: WebDavService? = null
        private val mutex = Mutex()

        fun percentEncodePath(path: String): String {
            val splitted = path.split("/")
            return splitted.joinToString("/") { UrlEscapers.urlPathSegmentEscaper().escape(it) }
        }

        fun parseIsoOr1123DateTime(dateTimeString: String?): ZonedDateTime {
            return try {
                ZonedDateTime.parse(
                    dateTimeString,
                    ISO_DATE_TIME
                )
            } catch (e: DateTimeParseException) {
                ZonedDateTime.parse(
                    dateTimeString,
                    RFC_1123_DATE_TIME
                )
            }
        }

        fun parsePropfind(reader: Reader, rootPath: RootPath): ArrayList<Resource> {
            val parserFactory = XmlPullParserFactory.newInstance()
            parserFactory.isNamespaceAware = true
            val parser = parserFactory.newPullParser()
            parser.setInput(reader)

            var text: String? = ""
            val resourceList = ArrayList<Resource>()
            var resource = Resource(rootPath = rootPath)
            var resourceTypeTag = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name
                when (event) {
                    XmlPullParser.START_TAG -> {
                        if (tag == "response") resource = Resource(rootPath = rootPath)
                        if (tag == "resourcetype") resourceTypeTag = true
                        if (parser.isEmptyElementTag) {
                            text = null
                        }
                    }

                    XmlPullParser.TEXT -> {
                        text = if (parser.text.isNullOrEmpty()) null else parser.text
                    }

                    XmlPullParser.END_TAG -> when (tag) {
                        "href" -> resource = resource.copy(href = URLDecoder.decode(text, "UTF-8"))
                        "creationdate" -> {
                            resource = resource.copy(
                                // should be encoded in rfc3339 according to webdav spec
                                // but mailbox.org uses RFC 1123, so support both
                                creationdate = parseIsoOr1123DateTime(text).toInstant()
                            )
                        }

                        "getlastmodified" -> resource = resource.copy(
                            getlastmodified = ZonedDateTime.parse(
                                text,
                                RFC_1123_DATE_TIME
                            ).toInstant()
                        )

                        "resourcetype" -> resourceTypeTag = false
                        "collection" -> if (resourceTypeTag) resource =
                            resource.copy(isCollection = true)

                        "getetag" -> resource = resource.copy(getetag = text)
                        "getcontentlength" -> resource = resource.copy(getcontentlength = text)
                        "getcontenttype" -> resource = resource.copy(getcontenttype = text)
                        "response" -> resourceList.add(resource)
                    }
                }
                event = parser.next()
            }
            return resourceList
        }

        suspend fun getInstance(settingsFlow: Flow<Settings>): WebDavService {
            if (instance == null) {
                val settings = settingsFlow.first()
                mutex.withLock {
                    if (instance == null) {
                        instance = create(settings)
                        settingsFlow
                            .onEach { instance!!.updateFromSettings(it) }
                            .launchIn(CoroutineScope(Dispatchers.Default))
                    }
                }
            }
            return instance!!
        }

        private fun createHttpClient(settings: Settings): OkHttpClient {
            val authCache: Map<String, CachingAuthenticator> = ConcurrentHashMap()
            val credentials = Credentials(settings.username, settings.password)
            val basicAuthenticator = BasicAuthenticator(credentials)
            val digestAuthenticator = DigestAuthenticator(credentials)

            // note that all auth schemes should be registered as lowercase!
            val authenticator = DispatchingAuthenticator.Builder()
                .with("digest", digestAuthenticator)
                .with("basic", basicAuthenticator)
                .build()

            val dispatcher = Dispatcher()
            dispatcher.maxRequestsPerHost = 6
            val okHttpClientBuilder = OkHttpClient.Builder()
                .authenticator(CachingAuthenticatorDecorator(authenticator, authCache))
                .addInterceptor(AuthenticationCacheInterceptor(authCache, DefaultRequestCacheKeyProvider()))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(3600, TimeUnit.SECONDS)
                .dispatcher(dispatcher)
                .addInterceptor(TrafficStatsInterceptor())
                .retryOnConnectionFailure(false)

            if (BuildConfig.DEBUG) {
                // Warning: logging at Level.BODY seems to load whole bodies in memory and may lead to out of memory
                val loggingInterceptor =
                    HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC)
                okHttpClientBuilder.addInterceptor(loggingInterceptor)
            }

            return okHttpClientBuilder.build()
        }

        fun create(settings: Settings): WebDavService {
            Log.d(TAG, "Creating DAVService with URL: " + settings.url)
            if (settings.url.isEmpty()) {
                throw MisconfigurationException()
            }

            val okHttpClient = createHttpClient(settings)

            return WebDavService(
                RootPath(settings.url).concat(CollectionPath(settings.davPath)),
                okHttpClient
            )
        }
    }
}