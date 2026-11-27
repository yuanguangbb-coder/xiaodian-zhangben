package com.example.ledger.domain.product

/** 一天的毫秒数，只用在这里，避免魔法数字散落 */
private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

/** 一个商品的流水汇总：全部历史 + 最近卖出/进货时间 + 近 30 天窗口内的销量。金额单位：分。 */
data class ProductActivity(
    val productId: Long,
    val name: String,
    val soldQty: Long,
    val revenueCents: Long,
    val purchasedQty: Long,
    val purchaseCents: Long,
    /** 最近一次卖出是哪天；从没卖过为 null */
    val lastSoldAtMillis: Long? = null,
    /** 最近一次进货是哪天；从没进过为 null */
    val lastPurchasedAtMillis: Long? = null,
    /** 近 30 天卖出件数 */
    val soldQtyInWindow: Long = 0,
    /** 近 30 天销售额 */
    val revenueCentsInWindow: Long = 0,
)

/**
 * 一个商品的提醒信息。
 * @param stockQty 库存估算 = 进货 − 卖出，小于 0 按 0
 * @param idleDays 距最近一次卖出多少天；从没卖过则按进货日期算
 * @param soldQtyInWindow 近 30 天卖出件数
 * @param slowMoving 滞销：还有库存，且超过 30 天没卖出
 * @param hot 畅销：近 30 天卖得最多的前 3 名
 * @param stockValueCents 压货金额 = 库存估算 × 平均进价；进货记录缺失时为 null（界面显示「—」）
 */
data class ProductInsight(
    val productId: Long,
    val name: String,
    val stockQty: Long,
    val idleDays: Long,
    val soldQtyInWindow: Long,
    val slowMoving: Boolean,
    val hot: Boolean,
    val stockValueCents: Long?,
)

/** 首页提醒卡用的数据：畅销、滞销各最多 3 条。 */
data class ProductAlerts(
    val hot: List<ProductInsight> = emptyList(),
    val slow: List<ProductInsight> = emptyList(),
) {
    /** 两边都没有时整张卡片不显示 */
    val isEmpty: Boolean get() = hot.isEmpty() && slow.isEmpty()
}

/**
 * 滞销/畅销判断。纯 Kotlin，不依赖 Android，方便单元测试。
 *
 * 规则：
 * - 库存估算 = 全部进货数量 − 全部卖出数量，小于 0 按 0。
 * - 滞销：库存估算 > 0，且最近一次卖出距今超过 [SLOW_MOVING_DAYS] 天；
 *   从没卖过的，按最近一次进货算。
 * - 畅销：近 [HOT_WINDOW_DAYS] 天卖出数量最多的前 [ALERT_LIMIT] 个（销量必须 > 0）。
 */
object ProductInsights {

    /** 多久没卖出算滞销。以后要做成可设置，改这里就行。 */
    const val SLOW_MOVING_DAYS = 30L

    /** 畅销看最近多少天的销量 */
    const val HOT_WINDOW_DAYS = 30L

    /** 首页提醒卡最多显示几条 */
    const val ALERT_LIMIT = 3

    /** 近 30 天窗口的毫秒数，DAO 查询时用 */
    fun windowMillis(days: Long = HOT_WINDOW_DAYS): Long = days * DAY_MILLIS

    fun analyze(
        activities: List<ProductActivity>,
        nowMillis: Long,
        hotLimit: Int = ALERT_LIMIT,
    ): List<ProductInsight> {
        val hotIds = hotRanking(activities, hotLimit)
        return activities.map { toInsight(it, nowMillis, hot = it.productId in hotIds) }
    }

    fun alerts(
        activities: List<ProductActivity>,
        nowMillis: Long,
        limit: Int = ALERT_LIMIT,
    ): ProductAlerts {
        val insights = analyze(activities, nowMillis, hotLimit = limit)
        val hotOrder = hotRanking(activities, limit).withIndex().associate { it.value to it.index }
        val hot = insights.filter { it.hot }.sortedBy { hotOrder[it.productId] ?: Int.MAX_VALUE }
        // 压得越久、压的钱越多，排前面
        val slow = insights.filter { it.slowMoving }
            .sortedWith(
                compareByDescending<ProductInsight> { it.idleDays }
                    .thenByDescending { it.stockValueCents ?: -1L }
                    .thenBy { it.productId }
            )
            .take(limit)
        return ProductAlerts(hot = hot, slow = slow)
    }

    /** 近 30 天卖出件数 > 0 的商品，按件数从多到少；件数相同看销售额，再相同看 id，保证顺序稳定。 */
    private fun hotRanking(activities: List<ProductActivity>, limit: Int): List<Long> =
        activities.filter { it.soldQtyInWindow > 0 }
            .sortedWith(
                compareByDescending<ProductActivity> { it.soldQtyInWindow }
                    .thenByDescending { it.revenueCentsInWindow }
                    .thenBy { it.productId }
            )
            .take(limit)
            .map { it.productId }

    private fun toInsight(a: ProductActivity, nowMillis: Long, hot: Boolean): ProductInsight {
        val stock = (a.purchasedQty - a.soldQty).coerceAtLeast(0L)
        // 没卖过就看最近一次进货是什么时候；两个都没有说明账上没这件货的流水
        val lastMovedAt = a.lastSoldAtMillis ?: a.lastPurchasedAtMillis
        val idleDays = lastMovedAt?.let {
            ((nowMillis - it) / DAY_MILLIS).coerceAtLeast(0L)
        } ?: 0L
        // 拿不到进价时算不出压货金额，就返回 null，界面显示「—」
        val stockValueCents = if (a.purchasedQty > 0 && a.purchaseCents > 0) {
            a.purchaseCents * stock / a.purchasedQty
        } else {
            null
        }
        return ProductInsight(
            productId = a.productId,
            name = a.name,
            stockQty = stock,
            idleDays = idleDays,
            soldQtyInWindow = a.soldQtyInWindow,
            slowMoving = stock > 0 && idleDays > SLOW_MOVING_DAYS,
            hot = hot,
            stockValueCents = stockValueCents,
        )
    }
}