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

import java.net.URL

data class RootPath(private val url: String) {
    /**
     * URL that always stop with /
     */
    val canonicalUrl: String

    /**
     * path that always start and stop with /
     */
    val path: String

    init {
        if (!url.lowercase().startsWith("http")) {
            throw IllegalArgumentException("WebDAV URL must start with http(s)")
        }
        canonicalUrl = if (url.endsWith("/")) {
            url
        } else {
            "$url/"
        }
        val urlUrl = URL(canonicalUrl)
        if (urlUrl.query != null || urlUrl.ref != null){
            throw IllegalArgumentException("WebDAV URL malformed")
        }
        path = URL(canonicalUrl).path
    }

    fun concat(path: CollectionPath): RootPath {
        return this.copy(url = this.canonicalUrl + path.getPathNoLeading())
    }
}
