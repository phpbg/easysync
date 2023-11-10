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

package com.phpbg.easysync.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ParametrizedMutexTest {
    @Test
    fun lock_on_same_str_works() {
        val pmutex = ParametrizedMutex<String>()
        val test = AtomicInteger()
        runBlocking {
            launch {
                pmutex.withLock("foo") {
                    val value = test.get()
                    println("Start $value")
                    test.incrementAndGet()
                    delay(100)
                    println("Stop ${test.get()}")
                    Assert.assertEquals(value+1, test.get())
                }
            }
            launch {
                pmutex.withLock("foo") {
                    val value = test.get()
                    println("Start $value")
                    test.incrementAndGet()
                    delay(100)
                    println("Stop ${test.get()}")
                    Assert.assertEquals(value+1, test.get())
                }
            }
            launch {
                pmutex.withLock("foo") {
                    val value = test.get()
                    println("Start $value")
                    test.incrementAndGet()
                    delay(100)
                    println("Stop ${test.get()}")
                    Assert.assertEquals(value+1, test.get())
                }
            }
        }
        Assert.assertEquals(3, test.get())
    }

    @Test
    fun lock_on_diff_str_works() {
        val pmutex = ParametrizedMutex<String>()
        val test = AtomicInteger()
        runBlocking {
            launch {
                pmutex.withLock("foo") {
                    println("Start ${test.get()}")
                    test.incrementAndGet()
                    delay(100)
                    println("Stop ${test.get()}")
                    Assert.assertEquals(3, test.get())
                }
            }
            launch {
                pmutex.withLock("bar") {
                    println("Start ${test.get()}")
                    test.incrementAndGet()
                    delay(100)
                    println("Stop ${test.get()}")
                    Assert.assertEquals(3, test.get())
                }
            }
            launch {
                pmutex.withLock("baz") {
                    println("Start ${test.get()}")
                    test.incrementAndGet()
                    delay(100)
                    println("Stop ${test.get()}")
                    Assert.assertEquals(3, test.get())
                }
            }
        }
        Assert.assertEquals(3, test.get())
    }
}