package com.expensetracker.app.ui.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.data.db.RecurringBillEntity
import com.expensetracker.app.data.db.RecurringBillStatus
import com.expensetracker.app.diagnostics.CrashLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** [lastChargedText] and [possiblyOverdue] come from staleness against the merchant's own
 * transaction history — never a guessed billing-cycle date (section 10: the mandate message
 * only tells us the mandate's own validity window, not a specific monthly charge day). */
data class BillRow(
    val bill: RecurringBillEntity,
    val lastChargedText: String,
    val possiblyOverdue: Boolean,
)

data class BillsUiState(val bills: List<BillRow> = emptyList())

class BillsViewModel(private val app: ExpenseTrackerApp) : ViewModel() {

    private val _uiState = MutableStateFlow(BillsUiState())
    val uiState: StateFlow<BillsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { collectState() }
    }

    private suspend fun collectState() {
        while (true) {
            try {
                app.database.recurringBillDao().observeAll().collect { bills ->
                    _uiState.value = BillsUiState(bills = bills.map { toRow(it) })
                }
                return
            } catch (c: CancellationException) {
                throw c
            } catch (e: Throwable) {
                CrashLog.record(app, "BillsViewModel.collectState", e)
                _uiState.value = BillsUiState()
                delay(1000)
            }
        }
    }

    private suspend fun toRow(bill: RecurringBillEntity): BillRow {
        val lastCharged = runCatching { app.database.transactionDao().getLastExpenseDate(bill.merchant) }.getOrNull()
        val overdue = bill.status == RecurringBillStatus.ACTIVE &&
            lastCharged != null &&
            ChronoUnit.DAYS.between(lastCharged, LocalDateTime.now()) > OVERDUE_GRACE_DAYS
        val lastChargedText = lastCharged?.let { "Last charged ${it.toLocalDate()}" } ?: "No charge seen yet"
        return BillRow(bill, lastChargedText, overdue)
    }

    companion object {
        // Not a guessed billing-cycle length — just a generous grace window (most bills are
        // monthly) before flagging "might be worth checking," per section 10's ask without
        // assuming a per-bill interval we don't actually know.
        private const val OVERDUE_GRACE_DAYS = 35L
    }
}
