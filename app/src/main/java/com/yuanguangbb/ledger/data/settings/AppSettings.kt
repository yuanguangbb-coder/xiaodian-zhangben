package com.yuanguangbb.ledger.data.settings

import android.content.Context
import android.util.Base64
import com.yuanguangbb.ledger.domain.backup.BackupCrypto
import com.yuanguangbb.ledger.domain.lock.LockGuard
import com.yuanguangbb.ledger.domain.lock.LockPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** 税务规则，可在设置页里改。金额单位：分。 */
data class TaxSettings(
    /** 免税额度（月）：这部分收入不算税 */
    val thresholdCents: Long = 0L,
    /** 税率，万分比。1% = 100 */
    val rateBasisPoints: Int = 100,
    /** 季度增值税起征点 */
    val quarterLimitCents: Long = 30_000_000L,
)

/**
 * 本地设置：税务规则、应用锁、自动备份。
 * 用 SharedPreferences 存，密码只存加盐 SHA-256，不存明文。
 */
class AppSettings(context: Context) : LockGuard {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _tax = MutableStateFlow(loadTax())
    val tax: StateFlow<TaxSettings> = _tax.asStateFlow()

    private val _autoBackup = MutableStateFlow(prefs.getBoolean(KEY_AUTO_BACKUP, true))
    val autoBackup: StateFlow<Boolean> = _autoBackup.asStateFlow()

    fun setTax(settings: TaxSettings) {
        prefs.edit()
            .putLong(KEY_TAX_THRESHOLD, settings.thresholdCents)
            .putInt(KEY_TAX_RATE, settings.rateBasisPoints)
            .putLong(KEY_TAX_QUARTER_LIMIT, settings.quarterLimitCents)
            .apply()
        _tax.value = settings
    }

    fun setAutoBackup(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply()
        _autoBackup.value = enabled
    }

    private fun loadTax(): TaxSettings = TaxSettings(
        thresholdCents = prefs.getLong(KEY_TAX_THRESHOLD, 0L),
        rateBasisPoints = prefs.getInt(KEY_TAX_RATE, 100),
        quarterLimitCents = prefs.getLong(KEY_TAX_QUARTER_LIMIT, 30_000_000L),
    )

    // ---------- 应用锁 ----------

    val isPinSet: Boolean get() = prefs.getString(KEY_PIN_HASH, null) != null

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, hash(pin, salt))
            .apply()
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_SALT).remove(KEY_PIN_HASH).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null)?.let {
            runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull()
        } ?: return false
        val saved = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return saved == hash(pin, salt)
    }

    // ---------- 备份加密（AES-256-GCM） ----------

    val hasBackupPassword: Boolean
        get() = prefs.getString(KEY_BACKUP_KEY, null) != null

    /** 只保存派生出的密钥和 salt，不保存密码本身 */
    fun setBackupPassword(password: CharArray) {
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey(password, salt)
        prefs.edit()
            .putString(KEY_BACKUP_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_BACKUP_KEY, Base64.encodeToString(key.encoded, Base64.NO_WRAP))
            .apply()
    }

    fun clearBackupPassword() {
        prefs.edit().remove(KEY_BACKUP_SALT).remove(KEY_BACKUP_KEY).apply()
    }

    fun backupSalt(): ByteArray? = prefs.getString(KEY_BACKUP_SALT, null)?.let {
        runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull()
    }

    fun backupKey(): SecretKey? = prefs.getString(KEY_BACKUP_KEY, null)?.let {
        runCatching { SecretKeySpec(Base64.decode(it, Base64.NO_WRAP), "AES") }.getOrNull()
    }

    // ---------- 失败次数（落盘，杀进程也绕不过） ----------

    override val failedAttempts: Int
        get() = prefs.getInt(KEY_LOCK_FAILURES, 0)

    override val cooldownUntilMillis: Long
        get() = prefs.getLong(KEY_LOCK_COOLDOWN, 0L)

    override fun recordFailure(nowMillis: Long): Boolean {
        val next = failedAttempts + 1
        return if (next >= LockPolicy.MAX_ATTEMPTS) {
            prefs.edit()
                .putInt(KEY_LOCK_FAILURES, 0)
                .putLong(KEY_LOCK_COOLDOWN, nowMillis + LockPolicy.COOLDOWN_SECONDS * 1000L)
                .apply()
            true
        } else {
            prefs.edit().putInt(KEY_LOCK_FAILURES, next).apply()
            false
        }
    }

    override fun clearFailures() {
        prefs.edit()
            .putInt(KEY_LOCK_FAILURES, 0)
            .putLong(KEY_LOCK_COOLDOWN, 0L)
            .apply()
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(pin.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // ---------- 备份时间 ----------

    var lastBackupAtMillis: Long
        get() = prefs.getLong(KEY_LAST_BACKUP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKUP, value).apply()

    /** 最近一次备份存到哪（给设置页显示） */
    var lastBackupPath: String?
        get() = prefs.getString(KEY_LAST_BACKUP_PATH, null)
        set(value) = prefs.edit().putString(KEY_LAST_BACKUP_PATH, value).apply()

    private companion object {
        const val PREFS = "ledger_settings"
        const val KEY_TAX_THRESHOLD = "tax_threshold_cents"
        const val KEY_TAX_RATE = "tax_rate_bp"
        const val KEY_TAX_QUARTER_LIMIT = "tax_quarter_limit_cents"
        const val KEY_AUTO_BACKUP = "auto_backup"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_LAST_BACKUP = "last_backup_at"
        const val KEY_LAST_BACKUP_PATH = "last_backup_path"
        const val KEY_LOCK_FAILURES = "lock_failures"
        const val KEY_LOCK_COOLDOWN = "lock_cooldown_until"
        const val KEY_BACKUP_SALT = "backup_salt"
        const val KEY_BACKUP_KEY = "backup_key"
    }
}