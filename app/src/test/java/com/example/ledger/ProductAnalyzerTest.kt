package com.example.ledger

import com.example.ledger.domain.product.ProductAnalyzer
import com.example.ledger.domain.product.ProductStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductAnalyzerTest {

    private fun stat(
        id: Long,
        name: String,
        sold: Long,
        revenue: Long,
        purchased: Long,
        purchaseCost: Long,
    ) = ProductStat(id, name, sold, revenue, purchased, purchaseCost)

    @Test
    fun normalCase_profitAndMargin() {
        // 进 100 件共 50000 分（每件 500 分），卖 10 件收入 10000 分
        // 成本 = 50000 * 10 / 100 = 5000，毛利 5000，毛利率 50%
        val r = ProductAnalyzer.analyze(listOf(stat(1, "可乐", 10, 10_000, 100, 50_000))).single()
        assertEquals(5_000L, r.costCents)
        assertEquals(5_000L, r.grossProfitCents)
        assertEquals(5_000, r.marginBp)
        assertFalse(r.costIncomplete)
    }

    @Test
    fun noPurchaseRecord_profitUnknown() {
        val r = ProductAnalyzer.analyze(listOf(stat(1, "香烟", 5, 9_999, 0, 0))).single()
        assertNull(r.costCents)
        assertNull(r.grossProfitCents)
        assertNull(r.marginBp)
    }

    @Test
    fun noSales_profitUnknown() {
        val r = ProductAnalyzer.analyze(listOf(stat(1, "大米", 0, 0, 20, 8_000))).single()
        assertNull(r.grossProfitCents)
        assertFalse(r.costIncomplete)
    }

    @Test
    fun soldMoreThanPurchased_marksIncomplete() {
        val r = ProductAnalyzer.analyze(listOf(stat(1, "矿泉水", 20, 4_000, 10, 1_000))).single()
        assertTrue(r.costIncomplete)
        // 平均进价 100 分，成本 = 1000 * 20 / 10 = 2000
        assertEquals(2_000L, r.costCents)
    }

    @Test
    fun lossMaking_negativeProfit() {
        // 成本 5000，收入 3000，毛利 -2000，毛利率 -6666（向零取整）
        val r = ProductAnalyzer.analyze(listOf(stat(1, "亏本货", 10, 3_000, 100, 50_000))).single()
        assertEquals(-2_000L, r.grossProfitCents)
        assertEquals(-6_666, r.marginBp)
    }

    @Test
    fun sorting_knownProfitFirst_thenUnknownByRevenue() {
        val result = ProductAnalyzer.analyze(
            listOf(
                stat(1, "A", 1, 1_100, 1, 1_000),   // 毛利 100
                stat(2, "B", 1, 1_500, 1, 1_000),   // 毛利 500
                stat(3, "C", 5, 9_999, 0, 0),       // 算不出，销售额最高
                stat(4, "D", 0, 0, 3, 300),         // 算不出，销售额 0
            )
        )
        assertEquals(listOf("B", "A", "C", "D"), result.map { it.name })
    }

    @Test
    fun emptyList_isEmpty() {
        assertTrue(ProductAnalyzer.analyze(emptyList()).isEmpty())
    }
}
