package com.example.ledger.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** 把用户输入的「元」转成「分」。非法或非正数返回 null。最多两位小数。 */
fun parseYuanToCents(input: String): Long? {
    val text = input.trim().replace(",", "").replace("，", "")
    if (text.isEmpty()) return null
    val value = text.toBigDecimalOrNull() ?: return null
    if (value.scale() > 2) return null
    if (value.signum() <= 0) return null
    return value.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).toLong()
}

/**
 * 界面上显示金额：带千分位。
 * 123456789 -> "1,234,567.89"
 */
fun formatCents(cents: Long): String =
    BigDecimal(cents).movePointLeft(2)
        .setScale(2, RoundingMode.UNNECESSARY)
        .let { displayFormat().format(it) }

/** 机器可读（CSV 备份/日志）用：不带千分位，123456789 -> "1234567.89" */
fun formatCentsPlain(cents: Long): String =
    BigDecimal(cents).movePointLeft(2)
        .setScale(2, RoundingMode.UNNECESSARY)
        .toPlainString()

private fun displayFormat(): DecimalFormat =
    DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

/** 当月 [起, 止) 的毫秒时间戳 */
fun currentMonthRange(zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
    val first = LocalDate.now(zone).withDayOfMonth(1)
    val from = first.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = first.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return from to to
}