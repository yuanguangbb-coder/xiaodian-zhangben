package com.yuanguangbb.ledger.domain.product

/** 一个商品的原始汇总（全部历史）。金额单位：分。 */
data class ProductStat(
    val productId: Long,
    val name: String,
    val soldQty: Long,
    val revenueCents: Long,
    val purchasedQty: Long,
    val purchaseCents: Long,
)

/**
 * 分析结果。
 * @param costCents 已卖出部分的进货成本；缺进货记录或没卖出时为 null
 * @param marginBp 毛利率，万分比（5000 = 50%）；算不出时为 null
 * @param costIncomplete 卖出数量多于进货数量：说明进货可能没记全，毛利仅供参考
 */
data class ProductProfit(
    val productId: Long,
    val name: String,
    val soldQty: Long,
    val revenueCents: Long,
    val costCents: Long?,
    val grossProfitCents: Long?,
    val marginBp: Int?,
    val costIncomplete: Boolean,
)

/** 纯 Kotlin，无 Android 依赖，方便写单元测试。 */
object ProductAnalyzer {

    /**
     * 毛利 = 销售额 − 卖出数量 × 平均进价
     * 平均进价 = 进货总金额 ÷ 进货总数量（简化：不区分批次）
     * 排序：能算出毛利的排前面，按毛利从高到低；算不出的排后面，按销售额从高到低。
     */
    fun analyze(stats: List<ProductStat>): List<ProductProfit> =
        stats.map { toProfit(it) }.sortedWith(
            compareByDescending<ProductProfit> { it.grossProfitCents != null }
                .thenByDescending { it.grossProfitCents ?: Long.MIN_VALUE }
                .thenByDescending { it.revenueCents }
        )

    private fun toProfit(s: ProductStat): ProductProfit {
        val canCost = s.soldQty > 0 && s.purchasedQty > 0 && s.purchaseCents > 0
        val cost = if (canCost) s.purchaseCents * s.soldQty / s.purchasedQty else null
        val profit = cost?.let { s.revenueCents - it }
        val margin = if (profit != null && s.revenueCents > 0) {
            (profit * 10_000 / s.revenueCents).toInt()
        } else {
            null
        }
        return ProductProfit(
            productId = s.productId,
            name = s.name,
            soldQty = s.soldQty,
            revenueCents = s.revenueCents,
            costCents = cost,
            grossProfitCents = profit,
            marginBp = margin,
            costIncomplete = s.soldQty > 0 && s.soldQty > s.purchasedQty,
        )
    }
}
