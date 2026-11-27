package com.yuanguangbb.ledger.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.yuanguangbb.ledger.data.db.TransactionEntity
import com.yuanguangbb.ledger.data.repository.LedgerRepository
import com.yuanguangbb.ledger.data.settings.AppSettings
import com.yuanguangbb.ledger.domain.backup.BackupContent
import com.yuanguangbb.ledger.domain.backup.BackupCrypto
import com.yuanguangbb.ledger.domain.backup.CsvLedger
import com.yuanguangbb.ledger.domain.backup.CsvRow
import com.yuanguangbb.ledger.domain.backup.DebtCsvRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ImportResult(
    val importedBills: Int,
    val skippedBills: Int,
    val importedDebts: Int,
    val skippedDebts: Int,
)

/** 需要用户输入备份密码才能继续 */
class BackupPasswordRequired : Exception("这个备份文件是加密的，请输入备份密码")

/**
 * 记账数据的备份与恢复。
 *
 * 三种导出：
 *  - CSV：账单 + 赊账，Excel 能开
 *  - ZIP：CSV + receipts/ 单据照片
 *  - 加密备份（.yjb）：ZIP 内容经 AES-256-GCM 加密，换手机时用密码解密
 *
 * 自动备份每天一次：设了备份密码就写加密文件，否则写明文 CSV（设置页会提示风险）。
 */
