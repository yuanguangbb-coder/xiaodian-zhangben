package com.example.ledger

import com.example.ledger.domain.backup.BackupCrypto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class BackupCryptoTest {

    private val password = "9fTlhWmIerAYd1BwLQCE".toCharArray()
    private val payload = ("账单备份内容".repeat(200) + "{\"bills\":[]}").toByteArray()

    @Test
    fun roundTrip_withStoredKey() {
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey(password, salt)
        val encrypted = BackupCrypto.encrypt(payload, key, salt)

        assertTrue(BackupCrypto.isEncrypted(encrypted))
        assertFalse(String(encrypted, 0, 12, Charsets.ISO_8859_1).contains("账单"))

        val decrypted = BackupCrypto.decrypt(encrypted, key)
        assertNotNull(decrypted)
        assertTrue(payload.contentEquals(decrypted!!))
    }

    @Test
    fun roundTrip_withPasswordOnly() {
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey(password, salt)
        val encrypted = BackupCrypto.encrypt(payload, key, salt)

        // 换手机场景：只用密码 + 文件里的 salt
        val decrypted = BackupCrypto.decrypt(encrypted, password)
        assertNotNull(decrypted)
        assertTrue(payload.contentEquals(decrypted!!))
    }

    @Test
    fun wrongPassword_fails() {
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey(password, salt)
        val encrypted = BackupCrypto.encrypt(payload, key, salt)

        assertNull(BackupCrypto.decrypt(encrypted, "wrong-password".toCharArray()))
    }

    @Test
    fun tamperedFile_fails() {
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey(password, salt)
        val encrypted = BackupCrypto.encrypt(payload, key, salt)

        val tampered = encrypted.copyOf()
        tampered[tampered.size - 1] = (tampered.last().toInt() xor 0xFF).toByte()
        assertNull(BackupCrypto.decrypt(tampered, key))
    }

    @Test
    fun saltIsEmbeddedAndStable() {
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey(password, salt)
        val encrypted = BackupCrypto.encrypt(payload, key, salt)

        val embedded = BackupCrypto.saltOf(encrypted)
        assertNotNull(embedded)
        assertTrue(salt.contentEquals(embedded!!))
    }

    @Test
    fun plainBytesAreNotMistakenForEncrypted() {
        assertFalse(BackupCrypto.isEncrypted(payload))
        assertNull(BackupCrypto.saltOf(payload))
    }

    @Test
    fun keyIsStableForSameSaltAndPassword() {
        val salt = BackupCrypto.newSalt()
        val k1 = BackupCrypto.deriveKey(password, salt)
        val k2 = BackupCrypto.deriveKey(password, salt)
        assertTrue(Base64.getEncoder().encodeToString(k1.encoded) == Base64.getEncoder().encodeToString(k2.encoded))
    }
}