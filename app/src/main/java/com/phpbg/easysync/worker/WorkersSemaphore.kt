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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.sync.Semaphore

/**
 * Workmanager may spawn too many CoroutineWorkers at a time
 * We do not wan this because it may overload DAV Server, and too many workers will progress at a too low pace
 * This semaphore that avoids running all the workers at once (they will be launched because we cannot control this, but remain suspended)
 */
object WorkersSemaphore : Semaphore {
    private val semaphore = Semaphore(6) // keep value in sync with okhttp max request per host
    override val availablePermits: Int
        get() = semaphore.availablePermits

    override suspend fun acquire() {
        semaphore.acquire()
    }

    override fun release() {
        semaphore.release()
    }

    override fun tryAcquire(): Boolean {
        return semaphore.tryAcquire()
    }

    /**
     * Test for network connectivity
     * As we use a semaphore to delay workers, we may loose connectivity before effectively running the job
     * Eg. the worker is started but the phone goes to sleep and loose connectivity while waiting for the semaphore
     * Test for network connectivity and properly cancel the job if connectivity is lost
     */
    fun isConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw      = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(nw) ?: return false
        return actNw.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}