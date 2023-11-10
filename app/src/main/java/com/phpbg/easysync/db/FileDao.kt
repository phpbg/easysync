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

package com.phpbg.easysync.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface FileDao {
    @Query("SELECT id FROM file")
    suspend fun getAllids(): List<Long>

    @Query("SELECT pathname FROM file")
    suspend fun getAllPathnames(): List<String>

    @Query("SELECT pathname FROM file WHERE pathname LIKE :pathStartLeadingAndTrailing || '%' AND pathname NOT LIKE :pathStartLeadingAndTrailing || '%/%'")
    suspend fun getDirectChildrenStartingWithPathname(pathStartLeadingAndTrailing: String): List<String>

    @Query("SELECT COUNT(pathname) FROM file")
    fun count(): LiveData<Int>

    @Query("SELECT * FROM file WHERE pathname=:pathname")
    suspend fun findByName(pathname: String): File?

    @Query("SELECT * FROM file WHERE id=:id")
    suspend fun findById(id: Long): File?

    @Insert
    suspend fun insertAll(vararg files: File)

    @Update
    suspend fun updateFile(file: File)

    @Delete
    suspend fun delete(user: File)
}
