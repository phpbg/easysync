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

import org.junit.Assert
import org.junit.Test
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class ResourceTest {
    @Test
    fun computedProperties_file_work() {
        val rootPath = RootPath("https://foo/remote.php/dav/files/foouser")
        val resource = Resource(
            rootPath = rootPath,
            href = "/remote.php/dav/files/foouser/DCIM/bar.jpg",
            creationdate = null,
            getlastmodified = ZonedDateTime.parse("Sat, 10 Jun 2023 21:06:18 GMT", DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(),
            isCollection = false,
            getetag = "\"bacfa6f123dee073dc9e20774470681a\"",
            getcontentlength = "425623",
            getcontenttype = "image/jpeg"
        )
        Assert.assertEquals("/DCIM/bar.jpg", resource.relativeHref.getPath())
        Assert.assertTrue(resource.relativeHref is File)
    }

    @Test
    fun computedProperties_collection_work() {
        val rootPath = RootPath("https://foo/remote.php/dav/files/foouser")
        val resource = Resource(
            rootPath = rootPath,
            href = "/remote.php/dav/files/foouser/DCIM/",
            creationdate = null,
            getlastmodified = ZonedDateTime.parse("Sat, 10 Jun 2023 21:06:18 GMT", DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(),
            isCollection = true,
            getetag = "\"bacfa6f123dee073dc9e20774470681a\"",
            getcontentlength = "425623",
            getcontenttype = "image/jpeg"
        )
        Assert.assertEquals("/DCIM/", resource.relativeHref.getPath())
        Assert.assertTrue(resource.relativeHref is CollectionPath)
    }

    @Test
    fun computedProperties_root_work() {
        val rootPath = RootPath("https://foo/remote.php/dav/files/foouser")
        val resource = Resource(
            rootPath = rootPath,
            href = "/remote.php/dav/files/foouser/",
            creationdate = null,
            getlastmodified = ZonedDateTime.parse("Sat, 10 Jun 2023 21:06:18 GMT", DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(),
            isCollection = true,
            getetag = "\"bacfa6f123dee073dc9e20774470681a\"",
            getcontentlength = "425623",
            getcontenttype = "image/jpeg"
        )
        Assert.assertEquals("/", resource.relativeHref.getPath())
        Assert.assertTrue(resource.relativeHref is CollectionPath)
    }
}