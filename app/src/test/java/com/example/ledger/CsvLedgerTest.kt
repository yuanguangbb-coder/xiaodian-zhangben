package com.example.ledger

import com.example.ledger.domain.backup.BackupContent
import com.example.ledger.domain.backup.CsvLedger
import com.example.ledger.domain.backup.CsvRow
import com.example.ledger.domain.backup.DebtCsvRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class CsvLedgerTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun bill(
        isIncome: Boolean = false,
        amountCents: Long = 1234,
        category: String = "进货",
        account: String = "微信",
        time: Long = 1_800_000_000_000,
        created: Long = 1_800_000_060_000,
        product: String? = "可乐",
        quantity: Int? = 12,
        note: String = "",
        receipt: String? = null,
    ) = CsvRow(
        isIncome = isIncome,
        amountCents = amountCents,
        categoryName = category,
        accountName = account,
        timeMillis = time,
        createdAtMillis = created,
        productName = product,
        quantity = quantity,
        note = note,
        receiptPath = receipt,
    )

    @Test
    fun roundTrip_keepsAllBillFields() {
        val original = listOf(
            bill(isIncome = false, amountCents = 5000, note = "含逗号, 和引号\"的备注"),
            bill(isIncome = true, amountCents = 2000, category = "销售收入", product = null, quantity = null),
        )
        val decoded = CsvLedger.decode(CsvLedger.encode(BackupContent(bills = original), zone), zone)
        assertEquals(original, decoded.bills)
        assertTrue(decoded.debts.isEmpty())
    }

    @Test
    fun roundTrip_keepsDebtFieldsIncludingSettledTime() {
        val debts = listOf(
            DebtCsvRow("老王", 12_500, 1_800_000_000_000, "赊两条烟", null),
            DebtCsvRow("小李", 3_000, 1_790_000_000_000, "", 1_795_000_000_000),
        )
        val decoded = CsvLedger.decode(CsvLedger.encode(BackupContent(debts = debts), zone), zone)
        assertEquals(debts, decoded.debts)
        assertTrue(decoded.bills.isEmpty())
    }

    @Test
    fun mixedBackup_keepsBothTypes() {
        val content = BackupContent(
            bills = listOf(bill()),
            debts = listOf(DebtCsvRow("老张", 500, 1_800_000_000_000, "", null)),
        )
        val decoded = CsvLedger.decode(CsvLedger.encode(content, zone), zone)
        assertEquals(1, decoded.bills.size)
        assertEquals(1, decoded.debts.size)
        assertEquals("老张", decoded.debts.single().customerName)
    }

    @Test
    fun headerIsWrittenAndSkipped() {
        val csv = CsvLedger.encode(BackupContent(bills = listOf(bill())), zone)
        assertTrue(csv.startsWith(CsvLedger.HEADER))
        assertEquals(1, CsvLedger.decode(csv, zone).bills.size)
    }

    @Test
    fun newlineInsideNote_survivesRoundTrip() {
        val original = listOf(bill(note = "第一行\n第二行"))
        val decoded = CsvLedger.decode(CsvLedger.encode(BackupContent(bills = original), zone), zone)
        assertEquals("第一行\n第二行", decoded.bills.single().note)
    }

    @Test
    fun badAmountsAreSkipped() {
        val csv = CsvLedger.HEADER + "\r\n" +
            "账单,支出,abc,进货,微信,2026-11-25 10:00:00,2026-11-25 10:00:00,,,,\r\n" +
            "账单,支出,12.50,进货,微信,2026-11-25 10:00:00,2026-11-25 10:00:00,,,,\r\n"
        val bills = CsvLedger.decode(csv, zone).bills
        assertEquals(1, bills.size)
        assertEquals(1250L, bills.single().amountCents)
    }

    @Test
    fun emptyProductAndQuantityBecomeNull() {
        val csv = CsvLedger.encode(
            BackupContent(bills = listOf(bill(product = null, quantity = null))),
            zone,
        )
        val decoded = CsvLedger.decode(csv, zone).bills.single()
        assertNull(decoded.productName)
        assertNull(decoded.quantity)
    }

    @Test
    fun bomAndCrlfAreTolerated() {
        val csv = "\uFEFF" + CsvLedger.encode(BackupContent(bills = listOf(bill())), zone).replace("\n", "\r\n")
        assertEquals(1, CsvLedger.decode(csv, zone).bills.size)
    }

    /** 老版本（v1，10 列、没有记录类型列）导出的备份还能读 */
    @Test
    fun legacyFile_isImportedAsBills() {
        val legacy = "收入/支出,金额(元),分类,账户,记账时间,录入时间,商品,数量,备注,单据\r\n" +
            "支出,88.80,进货,现金,2026-11-20 09:30:00,2026-11-20 09:31:00,大米,5,老账,\r\n"
        val content = CsvLedger.decode(legacy, zone)
        assertEquals(1, content.bills.size)
        val b = content.bills.single()
        assertEquals(8880L, b.amountCents)
        assertEquals("大米", b.productName)
        assertEquals(5, b.quantity)
        assertTrue(content.debts.isEmpty())
    }

    @Test
    fun escape_onlyWhenNeeded() {
        assertEquals("可乐", CsvLedger.escape("可乐"))
        assertEquals("\"a,b\"", CsvLedger.escape("a,b"))
        assertEquals("\"he said \"\"hi\"\"\"", CsvLedger.escape("he said \"hi\""))
    }
}