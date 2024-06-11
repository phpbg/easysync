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


class ParseTest {
    @Test
    fun propfind_collection_works() {
        val stream = this.javaClass.classLoader.getResourceAsStream("propfind.xml")
        val rootPath = RootPath("https://foo")
        val res = WebDavService.parsePropfind(stream.reader(), rootPath)
        assert(res.size == 8)
    }

    @Test
    fun propfind_mailbox_org_works() {
        val stream = this.javaClass.classLoader.getResourceAsStream("propfind_mailbox_org.xml")
        val rootPath = RootPath("https://foo")
        val res = WebDavService.parsePropfind(stream.reader(), rootPath)
        assert(res.size == 4)
        val expected = Resource(
            rootPath = rootPath,
            href = "/servlet/webdav.infostore/",
            creationdate = ZonedDateTime.parse("Tue, 11 Jun 2024 20:35:13 GMT", DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(),
            getlastmodified = ZonedDateTime.parse("Tue, 11 Jun 2024 20:35:13 GMT", DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(),
            isCollection = true,
            getetag = null,
            getcontentlength = null,
            getcontenttype = "httpd/unix-directory"
        )
        assert(expected == res.first())
    }

    @Test
    fun propfind_file_works() {
        val stream = this.javaClass.classLoader.getResourceAsStream("file.xml")
        val rootPath = RootPath("https://foo")
        val res = WebDavService.parsePropfind(stream.reader(), rootPath)
        val expected = Resource(
            rootPath = rootPath,
            href = "/remote.php/dav/files/foouser/DCIM/bar.jpg",
            creationdate = null,
            getlastmodified = ZonedDateTime.parse("Sat, 10 Jun 2023 21:06:18 GMT", DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(),
            isCollection = false,
            getetag = "\"bacfa6f123dee073dc9e20774470681a\"",
            getcontentlength = "425623",
            getcontenttype = "image/jpeg"
        )
        assert(expected == res.first())
    }

    @Test
    fun propfind_apache_file_works() {
        val stream = this.javaClass.classLoader.getResourceAsStream("file_apache.xml")
        val rootPath = RootPath("https://foo")
        val res = WebDavService.parsePropfind(stream.reader(), rootPath)
        val expected = Resource(
            rootPath = rootPath,
            href = "/remote.php/dav/files/foouser/DCIM/bar.jpg",
            creationdate = ZonedDateTime.parse("2024-01-08T19:14:11Z", DateTimeFormatter.ISO_DATE_TIME).toInstant(),
            getlastmodified = ZonedDateTime.parse("Mon, 08 Jan 2024 19:14:11 GMT", DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(),
            isCollection = false,
            getetag = "\"bacfa6f123dee073dc9e20774470681a\"",
            getcontentlength = "425623",
            getcontenttype = "image/jpeg"
        )
        assert(expected == res.first())
    }

    @Test
    fun propfind_uri_decode_works() {
        val stream = this.javaClass.classLoader.getResourceAsStream("file_uri_encoded.xml")
        val rootPath = RootPath("https://foo")
        val res = WebDavService.parsePropfind(stream.reader(), rootPath)
        val expected = Resource(
            rootPath = rootPath,
            href = "/remote.php/dav/files/foouser/Foo Bar/2023-08-01.jpg",
            creationdate = null,
            getlastmodified = ZonedDateTime.parse("Sat, 10 Jun 2023 21:06:18 GMT", DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(),
            isCollection = false,
            getetag = "\"bacfa6f123dee073dc9e20774470681a\"",
            getcontentlength = "425623",
            getcontenttype = "image/jpeg"
        )
        assert(expected == res.first())
    }

    @Test
    fun propfind_uri_decode2_works() {
        val stream = this.javaClass.classLoader.getResourceAsStream("file_uri_encoded2.xml")
        val rootPath = RootPath("https://foo")
        val res = WebDavService.parsePropfind(stream.reader(), rootPath)
        val expected = Resource(
            rootPath = rootPath,
            href = "/remote.php/dav/files/foouser/#1234 (1).pdf",
            creationdate = null,
            getlastmodified = ZonedDateTime.parse("Sat, 10 Jun 2023 21:06:18 GMT", DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(),
            isCollection = false,
            getetag = "\"bacfa6f123dee073dc9e20774470681a\"",
            getcontentlength = "425623",
            getcontenttype = "image/jpeg"
        )
        assert(expected == res.first())
    }

    @Test
    fun propfind_directory_works() {
        val stream = this.javaClass.classLoader.getResourceAsStream("directory.xml")
        val rootPath = RootPath("https://foo")
        val res = WebDavService.parsePropfind(stream.reader(), rootPath)
        val expected = Resource(
            rootPath = rootPath,
            href = "/remote.php/dav/files/foouser/Documents/",
            creationdate = null,
            getlastmodified = ZonedDateTime.parse("Thu, 01 Jun 2023 08:16:46 GMT", DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(),
            isCollection = true,
            getetag = "\"647853ef0e5ec\"",
        )
        assert(expected == res.first())
    }
}