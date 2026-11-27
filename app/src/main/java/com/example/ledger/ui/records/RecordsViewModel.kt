package com.example.ledger.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledger.data.db.AccountEntity
import com.example.ledger.data.db.CategoryEntity
import com.example.ledger.data.db.ProductEntity
import com.example.ledger.data.db.TransactionWithNames
import com.example.ledger.data.repository.LedgerRepository
import com.example.ledger.data.repository.TransactionFilter
import com.example.ledger.domain.monthRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RecordsRange { ALL, THIS_MONTH, LAST_MONTH }

/** 账单页的筛选条件 */
data class RecordsFilterState(
    val range: RecordsRange = RecordsRange.ALL,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val productId: Long? = null,
    val keyword: String = "",
) {
    val isActive: Boolean
        get() = range != RecordsRange.ALL || categoryId != null || accountId != null ||
            productId != null || keyword.isNotBlank()

    fun toFilter(): TransactionFilter {
        val months: Pair<Long, Long>? = when (range) {
            RecordsRange.ALL -> null
            RecordsRange.THIS_MONTH -> monthRange(0)
            RecordsRange.LAST_MONTH -> monthRange(1)
        }
        return TransactionFilter(
            fromMillis = months?.first,
            toMillis = months?.second,
            categoryId = categoryId,
            accountId = accountId,
            productId = productId,
            keyword = keyword,
        )
    }
}

class RecordsViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _filter = MutableStateFlow(RecordsFilterState())
    val filter: StateFlow<RecordsFilterState> = _filter.asStateFlow()

    /** 当前最多取多少条，点「加载更多」每次加一页 */
    private val pageSize = 200
    private val visibleLimit = MutableStateFlow(pageSize)
    val limit: StateFlow<Int> = visibleLimit.asStateFlow()

    val allCategories: StateFlow<List<CategoryEntity>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> = repository.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val products: StateFlow<List<ProductEntity>> = repository.products
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val records: StateFlow<List<TransactionWithNames>> =
        combine(_filter.debounce(250), visibleLimit) { filter, limit -> filter to limit }
            .flatMapLatest { (filter, limit) ->
                repository.transactions(filter.toFilter(), limit)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadMore() {
        visibleLimit.value += pageSize
    }

    /** 改筛选条件时回到第一页，避免「筛完还显示上一页的量」 */
    private fun updateFilter(block: (RecordsFilterState) -> RecordsFilterState) {
        visibleLimit.value = pageSize
        _filter.update(block)
    }

    fun setRange(range: RecordsRange) = updateFilter { it.copy(range = range) }

    fun setCategory(id: Long?) = updateFilter { it.copy(categoryId = id) }

    fun setAccount(id: Long?) = updateFilter { it.copy(accountId = id) }

    fun setProduct(id: Long?) = updateFilter { it.copy(productId = id) }

    fun setKeyword(text: String) = updateFilter { it.copy(keyword = text) }

    fun resetFilter() {
        visibleLimit.value = pageSize
        _filter.value = RecordsFilterState()
    }

    /** 删除单笔：进回收站，30 天内可恢复 */
    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteTransaction(id) }
    }

    /** 一键把全部账单放进回收站，回调条数 */
    fun deleteAll(onDeleted: (Int) -> Unit) {
        viewModelScope.launch { onDeleted(repository.deleteAllTransactions()) }
    }
}