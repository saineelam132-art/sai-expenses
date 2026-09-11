package com.expensetracker.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.data.db.AccountEntity
import com.expensetracker.app.data.db.SectorSpend
import com.expensetracker.app.diagnostics.CrashLog
import com.expensetracker.core.model.Category
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val recurring = MutableStateFlow<List<RecurringPayment>>(emptyList())

    // A plain, always-live MutableStateFlow that this ViewModel pushes into from a
    // supervised collection loop below — never a `combine(...).catch{}.stateIn(...)` chain
    // collected directly by Compose. That pattern crashed on launch with a NullPointerException
    // from inside kotlinx.coroutines' combine() internals (surfacing through collectAsState())
    // that `.catch{}` did not intercept — collectState()'s explicit try/catch below is the fix:
    // no exception from buildState() can reach the UI layer, ever, regardless of its cause.
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { loadRecurringPayments() }
        viewModelScope.launch { collectState() }
    }

    private suspend fun collectState() {
        while (true) {
            try {
                buildState().collect { _uiState.value = it }
                return // upstream completed normally — nothing left to collect
            } catch (c: CancellationException) {
                throw c
            } catch (e: Throwable) {
                CrashLog.record(app, "DashboardViewModel.buildState", e)
                _uiState.value = DashboardUiState()
                delay(1000) // brief backoff, then retry in case the failure was transient
            }
        }
    }

    private suspend fun loadRecurringPayments() {
        runCatching {
            app.database.transactionDao().getRepeatedMerchantAmounts()
        }.onSuccess { repeated ->
            recurring.value = repeated.map {
                RecurringPayment(it.merchant, it.amountText.toDoubleOrNull() ?: 0.0, it.occurrences)
            }
        }.onFailure {
            CrashLog.record(app, "DashboardViewModel.loadRecurringPayments", it)
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
