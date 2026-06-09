package com.nam.novelreader.extension.runtime

import android.util.Base64
import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * VBookExtensionDecryptor — giải mã các file JavaScript của extension VBook.
 *
 * Tái tạo chính xác thuật toán từ VBook:
 * 1. MD5 hex string của "com.vbook.app" + source + author (dùng BigInteger.toString(16))
 * 2. SHA-256 của MD5 hex string trên để làm khóa AES-256
 * 3. Thay thế nhãn Base64: x0P1Xx -> +, x0P2Xx -> /, x0P3Xx -> =
 * 4. Giải mã AES/CBC/PKCS5Padding với IV 16-byte 0
 */
object VBookExtensionDecryptor {
    private val ZERO_IV = ByteArray(16)

    fun decrypt(encryptedJs: String, source: String, author: String): String? {
        try {
            // 1. Tạo key thô: com.vbook.app + source + author
            val rawKeySource = "com.vbook.app$source$author"
            
            // 2. Tính MD5 của key thô (dạng BigInteger hex string)
            val md5Hex = md5(rawKeySource)
            
            // 3. Khôi phục chuỗi Base64 gốc
            val cleanedBase64 = restoreBase64(encryptedJs)
            
            // 4. Giải mã Base64
            val encryptedBytes = Base64.decode(cleanedBase64, Base64.NO_WRAP)
            
            // 5. Tính SHA-256 của MD5 hex string để làm khóa AES-256
            val aesKeyBytes = sha256(md5Hex)
            
            // 6. Giải mã AES-256-CBC
            val decryptedBytes = decryptAes(aesKeyBytes, encryptedBytes)
            
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("ExtensionDecryptor", "Failed to decrypt extension JS: ${e.message}", e)
            return null
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun sha256(input: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray(Charsets.UTF_8))
    }

    private fun restoreBase64(input: String): String {
        return input
            .replace("x0P1Xx", "+")
            .replace("x0P2Xx", "/")
            .replace("x0P3Xx", "=")
    }

    private fun decryptAes(keyBytes: ByteArray, encryptedBytes: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val ivSpec = IvParameterSpec(ZERO_IV)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(encryptedBytes)
    }
}
