package com.expensetracker.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.data.db.ContactEntity
import com.expensetracker.app.data.db.LedgerAccountCategory
import com.expensetracker.app.data.db.LedgerAccountEntity
import com.expensetracker.app.data.db.TransactionEntity
import com.expensetracker.app.diagnostics.CrashLog
import com.expensetracker.core.model.Category
import com.expensetracker.core.model.TransactionKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

class TransactionListViewModel(private val app: ExpenseTrackerApp) : ViewModel() {

    // Paged (not a plain StateFlow<List<...>>) — this is the fix for the crash-on-launch: an
    // unbounded query over every transaction ever captured got re-collected into memory on every
    // DB write, and blew up once enough messages (including the promotional-message flood from
    // the old parser) had piled up. Paging loads ~20 rows at a time and only more as you scroll.
    val allTransactions: Flow<PagingData<TransactionEntity>> =
        Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
            app.database.transactionDao().observeAllPaged()
        }.flow.cachedIn(viewModelScope)

    val needsReview: Flow<PagingData<TransactionEntity>> =
        Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
            app.database.transactionDao().observeNeedsReviewPaged()
        }.flow.cachedIn(viewModelScope)

    val totalCount: StateFlow<Int> =
        app.database.transactionDao().observeTotalCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val needsReviewCount: StateFlow<Int> =
        app.database.transactionDao().observeNeedsReviewCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun correctCategory(transaction: TransactionEntity, category: Category) {
        viewModelScope.launch {
            runCatching { app.transactionRepository.correctCategory(transaction, category) }
                .onFailure { CrashLog.record(app, "correctCategory", it) }
        }
    }

    val contacts: StateFlow<List<ContactEntity>> =
        app.database.contactDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loans: StateFlow<List<LedgerAccountEntity>> =
        app.database.ledgerAccountDao().observeAll()
            .map { it.filter { a -> a.category == LedgerAccountCategory.LOAN } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun correctKind(transaction: TransactionEntity, kind: TransactionKind, contactId: String?, loanId: String?) {
        viewModelScope.launch {
            runCatching { app.transactionRepository.correctKind(app, transaction, kind, contactId, loanId) }
                .onFailure { CrashLog.record(app, "correctKind", it) }
        }
    }

    fun setNote(transaction: TransactionEntity, note: String?) {
        viewModelScope.launch {
            runCatching { app.transactionRepository.setNote(transaction, note) }
                .onFailure { CrashLog.record(app, "setNote", it) }
        }
    }

    fun markReviewed(transaction: TransactionEntity) {
        viewModelScope.launch {
            runCatching { app.transactionRepository.markReviewed(transaction) }
                .onFailure { CrashLog.record(app, "markReviewed", it) }
        }
    }

    private val _deepLinkedTransaction = MutableStateFlow<TransactionEntity?>(null)

    /** Set when opened via a notification tap — looked up directly by id rather than searched
     * for within the (now paged, possibly-not-yet-loaded-that-far) transaction list. */
    val deepLinkedTransaction: StateFlow<TransactionEntity?> = _deepLinkedTransaction.asStateFlow()

    fun loadDeepLinkedTransaction(id: Long) {
        viewModelScope.launch {
            runCatching { app.database.transactionDao().getById(id) }
                .onSuccess { _deepLinkedTransaction.value = it }
                .onFailure { CrashLog.record(app, "loadDeepLinkedTransaction", it) }
        }
    }

    fun clearDeepLinkedTransaction() {
        _deepLinkedTransaction.value = null
    }
}
