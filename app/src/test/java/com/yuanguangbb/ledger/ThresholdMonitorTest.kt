package com.yuanguangbb.ledger

import com.yuanguangbb.ledger.domain.quarterOf
import com.yuanguangbb.ledger.domain.quarterRange
import com.yuanguangbb.ledger.domain.tax.ThresholdLevel
import com.yuanguangbb.ledger.domain.tax.ThresholdMonitor
import com.yuanguangbb.ledger.domain.tax.ThresholdRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ThresholdMonitorTest {
    // 测试用规则：1000 元（100_000 分）
    private val rule = ThresholdRule("测试", limitCents = 100_000L)

    @Test
    fun belowWarnLine_isSafe() {
        assertEquals(ThresholdLevel.SAFE, ThresholdMonitor.check(79_999, rule).level)
    }

    @Test
    fun atWarnLine_isNear() {
        assertEquals(ThresholdLevel.NEAR, ThresholdMonitor.check(80_000, rule).level)
    }

    @Test
    fun justBelowLimit_isNear() {
        assertEquals(ThresholdLevel.NEAR, ThresholdMonitor.check(99_999, rule).level)
    }

    @Test
    fun equalToLimit_isOver() {
        assertEquals(ThresholdLevel.OVER, ThresholdMonitor.check(100_000, rule).level)
    }

    @Test
    fun aboveLimit_isOver_andRemainingIsZero() {
        val s = ThresholdMonitor.check(150_000, rule)
        assertEquals(ThresholdLevel.OVER, s.level)
        assertEquals(0L, s.remainingCents)
    }

    @Test
    fun remaining_isLimitMinusIncome() {
        assertEquals(40_000L, ThresholdMonitor.check(60_000, rule).remainingCents)
    }

    @Test
    fun defaultQuarterlyRule_is300kYuan() {
        assertEquals(30_000_000L, ThresholdMonitor.QUARTERLY_2026_2027.limitCents)
    }

    @Test
    fun quarterOf_mapsMonths() {
        assertEquals(1, quarterOf(LocalDate.of(2026, 3, 31)))
        assertEquals(2, quarterOf(LocalDate.of(2026, 4, 1)))
        assertEquals(4, quarterOf(LocalDate.of(2026, 11, 25)))
    }

    @Test
    fun quarterRange_coversThreeMonths() {
        val zone = ZoneId.of("Asia/Shanghai")
        val (from, to) = quarterRange(LocalDate.of(2026, 11, 25), zone)
        val expectedFrom = LocalDate.of(2026, 10, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val expectedTo = LocalDate.of(2027, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expectedFrom, from)
        assertEquals(expectedTo, to)
        assertTrue(from < to)
    }
}
