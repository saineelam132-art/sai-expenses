package com.expensetracker.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.data.db.AccountEntity
import com.expensetracker.app.data.db.SectorSpend
import com.expensetracker.core.model.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class SectorTrend(val category: Category, val thisMonth: Double, val lastMonth: Double) {
    val jumped: Boolean get() = lastMonth > 0 && thisMonth > lastMonth * 1.3 // >30% jump vs last month
}

data class RecurringPayment(val merchant: String, val amount: Double, val occurrences: Int)

data class DashboardUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val totalBalance: Double = 0.0,
    val currentMonthSpend: List<Pair<Category, Double>> = emptyList(),
    val trends: List<SectorTrend> = emptyList(),
    val recurringPayments: List<RecurringPayment> = emptyList(),
)

class DashboardViewModel(private val app: ExpenseTrackerApp) : ViewModel() {

    // Must be declared (and thus initialized) before `uiState` below — buildState() reads this
    // property while constructing uiState's Flow, and Kotlin initializes properties in source
    // order. Declaring it after uiState would leave its backing field null at that point (a
    // silent runtime NPE inside combine(), not a compile error).
    private val recurring = MutableStateFlow<List<RecurringPayment>>(emptyList())

    val uiState: StateFlow<DashboardUiState> = buildState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    init {
        viewModelScope.launch { loadRecurringPayments() }
    }

    private suspend fun loadRecurringPayments() {
        val repeated = app.database.transactionDao().getRepeatedMerchantAmounts()
        recurring.value = repeated.map {
            RecurringPayment(it.merchant, it.amountText.toDoubleOrNull() ?: 0.0, it.occurrences)
        }
    }

    private fun buildState() = combine(
        app.database.accountDao().observeAll(),
        currentMonthSectorFlow(),
        lastMonthSectorFlow(),
        recurring,
    ) { accounts, thisMonth, lastMonth, recurringPayments ->
        val lastMonthMap = lastMonth.associate { it.category to it.total }
        val trends = thisMonth.map { SectorTrend(it.category, it.total, lastMonthMap[it.category] ?: 0.0) }
        DashboardUiState(
            accounts = accounts,
            totalBalance = accounts.sumOf { it.latestBalance?.toDouble() ?: 0.0 },
            currentMonthSpend = thisMonth.map { it.category to it.total },
            trends = trends.filter { it.jumped },
            recurringPayments = recurringPayments,
        )
    }

    private fun currentMonthSectorFlow() = run {
        val (start, end) = monthRange(LocalDate.now())
        app.database.transactionDao().observeSectorSpend(start, end)
    }

    private fun lastMonthSectorFlow() = run {
        val (start, end) = monthRange(LocalDate.now().minusMonths(1))
        app.database.transactionDao().observeSectorSpend(start, end)
    }

    private fun monthRange(anyDayInMonth: LocalDate): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val start = anyDayInMonth.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = anyDayInMonth.withDayOfMonth(anyDayInMonth.lengthOfMonth()).atTime(23, 59, 59)
            .atZone(zone).toInstant().toEpochMilli()
        return start to end
    }
}
