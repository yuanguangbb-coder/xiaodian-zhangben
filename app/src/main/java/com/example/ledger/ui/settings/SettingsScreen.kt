package com.example.ledger.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ledger.data.db.AccountEntity
import com.example.ledger.data.db.CategoryEntity
import com.example.ledger.domain.formatCents
import com.example.ledger.ui.AppViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class SettingsSection(val title: String, val subtitle: String) {
    CATEGORIES("分类管理", "加自己的分类，比如外卖平台服务费、烟酒"),
    ACCOUNTS("账户管理", "微信、支付宝、现金、银行卡，可以自己加"),
    TAX("税务规则", "免税额、税率、季度起征点，自己填"),
    RECYCLE("回收站", "删掉的账 30 天内可以恢复"),
    BACKUP("备份与恢复", "导出 CSV，换手机或重装能导回来"),
    LOCK("应用锁", "打开 App 要输 4 位数字"),
    ABOUT("关于", "版本和说明"),
}

private val backupTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")

private fun centsToYuanText(cents: Long): String =
    BigDecimal(cents).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY).toPlainString()

private fun yuanTextToCents(text: String): Long? =
    text.trim().toBigDecimalOrNull()?.movePointRight(2)
        ?.setScale(0, RoundingMode.HALF_UP)?.toLong()

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    var section by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val text = message
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? -> uri?.let(viewModel::exportTo) }

    val exportZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? -> uri?.let(viewModel::exportZipTo) }

    val exportEncryptedLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? -> uri?.let(viewModel::exportEncryptedTo) }

    // 导入时用哪种去重策略（对话框里选，选择结果带到文件选择回调）
    var pendingImportSkip by remember { mutableStateOf(true) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.importFrom(it, pendingImportSkip) } }

    Box(Modifier.fillMaxSize()) {
        val current = section
        if (current == null) {
            SettingsMenu(onOpen = { section = it })
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { section = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Text(current.title, style = MaterialTheme.typography.titleLarge)
                }
                when (current) {
                    SettingsSection.CATEGORIES -> CategoriesSection(viewModel)
                    SettingsSection.ACCOUNTS -> AccountsSection(viewModel)
                    SettingsSection.TAX -> TaxSection(viewModel)
                    SettingsSection.RECYCLE -> RecycleSection(viewModel)
                    SettingsSection.BACKUP -> BackupSection(
                        viewModel = viewModel,
                        onExport = { exportLauncher.launch("账单备份.csv") },
                        onExportZip = { exportZipLauncher.launch("账单备份-含单据.zip") },
                        onExportEncrypted = { exportEncryptedLauncher.launch("账单备份.yjb") },
                        onImport = { skipDuplicates -> pendingImportSkip = skipDuplicates; importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/zip", "application/octet-stream", "text/*")) },
                    )
                    SettingsSection.LOCK -> LockSection(viewModel)
                    SettingsSection.ABOUT -> AboutSection()
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 88.dp),
        )
    }

    val needsPassword by viewModel.importNeedsPassword.collectAsStateWithLifecycle()
    if (needsPassword) {
        var importPassword by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.cancelImportPassword() },
            title = { Text("输入备份密码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "这个备份文件是加密的，请输入当时设置的备份密码。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it },
                        label = { Text("备份密码") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.submitImportPassword(importPassword) }) { Text("解密导入") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelImportPassword() }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SettingsMenu(onOpen: (SettingsSection) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)
        SettingsSection.entries.forEach { s ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().clickable { onOpen(s) },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(s.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        s.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun CategoriesSection(viewModel: SettingsViewModel) {
    val categories by viewModel.allCategories.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<CategoryEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "共 ${categories.size} 个分类",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showAdd = true }) { Text("新增分类") }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
        ) {
            items(categories, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (c.isIncome) "收入分类" else "支出分类",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { renameTarget = c }) { Text("改名") }
                    TextButton(onClick = { viewModel.deleteCategory(c) }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider()
            }
        }
    }

    if (showAdd) {
        CategoryDialog(
            title = "新增分类",
            initialName = "",
            initialIsIncome = false,
            onDismiss = { showAdd = false },
            onConfirm = { name, isIncome ->
                viewModel.addCategory(name, isIncome)
                showAdd = false
            },
        )
    }

    renameTarget?.let { target ->
        CategoryDialog(
            title = "改分类名",
            initialName = target.name,
            initialIsIncome = target.isIncome,
            lockType = true,
            onDismiss = { renameTarget = null },
            onConfirm = { name, _ ->
                viewModel.renameCategory(target, name)
                renameTarget = null
            },
        )
    }
}

@Composable
private fun CategoryDialog(
    title: String,
    initialName: String,
    initialIsIncome: Boolean,
    lockType: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var isIncome by remember { mutableStateOf(initialIsIncome) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分类名") },
                    singleLine = true,
                )
                if (!lockType) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !isIncome,
                            onClick = { isIncome = false },
                            label = { Text("支出") },
                        )
                        FilterChip(
                            selected = isIncome,
                            onClick = { isIncome = true },
                            label = { Text("收入") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, isIncome) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AccountsSection(viewModel: SettingsViewModel) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<AccountEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "共 ${accounts.size} 个账户",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showAdd = true }) { Text("新增账户") }
        }
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp)) {
            items(accounts, key = { it.id }) { a ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(a.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { renameTarget = a }) { Text("改名") }
                    TextButton(onClick = { viewModel.deleteAccount(a) }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider()
            }
        }
    }

    if (showAdd || renameTarget != null) {
        var name by remember { mutableStateOf(renameTarget?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = {
                showAdd = false
                renameTarget = null
            },
            title = { Text(if (renameTarget != null) "改账户名" else "新增账户") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("账户名") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renameTarget
                    if (target != null) viewModel.renameAccount(target, name)
                    else viewModel.addAccount(name)
                    showAdd = false
                    renameTarget = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAdd = false
                    renameTarget = null
                }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun TaxSection(viewModel: SettingsViewModel) {
    val tax by viewModel.tax.collectAsStateWithLifecycle()
    var threshold by remember(tax) { mutableStateOf(centsToYuanText(tax.thresholdCents)) }
    var rate by remember(tax) {
        mutableStateOf(BigDecimal(tax.rateBasisPoints).movePointLeft(2).stripTrailingZeros().toPlainString())
    }
    var limit by remember(tax) { mutableStateOf(centsToYuanText(tax.quarterLimitCents)) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "这些数字按你当地税务局的规定自己填。首页的税额和起征点提醒都用这里的设置，只是估算。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = threshold,
            onValueChange = { threshold = it },
            label = { Text("每月免税额（元）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = rate,
            onValueChange = { rate = it },
            label = { Text("税率（%）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = limit,
            onValueChange = { limit = it },
            label = { Text("季度起征点（元）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                val t = yuanTextToCents(threshold) ?: 0L
                val r = rate.trim().toBigDecimalOrNull()?.movePointRight(2)?.toInt() ?: 0
                val l = yuanTextToCents(limit) ?: 0L
                viewModel.saveTax(t, r, l)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存税务规则") }

        Text(
            "现在：免税额 ¥${formatCents(tax.thresholdCents)}，税率 " +
                BigDecimal(tax.rateBasisPoints).movePointLeft(2).stripTrailingZeros().toPlainString() +
                "%，季度起征点 ¥${formatCents(tax.quarterLimitCents)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RecycleSection(viewModel: SettingsViewModel) {
    val items by viewModel.deletedTransactions.collectAsStateWithLifecycle()
    var confirmPurgeAll by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (items.isEmpty()) "回收站是空的" else "回收站里有 ${items.size} 笔",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { confirmPurgeAll = true }, enabled = items.isNotEmpty()) {
                Text("清空回收站", color = MaterialTheme.colorScheme.error)
            }
        }
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp)) {
            items(items, key = { it.id }) { t ->
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (t.isIncome) "+" else "-") + "¥" + formatCents(t.amountCents),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.restore(t.id) }) { Text("恢复") }
                        TextButton(onClick = { viewModel.purge(t.id) }) {
                            Text("彻底删", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Text(
                        "${t.categoryName} · ${t.accountName} · " +
                            Instant.ofEpochMilli(t.timeMillis).atZone(ZoneId.systemDefault())
                                .format(backupTimeFormatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    t.deletedAtMillis?.let { at ->
                        Text(
                            "删除于 " + Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
                                .format(backupTimeFormatter),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }

    if (confirmPurgeAll) {
        AlertDialog(
            onDismissRequest = { confirmPurgeAll = false },
            title = { Text("清空回收站？") },
            text = { Text("回收站里的 ${items.size} 笔会被永久删除，之后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.purgeAll()
                    confirmPurgeAll = false
                }) { Text("永久删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmPurgeAll = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun BackupSection(
    viewModel: SettingsViewModel,
    onExport: () -> Unit,
    onExportZip: () -> Unit,
    onExportEncrypted: () -> Unit,
    onImport: (Boolean) -> Unit,
) {
    val autoBackup by viewModel.autoBackup.collectAsStateWithLifecycle()
    val lastBackupAt by viewModel.lastBackupAt.collectAsStateWithLifecycle()
    val path by viewModel.backupPath.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val passwordSet by viewModel.backupPasswordSet.collectAsStateWithLifecycle()
    var confirmImport by remember { mutableStateOf(false) }
    var showSetPassword by remember { mutableStateOf(false) }
    var showClearPassword by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshBackupInfo() }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "CSV 给 Excel/WPS 看；ZIP 带单据照片；加密备份（.yjb）用密码保护，丢了密码就打不开。" +
                "自动备份存到「下载/小店账本」，卸载 App 不会丢。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onExport, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("导出到文件（CSV）")
        }
        OutlinedButton(onClick = onExportZip, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("导出（含单据图片，ZIP）")
        }
        OutlinedButton(
            onClick = onExportEncrypted,
            enabled = !busy && passwordSet,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (passwordSet) "导出加密备份（.yjb）" else "导出加密备份（先设密码）") }

        // 备份密码
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("备份密码", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (passwordSet) "已设置：自动备份会加密保存" else "未设置：自动备份是明文 CSV",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (passwordSet) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = { showSetPassword = true }) {
                Text(if (passwordSet) "更换" else "设置")
            }
            if (passwordSet) {
                TextButton(onClick = { showClearPassword = true }) { Text("清除") }
            }
        }

        Text(
            "注意：CSV/ZIP 是明文，任何能看文件的 App 都能读；加密备份才带密码保护。" +
                "密码忘了没有后门，请另存一份。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(onClick = { confirmImport = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("从文件恢复（CSV / ZIP / 加密备份）")
        }
        OutlinedButton(
            onClick = { viewModel.backupNow() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("立即备份到手机里") }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("每天自动备份", style = MaterialTheme.typography.titleSmall)
                Text(
                    "存在 App 自己的文件夹里，不进系统相册",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = autoBackup, onCheckedChange = { viewModel.setAutoBackup(it) })
        }

        Text(
            text = if (lastBackupAt > 0) {
                "上次备份：" + Instant.ofEpochMilli(lastBackupAt).atZone(ZoneId.systemDefault())
                    .format(backupTimeFormatter)
            } else {
                "还没备份过"
            },
            style = MaterialTheme.typography.bodySmall,
        )
        path?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            Text(
                "这台手机是 Android 9 及以下，自动备份只能存在 App 自己的目录里，卸载会一起删掉；" +
                    "换机前请用上面的「导出到文件」手动存一份。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("从文件恢复？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("文件里的账单会追加到现在的账本，不会删掉现有数据。分类和账户对不上的会自动新建。")
                    TextButton(onClick = {
                        confirmImport = false
                        onImport(true)
                    }) { Text("跳过重复（推荐）") }
                    TextButton(onClick = {
                        confirmImport = false
                        onImport(false)
                    }) { Text("全部导入（确实有两笔一模一样的账）") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { confirmImport = false }) { Text("取消") }
            },
        )
    }

    if (showSetPassword) {
        var password by remember { mutableStateOf("") }
        var repeat by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSetPassword = false },
            title = { Text("设置备份密码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "至少 6 位。密码忘了没有后门，恢复不了，请另存一份。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = "" },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    )
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it; error = "" },
                        label = { Text("再输一次") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    )
                    if (error.isNotEmpty()) {
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        password.length < 6 -> error = "至少 6 位"
                        password != repeat -> error = "两次输入不一致"
                        viewModel.setBackupPassword(password) -> showSetPassword = false
                        else -> error = "设置失败，再试一次"
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSetPassword = false }) { Text("取消") }
            },
        )
    }

    if (showClearPassword) {
        AlertDialog(
            onDismissRequest = { showClearPassword = false },
            title = { Text("清除备份密码？") },
            text = { Text("清除后，之后的自动备份会变回明文 CSV；已经导出的加密文件仍然需要原密码才能打开。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearBackupPassword()
                    showClearPassword = false
                }) { Text("清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearPassword = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun LockSection(viewModel: SettingsViewModel) {
    val pinSet by viewModel.pinSet.collectAsStateWithLifecycle()
    var showSetPin by remember { mutableStateOf(false) }
    var showChangePin by remember { mutableStateOf(false) }
    var showClearPin by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (pinSet) "已开启：每次打开 App 都要输 4 位数字。" else "没开启：打开 App 直接进账本。",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!pinSet) {
            OutlinedButton(onClick = { showSetPin = true }, modifier = Modifier.fillMaxWidth()) {
                Text("开启应用锁")
            }
        } else {
            OutlinedButton(onClick = { showChangePin = true }, modifier = Modifier.fillMaxWidth()) {
                Text("修改密码")
            }
            OutlinedButton(onClick = { showClearPin = true }, modifier = Modifier.fillMaxWidth()) {
                Text("关闭应用锁")
            }
        }
        Text(
            "忘了密码进不来，只能卸载重装（数据会丢），建议先备份。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showSetPin || showChangePin) {
        PinDialog(
            title = if (showChangePin) "设置新密码（4~6 位）" else "设置 4~6 位数字密码",
            onDismiss = {
                showSetPin = false
                showChangePin = false
            },
            onConfirm = { pin ->
                if (viewModel.setPin(pin)) {
                    showSetPin = false
                    showChangePin = false
                }
            },
        )
    }

    if (showClearPin) {
        PinDialog(
            title = "输入当前密码",
            onDismiss = { showClearPin = false },
            onConfirm = { pin ->
                if (viewModel.clearPin(pin)) showClearPin = false
            },
        )
    }
}

@Composable
private fun PinDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { text -> if (text.length <= 6 && text.all { it.isDigit() }) pin = text },
                label = { Text("4~6 位数字") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pin) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    var crashText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        crashText = withContext(Dispatchers.IO) {
            val file = com.example.ledger.LedgerApplication.crashLogFile(context.filesDir)
            if (file.exists()) file.readText(Charsets.UTF_8).take(1500) else null
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("小店账本 0.7.0", style = MaterialTheme.typography.titleMedium)
        Text(
            "给县城个体户用的记账小工具：记一笔、看利润、算个大概的税、盯住压货和卖得快的货。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "所有数据都存在这台手机上，不上传任何服务器。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "税额和起征点都只是按你自己填的规则估算，不是申报依据，请以当地税务局最新规定为准。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "应用锁只是防止熟人随手翻看，不是系统级加密，也不等于数据加密。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val crash = crashText
        if (crash != null) {
            Text("上次崩溃记录", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Text(
                crash,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                runCatching {
                    com.example.ledger.LedgerApplication.crashLogFile(context.filesDir).delete()
                }
                crashText = null
            }) { Text("清除记录") }
        }
        Spacer(Modifier.width(1.dp))
    }
}