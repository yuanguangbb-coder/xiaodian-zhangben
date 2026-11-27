package com.yuanguangbb.ledger.domain.tax

/**
 * 税额估算规则（可配置，不写死）。
 *
 * 税收政策会变化，且各地、各类型纳税人不同，
 * 具体数字请以国家税务总局或当地税务局最新规定为准。
 *
 * @param thresholdCents 周期内的免税额度（分）
 * @param rateBasisPoints 税率，万分比。例如 1% = 100
 */
data class TaxRule(
    val name: String,
    val thresholdCents: Long,
    val rateBasisPoints: Int,
)

/** 纯 Kotlin，无 Android 依赖，方便写单元测试。 */
object TaxEstimator {

    /** 简化估算：超出免税额度的部分 × 税率。仅供参考，不是申报依据。 */
    fun estimate(incomeCents: Long, rule: TaxRule): Long {
        val taxable = incomeCents - rule.thresholdCents
        if (taxable <= 0) return 0
        return taxable * rule.rateBasisPoints / 10_000
    }
}
