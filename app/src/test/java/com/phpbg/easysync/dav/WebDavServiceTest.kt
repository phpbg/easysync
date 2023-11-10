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
import org.junit.Assert

class WebDavServiceTest {
    @Test
    fun percentEncodePath_simple() {
        Assert.assertEquals("DCIM/bar.jpg", WebDavService.percentEncodePath("DCIM/bar.jpg"))
    }

    @Test
    fun percentEncodePath_encoded() {
        Assert.assertEquals("DCIM/%231234.jpg", WebDavService.percentEncodePath("DCIM/#1234.jpg"))
    }

    @Test
    fun percentEncodePath_spaces() {
        Assert.assertEquals("DCIM/foo%20bar%20baz.jpg", WebDavService.percentEncodePath("DCIM/foo bar baz.jpg"))
    }

    @Test
    fun percentEncodePath_short() {
        Assert.assertEquals("%231234.jpg", WebDavService.percentEncodePath("#1234.jpg"))
    }

    @Test
    fun percentEncodePath_full() {
        Assert.assertEquals("/foo/bar%20baz/%231234.jpg", WebDavService.percentEncodePath("/foo/bar baz/#1234.jpg"))
    }
}