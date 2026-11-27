package com.yuanguangbb.ledger.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuanguangbb.ledger.data.db.AccountEntity
import com.yuanguangbb.ledger.data.db.CategoryEntity
import com.yuanguangbb.ledger.data.db.ProductEntity
import com.yuanguangbb.ledger.data.db.TransactionEntity
import com.yuanguangbb.ledger.data.repository.LedgerRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddViewModel(private val repository: LedgerRepository) : ViewModel() {

    val expenseCategories: StateFlow<List<CategoryEntity>> = repository.categories(false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val incomeCategories: StateFlow<List<CategoryEntity>> = repository.categories(true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> = repository.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val products: StateFlow<List<ProductEntity>> = repository.products
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 不为空表示在改一笔老账 */
    private val _editing = MutableStateFlow<TransactionEntity?>(null)
    val editing: StateFlow<TransactionEntity?> = _editing.asStateFlow()

    fun load(id: Long) {
        if (_editing.value?.id == id) return
        viewModelScope.launch { _editing.value = repository.getTransaction(id) }
    }

    fun save(
        isIncome: Boolean,
        amountCents: Long,
        categoryId: Long,
        accountId: Long,
        note: String,
        productName: String = "",
        quantity: Int? = null,
        receiptPath: String? = null,
        /** 记账日期（补记或修改时用所选日期），默认现在 */
        timeMillis: Long = System.currentTimeMillis(),
    ) {
        val existing = _editing.value
        viewModelScope.launch {
            // 页面关闭会清掉 ViewModel，这里保证「建商品 + 存账」两步一定做完
            withContext(NonCancellable) {
                val productId = repository.getOrCreateProductId(productName)
                if (existing != null) {
                    // 改账：保留原 id、录入时间和回收站状态
                    repository.updateTransaction(
                        existing.copy(
                            isIncome = isIncome,
                            amountCents = amountCents,
                            categoryId = categoryId,
                            accountId = accountId,
                            timeMillis = timeMillis,
                            note = note.trim(),
                            productId = productId,
                            quantity = if (productId != null) quantity else null,
                            receiptPath = receiptPath,
                        )
                    )
                } else {
                    repository.addTransaction(
                        isIncome = isIncome,
                        amountCents = amountCents,
                        categoryId = categoryId,
                        accountId = accountId,
                        timeMillis = timeMillis,
                        note = note,
                        productId = productId,
                        quantity = quantity,
                        receiptPath = receiptPath,
                    )
                }
            }
        }
    }
}