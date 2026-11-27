package com.yuanguangbb.ledger.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuanguangbb.ledger.data.repository.LedgerRepository
import com.yuanguangbb.ledger.domain.monthRange
import com.yuanguangbb.ledger.domain.report.CategoryTotal
import com.yuanguangbb.ledger.domain.report.MonthPoint
import com.yuanguangbb.ledger.domain.report.ReportMath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val monthLabelFormatter = DateTimeFormatter.ofPattern("yyyy年M月")

data class ReportUi(
    /** 0 = 本月，1 = 上月，… */
    val offset: Long = 0,
    val monthLabel: String = "",
    val incomeCents: Long = 0,
    val expenseCents: Long = 0,
    val incomeSlices: List<CategoryTotal> = emptyList(),
    val expenseSlices: List<CategoryTotal> = emptyList(),
    val trend: List<MonthPoint> = emptyList(),
) {
    val profitCents: Long get() = incomeCents - expenseCents
    val hasData: Boolean get() = incomeCents > 0 || expenseCents > 0
}

class ReportViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val offset = MutableStateFlow(0L)

    fun previousMonth() {
        offset.value += 1
    }

    /** 回到本月 */
    fun nextMonth() {
        if (offset.value > 0) offset.value -= 1
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ReportUi> = offset.flatMapLatest { off ->
        val range = monthRange(off)
        val trendStart = monthRange(5).first
        val trendEnd = monthRange(0).second
        combine(
            repository.categorySums(range.first, range.second),
            repository.monthSums(trendStart, trendEnd),
        ) { cats, months ->
            val incomeRows = cats.filter { it.isIncome }
            val expenseRows = cats.filter { !it.isIncome }
            val incomeTotal = incomeRows.sumOf { it.totalCents }
            val expenseTotal = expenseRows.sumOf { it.totalCents }

            val profitByMonth = mutableMapOf<String, Long>()
            months.forEach { m ->
                val delta = if (m.isIncome) m.totalCents else -m.totalCents
                profitByMonth[m.month] = (profitByMonth[m.month] ?: 0L) + delta
            }
            val today = LocalDate.now()
            ReportUi(
                offset = off,
                monthLabel = Instant.ofEpochMilli(range.first)
                    .atZone(ZoneId.systemDefault()).format(monthLabelFormatter),
                incomeCents = incomeTotal,
                expenseCents = expenseTotal,
                incomeSlices = ReportMath.topTotals(incomeRows.map { CategoryTotal(it.name, it.totalCents) }),
                expenseSlices = ReportMath.topTotals(expenseRows.map { CategoryTotal(it.name, it.totalCents) }),
                trend = ReportMath.recentMonths(profitByMonth, today.year, today.monthValue),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUi())
}