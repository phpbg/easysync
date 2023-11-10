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

package com.phpbg.easysync.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

/**
 * Copied from https://github.com/philipplackner/AndroidCrypto/issues/2
 */
class CryptoManager {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private val encryptCipher
        get() = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getKey())
        }

    private fun getDecryptCipherForIv(iv: ByteArray): Cipher {
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getKey(), IvParameterSpec(iv))
        }
    }

    private fun getKey(): SecretKey {
        val existingKey = keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: createKey()
    }

    private fun createKey(): SecretKey {
        return KeyGenerator.getInstance(ALGORITHM).apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(KEY_SIZE * 8) // key size in bits
                    .setBlockModes(BLOCK_MODE)
                    .setEncryptionPaddings(PADDING)
                    .setUserAuthenticationRequired(false)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }

    fun encrypt(bytes: ByteArray, outputStream: OutputStream) {
        val cipher = encryptCipher
        val iv = cipher.iv
        outputStream.use { ous ->
            ous.write(iv)
            // write the payload in chunks to make sure to support larger data amounts (this would otherwise fail silently and result in corrupted data being read back)
            ByteArrayInputStream(bytes).use { inputStream ->
                val buffer = ByteArray(CHUNK_SIZE)
                while (inputStream.available() > CHUNK_SIZE) {
                    inputStream.read(buffer)
                    val ciphertextChunk = cipher.update(buffer)
                    ous.write(ciphertextChunk)
                }
                // the last chunk must be written using doFinal() because this takes the padding into account
                val remainingBytes = inputStream.readBytes()
                val lastChunk = cipher.doFinal(remainingBytes)
                ous.write(lastChunk)
            }
        }
    }

    fun decrypt(inputStream: InputStream): ByteArray {
        return inputStream.use { ins ->
            val iv = ByteArray(KEY_SIZE)
            ins.read(iv)
            val cipher = getDecryptCipherForIv(iv)

            ByteArrayOutputStream().use { outputStream ->
                // read the payload in chunks to make sure to support larger data amounts (this would otherwise fail silently and result in corrupted data being read back)
                val buffer = ByteArray(CHUNK_SIZE)
                while (ins.available() > CHUNK_SIZE) {
                    ins.read(buffer)
                    val ciphertextChunk = cipher.update(buffer)
                    outputStream.write(ciphertextChunk)
                }
                // the last chunk must be read using doFinal() because this takes the padding into account
                val remainingBytes = ins.readBytes()
                val lastChunk = cipher.doFinal(remainingBytes)
                outputStream.write(lastChunk)
                outputStream.toByteArray()
            }
        }
    }

    companion object {
        private const val CHUNK_SIZE = 1024 * 4 // bytes
        private const val KEY_SIZE = 16 // bytes
        private const val ALIAS = "my_alias"
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7
        private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
    }

}