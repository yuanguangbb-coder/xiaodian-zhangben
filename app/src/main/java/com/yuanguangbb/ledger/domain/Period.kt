package com.yuanguangbb.ledger.domain

import java.time.LocalDate
import java.time.ZoneId

/** 日期所在季度的 [起, 止) 毫秒时间戳。季度：1-3、4-6、7-9、10-12 月。 */
fun quarterRange(
    date: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): Pair<Long, Long> {
    val firstMonth = ((date.monthValue - 1) / 3) * 3 + 1
    val first = LocalDate.of(date.year, firstMonth, 1)
    val from = first.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = first.plusMonths(3).atStartOfDay(zone).toInstant().toEpochMilli()
    return from to to
}

/** 当前日期所在的季度序号 1..4 */
fun quarterOf(date: LocalDate = LocalDate.now()): Int = (date.monthValue - 1) / 3 + 1

/**
 * 某个月的 [起, 止) 毫秒时间戳。
 * @param monthsAgo 0 = 本月，1 = 上月，2 = 上上月，以此类推
 */
fun monthRange(
    monthsAgo: Long = 0,
    date: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): Pair<Long, Long> {
    val first = date.withDayOfMonth(1).minusMonths(monthsAgo)
    val from = first.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = first.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return from to to
}
