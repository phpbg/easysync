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

import org.junit.Test
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class FileTest {
    @Test
    fun file_collectionPath_notEmpty() {
        val cp = CollectionPath("/foo/bar")
        val file = File(cp, "foo.jpg")
        assert(file.getPath() == "/foo/bar/foo.jpg")
        assert(file.getPathNoLeading() == "foo/bar/foo.jpg")
    }

    @Test
    fun collectionPath_root() {
        val cp = CollectionPath("/")
        val file = File(cp, "foo.jpg")
        assert(file.getPath() == "/foo.jpg")
        assert(file.getPathNoLeading() == "foo.jpg")
    }

    @Test
    fun collectionPath_empty() {
        val cp = CollectionPath("")
        val file = File(cp, "foo.jpg")
        assert(file.getPath() == "/foo.jpg")
        assert(file.getPathNoLeading() == "foo.jpg")
    }

    @Test
    fun createFromResource() {
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
        val file = File(resource)
        assert(file.getPath() == "/DCIM/bar.jpg")
        assert(file.getPathNoLeading() == "DCIM/bar.jpg")
    }

    @Test
    fun createFromString_leading() {
        val file = File("/DCIM/bar.jpg")
        assert(file.getPath() == "/DCIM/bar.jpg")
        assert(file.getPathNoLeading() == "DCIM/bar.jpg")
    }

    @Test
    fun createFromString_noLeading() {
        val file = File("DCIM/bar.jpg")
        assert(file.getPath() == "/DCIM/bar.jpg")
        assert(file.getPathNoLeading() == "DCIM/bar.jpg")
    }
}