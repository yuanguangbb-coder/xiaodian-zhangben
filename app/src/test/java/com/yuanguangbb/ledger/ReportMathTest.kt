package com.yuanguangbb.ledger

import com.yuanguangbb.ledger.domain.report.CategoryTotal
import com.yuanguangbb.ledger.domain.report.ReportMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportMathTest {

    @Test
    fun topTotals_sortsByAmountAndLimits() {
        val rows = listOf(
            CategoryTotal("房租", 50_000),
            CategoryTotal("进货", 120_000),
            CategoryTotal("水电", 5_000),
            CategoryTotal("零", 0),
            CategoryTotal("负", -100),
        )
        val top = ReportMath.topTotals(rows, limit = 2)
        assertEquals(listOf("进货", "房租"), top.map { it.name })
        assertTrue(top.all { it.cents > 0 })
    }

    @Test
    fun topTotals_sameAmount_keepsInputOrder() {
        val rows = listOf(
            CategoryTotal("乙", 100),
            CategoryTotal("甲", 100),
        )
        assertEquals(listOf("乙", "甲"), ReportMath.topTotals(rows).map { it.name })
    }

    @Test
    fun sharePercent_handlesZeroAndRounding() {
        assertEquals(0, ReportMath.sharePercent(100, 0))
        assertEquals(50, ReportMath.sharePercent(50, 100))
        assertEquals(33, ReportMath.sharePercent(1, 3))
        assertEquals(100, ReportMath.sharePercent(100, 100))
        assertEquals(0, ReportMath.sharePercent(0, 100))
    }

    @Test
    fun recentMonths_fillsMissingMonthsInOrder() {
        val profits = mapOf(
            "2026-11" to 10_000L,
            "2026-09" to -2_000L,
        )
        val points = ReportMath.recentMonths(profits, endYear = 2026, endMonth = 11, count = 4)
        assertEquals(
            listOf("2026-08", "2026-09", "2026-10", "2026-11"),
            points.map { "%04d-%02d".format(it.year, it.month) },
        )
        assertEquals(listOf(0L, -2_000L, 0L, 10_000L), points.map { it.profitCents })
    }

    @Test
    fun recentMonths_crossesYearBoundary() {
        val points = ReportMath.recentMonths(emptyMap(), endYear = 2027, endMonth = 2, count = 6)
        assertEquals(
            listOf(2026 to 9, 2026 to 10, 2026 to 11, 2026 to 12, 2027 to 1, 2027 to 2),
            points.map { it.year to it.month },
        )
        assertTrue(points.all { it.profitCents == 0L })
    }

    @Test
    fun recentMonths_emptyWhenCountIsZero() {
        assertTrue(ReportMath.recentMonths(emptyMap(), 2026, 11, count = 0).isEmpty())
    }
}