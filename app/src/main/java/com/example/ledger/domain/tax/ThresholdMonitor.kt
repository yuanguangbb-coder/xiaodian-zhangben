package com.example.ledger.domain.tax

/**
 * 增值税起征点预警（小规模纳税人）。
 *
 * 起征点数字是可配置的，不写死在逻辑里。
 * 当前默认值来自财政部 税务总局公告2026年第10号：
 * 2026-01-01 至 2027-12-31，月销售额 10 万元 / 季度销售额 30 万元。
 * 政策到期或调整后，请更新 [ThresholdRule]，并以税务局最新规定为准。
 *
 * 注意：这里比较的是「账本里记录的收入」，不一定等于税法口径的「销售额」
 * （例如含税/不含税、免税项目、差额计算等），所以只作提醒，不作申报依据。
 */
data class ThresholdRule(
    val name: String,
    val limitCents: Long,
)

enum class ThresholdLevel { SAFE, NEAR, OVER }

data class ThresholdStatus(
    val incomeCents: Long,
    val limitCents: Long,
    val level: ThresholdLevel,
) {
    /** 距离起征点还剩多少（分），已超过则为 0 */
    val remainingCents: Long get() = (limitCents - incomeCents).coerceAtLeast(0)

    /** 已用比例，0.0 ~ 1.0+（超过起征点会大于 1） */
    val usedRatio: Double
        get() = if (limitCents <= 0) 0.0 else incomeCents.toDouble() / limitCents
}

object ThresholdMonitor {

    /** 达到起征点的这个比例开始提醒（默认 80%） */
    const val DEFAULT_WARN_PERCENT = 80

    val QUARTERLY_2026_2027 = ThresholdRule(name = "季度起征点（2026-2027）", limitCents = 30_000_000L)
    val MONTHLY_2026_2027 = ThresholdRule(name = "月度起征点（2026-2027）", limitCents = 10_000_000L)

    fun check(
        incomeCents: Long,
        rule: ThresholdRule,
        warnPercent: Int = DEFAULT_WARN_PERCENT,
    ): ThresholdStatus {
        val level = when {
            // 政策口径是「未达到起征点免征」，所以刚好等于起征点就视为已达到
            incomeCents >= rule.limitCents -> ThresholdLevel.OVER
            incomeCents * 100 >= rule.limitCents * warnPercent -> ThresholdLevel.NEAR
            else -> ThresholdLevel.SAFE
        }
        return ThresholdStatus(incomeCents, rule.limitCents, level)
    }
}
