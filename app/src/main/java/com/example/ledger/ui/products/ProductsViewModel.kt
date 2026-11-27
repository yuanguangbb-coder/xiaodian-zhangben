package com.example.ledger.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledger.data.repository.LedgerRepository
import com.example.ledger.domain.product.ProductAnalyzer
import com.example.ledger.domain.product.ProductInsight
import com.example.ledger.domain.product.ProductInsights
import com.example.ledger.domain.product.ProductProfit
import com.example.ledger.domain.product.ProductStat
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 商品页一行：毛利信息 + 滞销/畅销标签 */
data class ProductRowUi(
    val profit: ProductProfit,
    val insight: ProductInsight?,
)

class ProductsViewModel(private val repository: LedgerRepository) : ViewModel() {

    // 近 30 天窗口的起点；改窗口只改 ProductInsights.HOT_WINDOW_DAYS
    private val sinceMillis = System.currentTimeMillis() - ProductInsights.windowMillis()

    val items: StateFlow<List<ProductRowUi>> = combine(
        repository.productStats,
        repository.productActivity(sinceMillis),
    ) { rows, activities ->
        val profits = ProductAnalyzer.analyze(
            rows.map {
                ProductStat(
                    productId = it.productId,
                    name = it.name,
                    soldQty = it.soldQty,
                    revenueCents = it.revenueCents,
                    purchasedQty = it.purchasedQty,
                    purchaseCents = it.purchaseCents,
                )
            }
        )
        val insights = ProductInsights.analyze(activities, System.currentTimeMillis())
            .associateBy { it.productId }
        profits.map { ProductRowUi(profit = it, insight = insights[it.productId]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 一键删除全部商品：账单保留，只摘掉账单里的商品和数量。删完回调删除个数。 */
    fun deleteAll(onDeleted: (Int) -> Unit) {
        viewModelScope.launch {
            onDeleted(repository.clearAllProducts())
        }
    }
}