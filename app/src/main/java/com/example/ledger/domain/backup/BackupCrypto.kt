package com.example.ledger.domain.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份加密：AES-256-GCM + PBKDF2-HMAC-SHA256 派生密钥。
 *
 * 文件格式（.yjb）：
 *   magic "YJB1"(4) | salt(16) | iv(12) | ciphertext+tag(...)
 *
 * salt 每次安装随机生成一次，写进每个文件；换手机时用密码 + 文件里的 salt 重新派生密钥即可解密。
 */
object BackupCrypto {

    private const val MAGIC = "YJB1"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val raw = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(raw, "AES")
    }

    fun encrypt(plain: ByteArray, key: SecretKey, salt: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val cipherText = cipher.doFinal(plain)
        return MAGIC.toByteArray(Charsets.US_ASCII) + salt + iv + cipherText
    }

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.length + SALT_BYTES + IV_BYTES &&
            String(bytes, 0, MAGIC.length, Charsets.US_ASCII) == MAGIC

    fun saltOf(bytes: ByteArray): ByteArray? {
        if (!isEncrypted(bytes)) return null
        return bytes.copyOfRange(MAGIC.length, MAGIC.length + SALT_BYTES)
    }

    /** 用已保存的密钥解密（同一台手机） */
    fun decrypt(bytes: ByteArray, key: SecretKey): ByteArray? = runCatching {
        val salt = saltOf(bytes) ?: return null
        val ivStart = MAGIC.length + SALT_BYTES
        val iv = bytes.copyOfRange(ivStart, ivStart + IV_BYTES)
        val cipherText = bytes.copyOfRange(ivStart + IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.doFinal(cipherText)
    }.getOrNull()

    /** 换手机时用密码 + 文件里的 salt 解密 */
    fun decrypt(bytes: ByteArray, password: CharArray): ByteArray? {
        val salt = saltOf(bytes) ?: return null
        return runCatching { decrypt(bytes, deriveKey(password, salt)) }.getOrNull()
    }
}