package com.yuanguangbb.ledger

import com.yuanguangbb.ledger.domain.formatCents
import com.yuanguangbb.ledger.domain.formatCentsPlain
import com.yuanguangbb.ledger.domain.parseYuanToCents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun parse_validInputs() {
        assertEquals(1250L, parseYuanToCents("12.5"))
        assertEquals(1200L, parseYuanToCents("12"))
        assertEquals(5L, parseYuanToCents("0.05"))
    }

    @Test
    fun parse_invalidInputs() {
        assertNull(parseYuanToCents(""))
        assertNull(parseYuanToCents("abc"))
        assertNull(parseYuanToCents("0"))
        assertNull(parseYuanToCents("12.345"))
    }

    @Test
    fun format_cents() {
        assertEquals("0.00", formatCents(0))
        assertEquals("0.05", formatCents(5))
        assertEquals("123.45", formatCents(12345))
    }

    @Test
    fun format_cents_usesThousandsSeparatorForDisplay() {
        assertEquals("1,234.56", formatCents(123456))
        assertEquals("1,234,567.89", formatCents(123456789))
        assertEquals("30,000,000.00", formatCents(3_000_000_000))
    }

    @Test
    fun format_cents_plainStaysMachineReadable() {
        assertEquals("1234567.89", formatCentsPlain(123456789))
        assertEquals("0.05", formatCentsPlain(5))
        // 带千分位的展示串也要能被解析回去
        assertEquals(123456L, parseYuanToCents("1,234.56"))
    }
}
