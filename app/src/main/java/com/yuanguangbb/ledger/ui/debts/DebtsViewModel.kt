package com.yuanguangbb.ledger.ui.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuanguangbb.ledger.data.db.CustomerWithDebt
import com.yuanguangbb.ledger.data.db.DebtEntity
import com.yuanguangbb.ledger.data.repository.LedgerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DebtsViewModel(private val repository: LedgerRepository) : ViewModel() {

    val customers: StateFlow<List<CustomerWithDebt>> = repository.customers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun debtsOf(customerId: Long): Flow<List<DebtEntity>> = repository.debts(customerId)

    fun addCustomer(name: String, phone: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.addCustomer(name, phone)
            onDone()
        }
    }

    fun deleteCustomer(id: Long) {
        viewModelScope.launch { repository.deleteCustomer(id) }
    }

    fun addDebt(
        customerId: Long,
        customerName: String,
        amountCents: Long,
        timeMillis: Long,
        note: String,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            repository.addDebt(
                customerId = customerId,
                customerName = customerName,
                amountCents = amountCents,
                timeMillis = timeMillis,
                note = note,
            )
            onDone()
        }
    }

    fun settle(id: Long) {
        viewModelScope.launch { repository.settleDebt(id) }
    }

    fun unsettle(id: Long) {
        viewModelScope.launch { repository.unsettleDebt(id) }
    }

    fun deleteDebt(id: Long) {
        viewModelScope.launch { repository.deleteDebt(id) }
    }
}