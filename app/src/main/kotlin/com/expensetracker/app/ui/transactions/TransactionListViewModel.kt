package com.expensetracker.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.data.db.TransactionEntity
import com.expensetracker.core.model.Category
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionListViewModel(private val app: ExpenseTrackerApp) : ViewModel() {

    val allTransactions: StateFlow<List<TransactionEntity>> =
        app.database.transactionDao().observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val needsReview: StateFlow<List<TransactionEntity>> =
        app.database.transactionDao().observeNeedsReview()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun correctCategory(transaction: TransactionEntity, category: Category) {
        viewModelScope.launch { app.transactionRepository.correctCategory(transaction, category) }
    }
}
