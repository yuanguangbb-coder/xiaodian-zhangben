package com.yuanguangbb.ledger

import com.yuanguangbb.ledger.domain.formatCents
import com.yuanguangbb.ledger.domain.product.ProductActivity
import com.yuanguangbb.ledger.domain.product.ProductInsights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductInsightTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    private fun activity(
        id: Long = 1,
        name: String = "可乐",
        sold: Long = 0,
        revenue: Long = 0,
        purchased: Long = 0,
        purchaseCost: Long = 0,
        lastSold: Long? = null,
        lastPurchased: Long? = null,
        soldInWindow: Long = 0,
        revenueInWindow: Long = 0,
    ) = ProductActivity(
        productId = id,
        name = name,
        soldQty = sold,
        revenueCents = revenue,
        purchasedQty = purchased,
        purchaseCents = purchaseCost,
        lastSoldAtMillis = lastSold,
        lastPurchasedAtMillis = lastPurchased,
        soldQtyInWindow = soldInWindow,
        revenueCentsInWindow = revenueInWindow,
    )

    @Test
    fun hasStockAndLongNoSale_isSlowMoving() {
        // 进 10 件花 5000 分（每件 500），卖出 4 件，45 天前最后一次卖出
        val a = activity(
            sold = 4, revenue = 4_000, purchased = 10, purchaseCost = 5_000,
            lastSold = now - 45 * day, lastPurchased = now - 60 * day,
        )
        val r = ProductInsights.analyze(listOf(a), now).single()
        assertEquals(6L, r.stockQty)
        assertEquals(45L, r.idleDays)
        assertTrue(r.slowMoving)
        assertFalse(r.hot)
        // 压货 = 5000 * 6 / 10 = 3000 分
        assertEquals(3_000L, r.stockValueCents)
        assertEquals("30.00", formatCents(r.stockValueCents!!))
    }

    @Test
    fun zeroStock_isNotSlowMoving() {
        // 进 10 件全卖完，虽然 100 天没卖出，但没有库存就不算压货
        val a = activity(
            sold = 10, revenue = 12_000, purchased = 10, purchaseCost = 5_000,
            lastSold = now - 100 * day, lastPurchased = now - 120 * day,
        )
        val r = ProductInsights.analyze(listOf(a), now).single()
        assertEquals(0L, r.stockQty)
        assertFalse(r.slowMoving)
    }

    @Test
    fun soldMoreThanPurchased_stockClampedToZero() {
        // 进货没记全：卖出多于进货，库存按 0 算，不出现负数
        val a = activity(sold = 10, revenue = 12_000, purchased = 3, purchaseCost = 1_500)
        val r = ProductInsights.analyze(listOf(a), now).single()
        assertEquals(0L, r.stockQty)
        assertFalse(r.slowMoving)
    }

    @Test
    fun neverSold_usesPurchaseDateForIdleDays() {
        // 从没卖出，40 天前进的货，还有库存 → 滞销，天数按进货算
        val a = activity(
            purchased = 5, purchaseCost = 2_500,
            lastPurchased = now - 40 * day,
        )
        val r = ProductInsights.analyze(listOf(a), now).single()
        assertEquals(5L, r.stockQty)
        assertEquals(40L, r.idleDays)
        assertTrue(r.slowMoving)
    }

    @Test
    fun justPurchased_isNotSlowMoving() {
        // 2 天前进的货，还没卖过 → 不算滞销
        val a = activity(
            purchased = 5, purchaseCost = 2_500,
            lastPurchased = now - 2 * day,
        )
        val r = ProductInsights.analyze(listOf(a), now).single()
        assertEquals(2L, r.idleDays)
        assertFalse(r.slowMoving)
    }

    @Test
    fun exactly30Days_isNotSlowMoving_31DaysIs() {
        val at30 = ProductInsights.analyze(
            listOf(
                activity(
                    sold = 1, purchased = 5, purchaseCost = 2_500,
                    lastSold = now - 30 * day,
                )
            ),
            now,
        ).single()
        val at31 = ProductInsights.analyze(
            listOf(
                activity(
                    sold = 1, purchased = 5, purchaseCost = 2_500,
                    lastSold = now - 31 * day,
                )
            ),
            now,
        ).single()
        assertFalse(at30.slowMoving)
        assertTrue(at31.slowMoving)
    }

    @Test
    fun hotRanking_tiesBrokenByRevenueThenId() {
        val a = activity(id = 1, name = "A", soldInWindow = 10, revenueInWindow = 1_000)
        val b = activity(id = 2, name = "B", soldInWindow = 10, revenueInWindow = 2_000)
        val c = activity(id = 3, name = "C", soldInWindow = 12, revenueInWindow = 500)
        val d = activity(id = 4, name = "D", soldInWindow = 10, revenueInWindow = 2_000)
        val alerts = ProductInsights.alerts(listOf(a, b, c, d), now, limit = 3)
        // C 件数最多；B 和 D 件数、销售额都一样，按 id 排；A 销售额较低排最后（超出 3 条被截掉）
        assertEquals(listOf("C", "B", "D"), alerts.hot.map { it.name })
        assertEquals(3, alerts.hot.size)
        assertTrue(alerts.hot.all { it.soldQtyInWindow > 0 })
    }

    @Test
    fun hot_needsSalesInsideWindow() {
        // 全部销量都在 30 天窗口之外：窗口内销量为 0，不算畅销
        val a = activity(
            sold = 20, revenue = 20_000, purchased = 30, purchaseCost = 15_000,
            lastSold = now - 40 * day, soldInWindow = 0,
        )
        val alerts = ProductInsights.alerts(listOf(a), now)
        assertTrue(alerts.hot.isEmpty())
        assertEquals(1, alerts.slow.size)
    }

    @Test
    fun missingPurchaseRecord_stockValueIsNull() {
        // 只记了卖出、没记进货：算不出压货金额，显示「—」
        val noPurchase = activity(sold = 5, revenue = 6_000)
        val r1 = ProductInsights.analyze(listOf(noPurchase), now).single()
        assertEquals(0L, r1.stockQty)
        assertNull(r1.stockValueCents)

        // 有进货数量但没金额（脏数据）：同样算不出
        val noCost = activity(sold = 1, purchased = 5, purchaseCost = 0, lastSold = now - 40 * day)
        val r2 = ProductInsights.analyze(listOf(noCost), now).single()
        assertNull(r2.stockValueCents)
        assertTrue(r2.slowMoving)
    }

    @Test
    fun slowList_sortedByIdleDaysAndLimited() {
        val list = (1..5).map { i ->
            activity(
                id = i.toLong(), name = "P$i",
                sold = 1, purchased = 10, purchaseCost = 5_000,
                lastSold = now - (31L + i) * day,
            )
        }
        val alerts = ProductInsights.alerts(list, now, limit = 3)
        assertEquals(3, alerts.slow.size)
        // 压得最久的排最前
        assertEquals(listOf("P5", "P4", "P3"), alerts.slow.map { it.name })
    }

    @Test
    fun noData_alertsAreEmpty() {
        val alerts = ProductInsights.alerts(emptyList(), now)
        assertTrue(alerts.isEmpty)
        assertTrue(alerts.hot.isEmpty())
        assertTrue(alerts.slow.isEmpty())
    }

    @Test
    fun slowAndHot_areMutuallyExclusive() {
        // 刚卖过又有库存：不滞销；近 30 天有销量：畅销
        val a = activity(
            sold = 5, revenue = 6_000, purchased = 20, purchaseCost = 10_000,
            lastSold = now - 2 * day, soldInWindow = 5, revenueInWindow = 6_000,
        )
        val r = ProductInsights.analyze(listOf(a), now).single()
        assertTrue(r.hot)
        assertFalse(r.slowMoving)
        assertEquals(15L, r.stockQty)
        // 压货 = 10000 * 15 / 20 = 7500 分
        assertEquals(7_500L, r.stockValueCents)
    }
}