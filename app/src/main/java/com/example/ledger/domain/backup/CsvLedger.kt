package com.example.ledger.domain.backup

import com.example.ledger.domain.formatCentsPlain
import com.example.ledger.domain.parseYuanToCents
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 备份文件里的一行账。分类/账户用名字存，换手机也能认出来。 */
data class CsvRow(
    val isIncome: Boolean,
    val amountCents: Long,
    val categoryName: String,
    val accountName: String,
    val timeMillis: Long,
    val createdAtMillis: Long,
    val productName: String?,
    val quantity: Int?,
    val note: String,
    val receiptPath: String?,
)

/** 备份文件里的一条赊账。用客户名字关联，不依赖自增 id。 */
data class DebtCsvRow(
    val customerName: String,
    val amountCents: Long,
    val timeMillis: Long,
    val note: String,
    val settledAtMillis: Long?,
)

/** 一份备份里的全部内容 */
data class BackupContent(
    val bills: List<CsvRow> = emptyList(),
    val debts: List<DebtCsvRow> = emptyList(),
) {
    val isEmpty: Boolean get() = bills.isEmpty() && debts.isEmpty()
}

/**
 * 账单 + 赊账的 CSV 编解码。纯 Kotlin，不依赖 Android，方便单元测试。
 *
 * v2 格式（第一列区分类型，Excel/WPS 直接能看）：
 * 记录类型,收入/支出,金额(元),分类,账户,记账时间,录入时间,商品,数量,备注,单据,客户,已还时间
 *
 * 老版本（v1，10 列、没有「记录类型」）也能导入，按账单处理。
 */
object CsvLedger {

    const val TYPE_BILL = "账单"
    const val TYPE_DEBT = "赊账"

    const val HEADER =
        "记录类型,收入/支出,金额(元),分类,账户,记账时间,录入时间,商品,数量,备注,单据,客户,已还时间"

    private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun encode(content: BackupContent, zone: ZoneId = ZoneId.systemDefault()): String =
        buildString {
            append(HEADER).append("\r\n")
            content.bills.forEach { r ->
                append(
                    line(
                        TYPE_BILL,
                        if (r.isIncome) "收入" else "支出",
                        formatCentsPlain(r.amountCents),
                        r.categoryName,
                        r.accountName,
                        formatTime(r.timeMillis, zone),
                        formatTime(r.createdAtMillis, zone),
                        r.productName.orEmpty(),
                        r.quantity?.toString().orEmpty(),
                        r.note,
                        r.receiptPath.orEmpty(),
                        "",
                        "",
                    )
                )
            }
            content.debts.forEach { d ->
                append(
                    line(
                        TYPE_DEBT,
                        "",
                        formatCentsPlain(d.amountCents),
                        "",
                        "",
                        formatTime(d.timeMillis, zone),
                        "",
                        "",
                        "",
                        d.note,
                        "",
                        d.customerName,
                        d.settledAtMillis?.let { formatTime(it, zone) }.orEmpty(),
                    )
                )
            }
        }

    /** 一行 13 列，逐格转义后拼起来 */
    private fun line(vararg cells: String): String =
        cells.joinToString(separator = ",") { escape(it) } + "\r\n"

    fun decode(text: String, zone: ZoneId = ZoneId.systemDefault()): BackupContent {
        val records = parseRecords(text.removePrefix("\uFEFF"))
        val bills = ArrayList<CsvRow>()
        val debts = ArrayList<DebtCsvRow>()

        records.forEach { fields ->
            if (fields.isEmpty()) return@forEach
            when (fields[0].trim()) {
                // v2
                TYPE_BILL -> parseBill(fields, zone, offset = 1)?.let { bills.add(it) }
                TYPE_DEBT -> parseDebt(fields, zone)?.let { debts.add(it) }
                // v1 老文件：第一列就是「收入/支出」
                "收入", "支出" -> parseBill(fields, zone, offset = 0)?.let { bills.add(it) }
                // 表头行，跳过
                "记录类型", "收入/支出" -> Unit
            }
        }
        return BackupContent(bills = bills, debts = debts)
    }

    /** offset=0：v1（10 列）；offset=1：v2（有「记录类型」列） */
    private fun parseBill(fields: List<String>, zone: ZoneId, offset: Int): CsvRow? {
        if (fields.size < offset + 9) return null
        val amount = parseYuanToCents(fields[offset + 1]) ?: return null
        val time = parseTime(fields[offset + 4], zone) ?: return null
        val created = parseTime(fields[offset + 5], zone) ?: time
        return CsvRow(
            isIncome = fields[offset].trim() == "收入",
            amountCents = amount,
            categoryName = fields[offset + 2].trim(),
            accountName = fields[offset + 3].trim(),
            timeMillis = time,
            createdAtMillis = created,
            productName = fields[offset + 6].trim().ifEmpty { null },
            quantity = fields[offset + 7].trim().toIntOrNull()?.takeIf { it > 0 },
            note = fields[offset + 8],
            receiptPath = fields.getOrNull(offset + 9)?.trim()?.ifEmpty { null },
        )
    }

    private fun parseDebt(fields: List<String>, zone: ZoneId): DebtCsvRow? {
        if (fields.size < 12) return null
        val amount = parseYuanToCents(fields[2]) ?: return null
        val time = parseTime(fields[5], zone) ?: return null
        val customer = fields[11].trim()
        if (customer.isEmpty()) return null
        return DebtCsvRow(
            customerName = customer,
            amountCents = amount,
            timeMillis = time,
            note = fields[9],
            settledAtMillis = fields.getOrNull(12)?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { parseTime(it, zone) },
        )
    }

    /** 含逗号、引号、换行时用双引号包起来，内部引号翻倍 */
    internal fun escape(value: String): String {
        val needsQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuote) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun formatTime(millis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(FORMATTER)

    private fun parseTime(text: String, zone: ZoneId): Long? = runCatching {
        LocalDateTime.parse(text.trim(), FORMATTER).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    /** 逐字符扫，支持引号里的逗号和换行 */
    private fun parseRecords(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' -> {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                inQuotes -> field.append(c)
                c == '"' -> inQuotes = true
                c == ',' -> {
                    fields.add(field.toString())
                    field.setLength(0)
                }
                c == '\n' -> {
                    fields.add(field.toString())
                    field.setLength(0)
                    if (fields.any { it.isNotEmpty() }) records.add(fields.toList())
                    fields = mutableListOf()
                }
                c == '\r' -> Unit
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || fields.isNotEmpty()) {
            fields.add(field.toString())
            if (fields.any { it.isNotEmpty() }) records.add(fields.toList())
        }
        return records
    }
}