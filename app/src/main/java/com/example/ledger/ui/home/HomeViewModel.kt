package com.example.ledger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledger.data.repository.LedgerRepository
import com.example.ledger.data.settings.AppSettings
import com.example.ledger.domain.currentMonthRange
import com.example.ledger.domain.product.ProductAlerts
import com.example.ledger.domain.product.ProductInsights
import com.example.ledger.domain.quarterOf
import com.example.ledger.domain.quarterRange
import com.example.ledger.domain.tax.TaxEstimator
import com.example.ledger.domain.tax.TaxRule
import com.example.ledger.domain.tax.ThresholdMonitor
import com.example.ledger.domain.tax.ThresholdRule
import com.example.ledger.domain.tax.ThresholdStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class HomeState(
    val incomeCents: Long = 0,
    val expenseCents: Long = 0,
    val taxCents: Long = 0,
    /** 显示的月份和季度，跨月时随 refresh() 更新，不再是 ViewModel 创建时的值 */
    val month: Int = LocalDate.now().monthValue,
    val quarter: Int = quarterOf(),
    /** 本季度收入与起征点对比 */
    val quarterThreshold: ThresholdStatus =
        ThresholdMonitor.check(0, ThresholdMonitor.QUARTERLY_2026_2027),
    /** 畅销 / 滞销提醒，两边都空时整张卡片不显示 */
    val alerts: ProductAlerts = ProductAlerts(),
) {
    val profitCents: Long get() = incomeCents - expenseCents
}

class HomeViewModel(
    repository: LedgerRepository,
    settings: AppSettings,
) : ViewModel() {

    /** 每次回到首页就推一下，让「本月」「本季度」跟着日期走 */
    private val refreshTick = MutableStateFlow(System.currentTimeMillis())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeState> = refreshTick.flatMapLatest { now ->
        // 区间在每次刷新时重算，跨月/跨季度不会显示上个月的数
        val range = currentMonthRange()
        val quarter = quarterRange()
        val productWindowStart = now - ProductInsights.windowMillis()
        combine(
            repository.sum(isIncome = true, fromMillis = range.first, toMillis = range.second),
            repository.sum(isIncome = false, fromMillis = range.first, toMillis = range.second),
            repository.sum(isIncome = true, fromMillis = quarter.first, toMillis = quarter.second),
            repository.productActivity(productWindowStart),
            settings.tax,
        ) { income, expense, quarterIncome, activities, tax ->
            val today = LocalDate.now()
            HomeState(
                incomeCents = income,
                expenseCents = expense,
                // 规则来自设置页，改了立刻生效
                taxCents = TaxEstimator.estimate(
                    income,
                    TaxRule(
                        name = "自定义规则",
                        thresholdCents = tax.thresholdCents,
                        rateBasisPoints = tax.rateBasisPoints,
                    ),
                ),
                month = today.monthValue,
                quarter = quarterOf(today),
                quarterThreshold = ThresholdMonitor.check(
                    quarterIncome,
                    ThresholdRule(name = "自定义起征点", limitCents = tax.quarterLimitCents),
                ),
                alerts = ProductInsights.alerts(activities, System.currentTimeMillis()),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /** 页面回到前台时调用，重算本月/本季度 */
    fun refresh() {
        refreshTick.value = System.currentTimeMillis()
    }
}