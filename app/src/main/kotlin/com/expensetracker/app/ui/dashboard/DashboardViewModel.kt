package com.expensetracker.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.data.db.LedgerAccountCategory
import com.expensetracker.app.data.db.LedgerAccountEntity
import com.expensetracker.app.data.db.LedgerSide
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.diagnostics.CrashLog
import com.expensetracker.core.model.Category
import com.expensetracker.core.model.TransactionKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/**
 * Cash flow for the current month, across every account combined. Only Expense, Income and
 * Investment-Buy/Sell count: self-transfers, lending and loan disbursals move money between the
 * user's own pots rather than in or out of them, so counting them would inflate both sides.
 */
data class CashFlow(
    val incoming: Double = 0.0,
    val outgoing: Double = 0.0,
    val invested: Double = 0.0,
) {
    val left: Double get() = incoming - outgoing - invested
}

/** One sector's spend for the month. [label] is what the user sees — their own sector name when
 * they typed one, otherwise the built-in sector's name. */
data class SectorSlice(
    val label: String,
    val amount: Double,
    val category: Category,
    val isCustom: Boolean,
)

/** One bank account's displayed balance: its anchored figure plus the transactions since. */
data class AccountBalance(val label: String, val balance: Double)

data class DashboardUiState(
    val accounts: List<AccountBalance> = emptyList(),
    val totalBalance: Double = 0.0,
    val cashOnHand: BigDecimal = BigDecimal.ZERO,
    val cashFlow: CashFlow = CashFlow(),
    val spendingBySector: List<SectorSlice> = emptyList(),
) {
    val monthTotalSpend: Double get() = spendingBySector.sumOf { it.amount }
}

class DashboardViewModel(private val app: ExpenseTrackerApp) : ViewModel() {

    // Plain MutableStateFlow fed by a supervised loop — never a combine().catch{}.stateIn()
    // chain collected straight by Compose. An exception thrown while combine() restarts its
    // upstreams escapes .catch{} and reaches collectAsState() as a fatal crash; the explicit
    // try/catch below cannot be bypassed that way.
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { collectState() }
    }

    private suspend fun collectState() {
        while (true) {
            try {
                buildState().collect { _uiState.value = it }
                return
            } catch (c: CancellationException) {
                throw c
            } catch (e: Throwable) {
                CrashLog.record(app, "DashboardViewModel.buildState", e)
                _uiState.value = DashboardUiState()
                delay(1000)
            }
        }
    }

    fun setCashOnHand(amount: BigDecimal) {
        viewModelScope.launch {
            runCatching {
                val dao = app.database.ledgerAccountDao()
                val existing = dao.getCashBucket()
                dao.upsert(
                    (existing ?: LedgerAccountEntity(
                        id = "cash-on-hand",
                        name = "Cash on hand",
                        side = LedgerSide.ASSET,
                        category = LedgerAccountCategory.CASH,
                        balance = BigDecimal.ZERO,
                    )).copy(balance = amount),
                )
            }.onFailure { CrashLog.record(app, "DashboardViewModel.setCashOnHand", it) }
        }
    }

    private fun buildState(): Flow<DashboardUiState> {
        val (start, end) = monthRange(LocalDate.now())
        return combine(
            app.database.accountDao().observeAll(),
            app.database.transactionDao().observeSectorSpend(start, end),
            app.database.transactionDao().observeTotalsByKind(start, end),
            app.database.ledgerAccountDao().observeAll(),
            app.database.accountDao().observeBalanceDeltas(),
        ) { accounts, sectorSpend, kindTotals, ledgerAccounts, deltas ->
            val totals = kindTotals.associate { it.kind to it.total }
            val invested = (totals[TransactionKind.INVESTMENT_BUY] ?: 0.0) -
                (totals[TransactionKind.INVESTMENT_SELL] ?: 0.0)

            // Anchored bank figure plus everything since it — see AccountEntity for why the
            // reported balance alone isn't enough.
            val deltaByAccount = deltas.associate { it.accountId to it.delta }
            val accountBalances = accounts.map {
                AccountBalance(
                    label = "${it.bankLabel} ••${it.lastFourDigits ?: "----"}",
                    balance = (it.latestBalance?.toDouble() ?: 0.0) + (deltaByAccount[it.id] ?: 0.0),
                )
            }

            DashboardUiState(
                accounts = accountBalances,
                totalBalance = accountBalances.sumOf { it.balance },
                cashOnHand = ledgerAccounts
                    .firstOrNull { it.category == LedgerAccountCategory.CASH }?.balance ?: BigDecimal.ZERO,
                cashFlow = CashFlow(
                    incoming = totals[TransactionKind.INCOME] ?: 0.0,
                    outgoing = totals[TransactionKind.EXPENSE] ?: 0.0,
                    invested = invested,
                ),
                // Grouped by what's displayed, so two rows that show the same sector name (a
                // built-in and a custom one) never appear as separate slices.
                spendingBySector = sectorSpend
                    .filter { it.total > 0.0 }
                    .map {
                        val custom = it.customCategory?.takeIf { name -> name.isNotBlank() }
                        SectorSlice(
                            label = custom ?: it.category.displayName,
                            amount = it.total,
                            category = it.category,
                            isCustom = custom != null,
                        )
                    }
                    .groupBy { it.label }
                    .map { (label, slices) ->
                        slices.first().copy(label = label, amount = slices.sumOf { s -> s.amount })
                    }
                    .sortedByDescending { it.amount },
            )
        }
    }

    private fun monthRange(anyDayInMonth: LocalDate): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val start = anyDayInMonth.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = anyDayInMonth.withDayOfMonth(anyDayInMonth.lengthOfMonth()).atTime(23, 59, 59)
            .atZone(zone).toInstant().toEpochMilli()
        return start to end
    }
}
