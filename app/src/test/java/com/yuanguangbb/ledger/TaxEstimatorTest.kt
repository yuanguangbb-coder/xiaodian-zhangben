package com.yuanguangbb.ledger

import com.yuanguangbb.ledger.domain.tax.TaxEstimator
import com.yuanguangbb.ledger.domain.tax.TaxRule
import org.junit.Assert.assertEquals
import org.junit.Test

class TaxEstimatorTest {
    // 测试用的假规则，数字与真实政策无关
    private val rule = TaxRule(name = "测试规则", thresholdCents = 100_000, rateBasisPoints = 300)

    @Test
    fun belowThreshold_isZero() {
        assertEquals(0L, TaxEstimator.estimate(50_000, rule))
    }

    @Test
    fun equalToThreshold_isZero() {
        assertEquals(0L, TaxEstimator.estimate(100_000, rule))
    }

    @Test
    fun aboveThreshold_taxesOnlyTheExcess() {
        // (200_000 - 100_000) * 3% = 3_000
        assertEquals(3_000L, TaxEstimator.estimate(200_000, rule))
    }
}
