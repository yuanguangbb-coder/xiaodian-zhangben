package com.example.ledger.domain.report

/** 一个分类在某个区间的合计 */
data class CategoryTotal(val name: String, val cents: Long)

/** 某个月的利润（收入 − 支出） */
data class MonthPoint(val year: Int, val month: Int, val profitCents: Long) {
    val label: String get() = "${month}月"
}

/** 报表用的纯计算，不依赖 Android。 */
object ReportMath {

    /** 取金额最大的前 [limit] 个分类；金额相同保持原来的顺序（稳定排序） */
    fun topTotals(sums: List<CategoryTotal>, limit: Int = 5): List<CategoryTotal> =
        sums.filter { it.cents > 0 }
            .sortedByDescending { it.cents }
            .take(limit)

    /** 占比百分比，0..100；总额为 0 时返回 0 */
    fun sharePercent(part: Long, total: Long): Int =
        if (total <= 0L) 0 else ((part * 100) / total).toInt().coerceIn(0, 100)

    /**
     * 最近 [count] 个月（包含 [endYear]-[endMonth] 这个月）的利润，按时间正序返回。
     * 没有数据的月份补 0，保证柱状图不断档。
     */
    fun recentMonths(
        profitByMonth: Map<String, Long>,
        endYear: Int,
        endMonth: Int,
        count: Int = 6,
    ): List<MonthPoint> {
        if (count <= 0) return emptyList()
        val points = ArrayList<MonthPoint>(count)
        var year = endYear
        var month = endMonth
        repeat(count) {
            val key = "%04d-%02d".format(year, month)
            points.add(MonthPoint(year = year, month = month, profitCents = profitByMonth[key] ?: 0L))
            month--
            if (month == 0) {
                month = 12
                year--
            }
        }
        return points.reversed()
    }
}