package com.yuanguangbb.ledger.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuanguangbb.ledger.data.backup.BackupManager
import com.yuanguangbb.ledger.data.db.AccountEntity
import com.yuanguangbb.ledger.data.db.CategoryEntity
import com.yuanguangbb.ledger.data.db.TransactionWithNames
import com.yuanguangbb.ledger.data.repository.LedgerRepository
import com.yuanguangbb.ledger.data.settings.AppSettings
import com.yuanguangbb.ledger.data.settings.TaxSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: LedgerRepository,
    private val settings: AppSettings,
    private val backup: BackupManager,
) : ViewModel() {

    val allCategories: StateFlow<List<CategoryEntity>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> = repository.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val deletedTransactions: StateFlow<List<TransactionWithNames>> = repository.deletedTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tax: StateFlow<TaxSettings> = settings.tax
    val autoBackup: StateFlow<Boolean> = settings.autoBackup

    private val _pinSet = MutableStateFlow(settings.isPinSet)
    val pinSet: StateFlow<Boolean> = _pinSet.asStateFlow()

    private val _lastBackupAt = MutableStateFlow(settings.lastBackupAtMillis)
    val lastBackupAt: StateFlow<Long> = _lastBackupAt.asStateFlow()

    private val _backupPath = MutableStateFlow<String?>(null)
    val backupPath: StateFlow<String?> = _backupPath.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _backupPasswordSet = MutableStateFlow(settings.hasBackupPassword)
    val backupPasswordSet: StateFlow<Boolean> = _backupPasswordSet.asStateFlow()

    /** 导入加密备份时，如果本机没有密钥就要用户输密码 */
    private val _importNeedsPassword = MutableStateFlow(false)
    val importNeedsPassword: StateFlow<Boolean> = _importNeedsPassword.asStateFlow()
    private var pendingImportUri: Uri? = null
    private var pendingImportSkipDuplicates = true

    fun consumeMessage() {
        _message.value = null
    }

    // ---------- 分类 ----------

    fun addCategory(name: String, isIncome: Boolean) {
        if (name.isBlank()) {
            _message.value = "分类名不能为空"
            return
        }
        viewModelScope.launch {
            repository.addCategory(name, isIncome)
            _message.value = "已添加「${name.trim()}」"
        }
    }

    fun renameCategory(entity: CategoryEntity, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) {
            _message.value = "分类名不能为空"
            return
        }
        viewModelScope.launch {
            repository.renameCategory(entity.copy(name = clean))
            _message.value = "已改名"
        }
    }

    fun deleteCategory(entity: CategoryEntity) {
        viewModelScope.launch {
            val ok = repository.deleteCategory(entity.id)
            _message.value = if (ok) "已删除「${entity.name}」" else "「${entity.name}」还有账单在用（含回收站里的），先把账单改到别的分类"
        }
    }

    // ---------- 账户 ----------

    fun addAccount(name: String) {
        if (name.isBlank()) {
            _message.value = "账户名不能为空"
            return
        }
        viewModelScope.launch {
            repository.addAccount(name)
            _message.value = "已添加「${name.trim()}」"
        }
    }

    fun renameAccount(entity: AccountEntity, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) {
            _message.value = "账户名不能为空"
            return
        }
        viewModelScope.launch {
            repository.renameAccount(entity.copy(name = clean))
            _message.value = "已改名"
        }
    }

    fun deleteAccount(entity: AccountEntity) {
        viewModelScope.launch {
            val ok = repository.deleteAccount(entity.id)
            _message.value = if (ok) "已删除「${entity.name}」" else "「${entity.name}」还有账单在用（含回收站里的），先把账单改到别的账户"
        }
    }

    // ---------- 税务 ----------

    fun saveTax(thresholdCents: Long, rateBasisPoints: Int, quarterLimitCents: Long) {
        if (thresholdCents < 0 || rateBasisPoints < 0 || quarterLimitCents <= 0) {
            _message.value = "数字看着不对，检查一下再存"
            return
        }
        settings.setTax(
            TaxSettings(
                thresholdCents = thresholdCents,
                rateBasisPoints = rateBasisPoints.coerceAtMost(10_000),
                quarterLimitCents = quarterLimitCents,
            )
        )
        _message.value = "税务规则已保存"
    }

    // ---------- 回收站 ----------

    fun restore(id: Long) {
        viewModelScope.launch {
            repository.restoreTransaction(id)
            _message.value = "已恢复"
        }
    }

    fun purge(id: Long) {
        viewModelScope.launch {
            repository.purgeTransaction(id)
            _message.value = "已彻底删除"
        }
    }

    fun purgeAll() {
        viewModelScope.launch {
            val n = repository.purgeAllDeleted()
            _message.value = "已彻底删除 $n 条"
        }
    }

    // ---------- 备份 ----------

    fun setAutoBackup(enabled: Boolean) = settings.setAutoBackup(enabled)

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val result = runCatching { backup.exportToUri(uri) }
            _busy.value = false
            _message.value = result.fold(
                onSuccess = { "已导出 $it 笔账单" },
                onFailure = { "导出失败：${it.message ?: "读写出错"}" },
            )
        }
    }

    fun setBackupPassword(password: String): Boolean {
        if (password.length < 6) {
            _message.value = "备份密码至少 6 位"
            return false
        }
        settings.setBackupPassword(password.toCharArray())
        _backupPasswordSet.value = true
        _message.value = "备份密码已设置，之后的备份都会加密"
        return true
    }

    fun clearBackupPassword() {
        settings.clearBackupPassword()
        _backupPasswordSet.value = false
        _message.value = "已清除备份密码，自动备份会退回明文 CSV（不建议）"
    }

    fun exportEncryptedTo(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val result = runCatching { backup.exportEncryptedToUri(uri) }
            _busy.value = false
            _message.value = result.fold(
                onSuccess = { "已导出加密备份 $it 条记录" },
                onFailure = { "导出失败：${it.message ?: "读写出错"}" },
            )
        }
    }

    fun exportZipTo(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val result = runCatching { backup.exportZipToUri(uri) }
            _busy.value = false
            _message.value = result.fold(
                onSuccess = { "已导出 $it 条记录（含单据图片）" },
                onFailure = { "导出失败：${it.message ?: "读写出错"}" },
            )
        }
    }

    fun importFrom(uri: Uri, skipDuplicates: Boolean = true, password: CharArray? = null) {
        viewModelScope.launch {
            _busy.value = true
            val result = runCatching { backup.importFromUri(uri, skipDuplicates, password) }
            _busy.value = false
            result.fold(
                onSuccess = { r ->
                    _importNeedsPassword.value = false
                    pendingImportUri = null
                    val parts = mutableListOf("导入账单 ${r.importedBills} 笔")
                    if (r.importedDebts > 0) parts += "赊账 ${r.importedDebts} 条"
                    val skipped = r.skippedBills + r.skippedDebts
                    if (skipped > 0) parts += "跳过 ${skipped} 条（重复或无效）"
                    _message.value = parts.joinToString("，")
                },
                onFailure = { error ->
                    if (error is com.yuanguangbb.ledger.data.backup.BackupPasswordRequired) {
                        _importNeedsPassword.value = true
                        pendingImportUri = uri
                        pendingImportSkipDuplicates = skipDuplicates
                        _message.value = "这个备份是加密的，请输入备份密码"
                    } else {
                        _message.value = "导入失败：${error.message ?: "文件读不了"}"
                    }
                },
            )
        }
    }

    /** 用户在弹窗里输入密码后重试导入 */
    fun submitImportPassword(password: String) {
        val uri = pendingImportUri ?: return
        importFrom(uri, pendingImportSkipDuplicates, password.toCharArray())
    }

    fun cancelImportPassword() {
        _importNeedsPassword.value = false
        pendingImportUri = null
    }

    fun backupNow() {
        viewModelScope.launch {
            _busy.value = true
            val result = runCatching { backup.autoBackup(force = true) }
            _busy.value = false
            result.onSuccess { location ->
                if (location != null) {
                    _lastBackupAt.value = System.currentTimeMillis()
                    _backupPath.value = location
                    _message.value = "已备份到 $location"
                } else {
                    _message.value = "备份没写成，检查存储空间"
                }
            }.onFailure {
                _message.value = "备份失败：${it.message ?: "读写出错"}"
            }
        }
    }

    fun refreshBackupInfo() {
        _lastBackupAt.value = settings.lastBackupAtMillis
        _backupPath.value = backup.lastBackupLocation()
    }

    // ---------- 应用锁 ----------

    fun setPin(pin: String): Boolean {
        if (!pin.matches(Regex("""\d{4,6}"""))) {
            _message.value = "请输入 4~6 位数字"
            return false
        }
        settings.setPin(pin)
        _pinSet.value = true
        _message.value = "应用锁已开启"
        return true
    }

    fun clearPin(currentPin: String): Boolean {
        if (!settings.verifyPin(currentPin)) {
            _message.value = "密码不对"
            return false
        }
        settings.clearPin()
        _pinSet.value = false
        _message.value = "应用锁已关闭"
        return true
    }
}