class BackupManager(
    private val context: Context,
    private val repository: LedgerRepository,
    private val settings: AppSettings,
) {

    private val autoBackupDir: File?
        get() = context.getExternalFilesDir(null)?.resolve("backup")

    // ---------- 导出 ----------

    /** 纯 CSV。返回写入了多少条记录。 */
    suspend fun exportToUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        val content = collectContent()
        writeBytes(uri, CsvLedger.encode(content).toByteArray(Charsets.UTF_8))
        content.bills.size + content.debts.size
    }

    /** ZIP（含单据照片）。 */
    suspend fun exportZipToUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        val content = collectContent()
        writeBytes(uri, buildZipBytes(content))
        content.bills.size + content.debts.size
    }

    /** 加密备份（.yjb）：ZIP 内容加密后再写。需要先在设置里设备份密码。 */
    suspend fun exportEncryptedToUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        val key = settings.backupKey() ?: error("请先在设置里设置备份密码")
        val salt = settings.backupSalt() ?: error("备份密钥已损坏，请重新设置备份密码")
        val content = collectContent()
        val zip = buildZipBytes(content)
        writeBytes(uri, BackupCrypto.encrypt(zip, key, salt))
        content.bills.size + content.debts.size
    }

    /** 自动备份：一天最多一份。设了密码写 .yjb，否则写明文 CSV。返回保存位置。 */
    suspend fun autoBackup(force: Boolean = false): String? = withContext(Dispatchers.IO) {
        if (!force) {
            val last = settings.lastBackupAtMillis
            if (System.currentTimeMillis() - last < 24L * 60 * 60 * 1000) return@withContext null
        }
        val content = collectContent()
        if (content.isEmpty) return@withContext null

        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val key = settings.backupKey()
        val salt = settings.backupSalt()
        val encrypted = key != null && salt != null

        val payload = if (encrypted) {
            BackupCrypto.encrypt(buildZipBytes(content), key!!, salt!!)
        } else {
            CsvLedger.encode(content).toByteArray(Charsets.UTF_8)
        }
        val fileName = if (encrypted) "账单备份-$date.yjb" else "账单备份-$date.csv"
        val location = writeBackup(fileName, payload) ?: return@withContext null
        settings.lastBackupAtMillis = System.currentTimeMillis()
        settings.lastBackupPath = location
        location
    }

    fun lastBackupLocation(): String? = settings.lastBackupPath

    // ---------- 导入 ----------

    /**
     * 从文件恢复，自动识别：加密备份(.yjb) / ZIP(含单据) / CSV。
     * @param password 加密文件在本机没有密钥时的密码
     * @param skipDuplicates true = 按内容指纹跳过已有的账
     */
    suspend fun importFromUri(
        uri: Uri,
        skipDuplicates: Boolean = true,
        password: CharArray? = null,
    ): ImportResult = withContext(Dispatchers.IO) {
        val bytes = readBytes(uri)
        when {
            BackupCrypto.isEncrypted(bytes) -> {
                val decrypted = settings.backupKey()?.let { BackupCrypto.decrypt(bytes, it) }
                    ?: password?.let { BackupCrypto.decrypt(bytes, it) }
                    ?: throw BackupPasswordRequired()
                importPayload(decrypted, skipDuplicates)
            }
            isZip(bytes) -> importPayload(bytes, skipDuplicates)
            else -> importCsv(bytes.toString(Charsets.UTF_8), skipDuplicates, emptyMap())
        }
    }

    private suspend fun importPayload(bytes: ByteArray, skipDuplicates: Boolean): ImportResult {
        if (isZip(bytes)) {
            val (csv, receipts) = unzip(bytes)
            return importCsv(csv, skipDuplicates, receipts)
        }
        return importCsv(bytes.toString(Charsets.UTF_8), skipDuplicates, emptyMap())
    }

    // ---------- 内部：打包 / 解包 ----------

    private fun buildZipBytes(content: BackupContent): ByteArray {
        val receiptEntries = LinkedHashMap<String, String>()
        var index = 0
        content.bills.forEach { bill ->
            val path = bill.receiptPath
            if (path != null && File(path).exists()) {
                receiptEntries.getOrPut(path) {
                    val ext = File(path).extension.lowercase().ifEmpty { "jpg" }
                    "receipts/r${index++}.$ext"
                }
            }
        }
        val csv = CsvLedger.encode(
            content.copy(bills = content.bills.map { it.copy(receiptPath = it.receiptPath?.let { p -> receiptEntries[p] }) })
        )
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(BufferedOutputStream(buffer)).use { zip ->
            zip.putNextEntry(ZipEntry("账单备份.csv"))
            zip.write(csv.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            receiptEntries.forEach { (path, entryName) ->
                zip.putNextEntry(ZipEntry(entryName))
                File(path).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return buffer.toByteArray()
    }

    /** 只解出 CSV 文本和单据（单据落到 App 私有目录，文件名用 UUID，条目里的路径一律不信） */
    private fun unzip(bytes: ByteArray): Pair<String, Map<String, String>> {
        var csv: String? = null
        val receiptFiles = HashMap<String, String>()
        val receiptDir = File(context.filesDir, "receipts").apply { mkdirs() }

        ZipInputStream(BufferedInputStream(ByteArrayInputStream(bytes))).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name.endsWith(".csv", ignoreCase = true) -> {
                        csv = zip.readBytes().toString(Charsets.UTF_8)
                    }
                    entry.name.startsWith("receipts/", ignoreCase = true) && !entry.isDirectory -> {
                        // 扩展名白名单，防止奇怪文件名
                        val ext = entry.name.substringAfterLast('.', "jpg").lowercase()
                        val safeExt = if (ext.matches(Regex("[a-z0-9]{1,5}"))) ext else "jpg"
                        val target = File(receiptDir, UUID.randomUUID().toString() + "." + safeExt)
                        target.outputStream().use { out -> zip.copyTo(out) }
                        receiptFiles[entry.name] = target.absolutePath
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return (csv ?: error("压缩包里没找到 CSV")) to receiptFiles
    }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    private fun readBytes(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("读不了这个文件")

    private fun writeBytes(uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: error("打不开这个位置")
    }

    private suspend fun importCsv(
        text: String,
        skipDuplicates: Boolean,
        receiptFiles: Map<String, String>,
    ): ImportResult {
        val content = CsvLedger.decode(text)

        val existingBills = if (skipDuplicates) {
            repository.transactions().first().map { billKey(it) }.toHashSet()
        } else {
            hashSetOf()
        }
        val existingDebts = if (skipDuplicates) {
            repository.debtsForBackup().map {
                debtKey(it.customerName, it.timeMillis, it.amountCents)
            }.toHashSet()
        } else {
            hashSetOf()
        }

        var importedBills = 0
        var skippedBills = 0
        val entities = ArrayList<TransactionEntity>(content.bills.size)
        content.bills.forEach { row ->
            if (row.amountCents <= 0) {
                skippedBills++
                return@forEach
            }
            if (skipDuplicates && !existingBills.add(billKey(row))) {
                skippedBills++
                return@forEach
            }
            val categoryId = repository.findOrCreateCategoryId(row.categoryName, row.isIncome)
            val accountId = repository.findOrCreateAccountId(row.accountName)
            val productId = repository.getOrCreateProductId(row.productName.orEmpty())
            val receiptPath = row.receiptPath?.let { receiptFiles[it] ?: it }
            entities.add(
                TransactionEntity(
                    isIncome = row.isIncome,
                    amountCents = row.amountCents,
                    categoryId = categoryId,
                    accountId = accountId,
                    timeMillis = row.timeMillis,
                    createdAtMillis = row.createdAtMillis,
                    note = row.note,
                    receiptPath = receiptPath,
                    productId = productId,
                    quantity = if (productId != null) row.quantity else null,
                )
            )
            importedBills++
        }
        repository.importTransactions(entities)

        var importedDebts = 0
        var skippedDebts = 0
        content.debts.forEach { row ->
            if (row.amountCents <= 0) {
                skippedDebts++
                return@forEach
            }
            if (skipDuplicates &&
                !existingDebts.add(debtKey(row.customerName, row.timeMillis, row.amountCents))
            ) {
                skippedDebts++
                return@forEach
            }
            val customerId = repository.addCustomer(row.customerName, "")
                ?: run { skippedDebts++; return@forEach }
            repository.addDebt(
                customerId = customerId,
                customerName = row.customerName,
                amountCents = row.amountCents,
                timeMillis = row.timeMillis,
                note = row.note,
                settledAtMillis = row.settledAtMillis,
                createIncome = false,
            )
            importedDebts++
        }

        return ImportResult(
            importedBills = importedBills,
            skippedBills = skippedBills,
            importedDebts = importedDebts,
            skippedDebts = skippedDebts,
        )
    }

    private fun billKey(row: CsvRow): String = listOf(
        row.timeMillis, row.createdAtMillis, row.isIncome, row.amountCents,
        row.categoryName, row.accountName, row.productName.orEmpty(),
        row.quantity?.toString().orEmpty(), row.note,
    ).joinToString("|")

    private fun billKey(t: com.yuanguangbb.ledger.data.db.TransactionWithNames): String = listOf(
        t.timeMillis, t.createdAtMillis, t.isIncome, t.amountCents,
        t.categoryName, t.accountName, t.productName.orEmpty(),
        t.quantity?.toString().orEmpty(), t.note,
    ).joinToString("|")

    private fun debtKey(customer: String, timeMillis: Long, amountCents: Long): String =
        "$customer|$timeMillis|$amountCents"

    private suspend fun collectContent(): BackupContent {
        val bills = repository.transactions().first().map { t ->
            CsvRow(
                isIncome = t.isIncome,
                amountCents = t.amountCents,
                categoryName = t.categoryName,
                accountName = t.accountName,
                timeMillis = t.timeMillis,
                createdAtMillis = t.createdAtMillis,
                productName = t.productName,
                quantity = t.quantity,
                note = t.note,
                receiptPath = t.receiptPath,
            )
        }
        val debts = repository.debtsForBackup().map { d ->
            DebtCsvRow(
                customerName = d.customerName,
                amountCents = d.amountCents,
                timeMillis = d.timeMillis,
                note = d.note,
                settledAtMillis = d.settledAtMillis,
            )
        }
        return BackupContent(bills = bills, debts = debts)
    }

    /**
     * Android 10+：写到公共「下载/小店账本」，卸载 App 不会一起删。
     * Android 9 及以下：退回 App 私有目录（设置页会提示）。
     */
    private fun writeBackup(fileName: String, payload: ByteArray): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relative = "${Environment.DIRECTORY_DOWNLOADS}/小店账本/"
            val resolver = context.contentResolver
            runCatching {
                resolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
                    arrayOf(fileName, relative),
                )
            }
            val mime = if (fileName.endsWith(".csv")) "text/csv" else "application/octet-stream"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, relative)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            runCatching {
                resolver.openOutputStream(uri)?.use { it.write(payload) }
            }.onFailure {
                runCatching { resolver.delete(uri, null, null) }
                return null
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return "下载/小店账本/$fileName"
        }

        val dir = autoBackupDir ?: return null
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, fileName)
        return runCatching {
            file.writeBytes(payload)
            file.absolutePath
        }.getOrNull()
    }
}