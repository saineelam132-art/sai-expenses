package com.expensetracker.app.ui.balancesheet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.data.db.LedgerSide
import com.expensetracker.app.diagnostics.CrashLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class BalanceSheetRow(val name: String, val balance: Double)

data class BalanceSheetUiState(
    val assets: List<BalanceSheetRow> = emptyList(),
    val liabilities: List<BalanceSheetRow> = emptyList(),
    val netWorth: Double = 0.0,
)

class BalanceSheetViewModel(private val app: ExpenseTrackerApp) : ViewModel() {

    // A plain, always-live MutableStateFlow that this ViewModel pushes into from a supervised
    // collection loop, rather than exposing a `combine(...).catch{}.stateIn(...)` chain directly
    // to Compose — that pattern crashed on launch with a NullPointerException from inside
    // kotlinx.coroutines' combine() internals (surfacing through collectAsState()) that
    // `.catch{}` did not intercept. collectState()'s explicit try/catch is the fix: no exception
    // from buildState() can reach the UI layer, ever, regardless of its cause.
    private val _uiState = MutableStateFlow(BalanceSheetUiState())
    val uiState: StateFlow<BalanceSheetUiState> = _uiState.asStateFlow()

    init {
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
                CrashLog.record(app, "BalanceSheetViewModel", e)
                _uiState.value = BalanceSheetUiState()
                delay(1000) // brief backoff, then retry in case the failure was transient
            }
        }
    }

    private fun buildState() = combine(
        app.database.accountDao().observeAll(),
        app.database.ledgerAccountDao().observeAll(),
        app.database.accountDao().observeBalanceDeltas(),
    ) { bankAccounts, ledgerAccounts, deltas ->
        // A bank balance is the last figure the bank itself reported plus every transaction since
        // (see AccountEntity) — without that addition, an account whose bank omits balances on
        // debits would sit frozen here while its transactions piled up. Everything else comes
        // from LedgerPostingEngine's running totals. Net worth is always the live sum of both —
        // never a stored or overridable number — so it can't drift from the accounts beneath it.
        val deltaByAccount = deltas.associate { it.accountId to it.delta }
        val bankAssetRows = bankAccounts.map {
            val anchored = it.latestBalance?.toDouble() ?: 0.0
            BalanceSheetRow(
                "${it.bankLabel} ••${it.lastFourDigits ?: "----"}",
                anchored + (deltaByAccount[it.id] ?: 0.0),
            )
        }
        val ledgerAssetRows = ledgerAccounts.filter { it.side == LedgerSide.ASSET }
            .map { BalanceSheetRow(it.name, it.balance.toDouble()) }
        val liabilityRows = ledgerAccounts.filter { it.side == LedgerSide.LIABILITY }
            .map { BalanceSheetRow(it.name, it.balance.toDouble()) }

        val assets = bankAssetRows + ledgerAssetRows
        BalanceSheetUiState(
            assets = assets,
            liabilities = liabilityRows,
            netWorth = assets.sumOf { it.balance } - liabilityRows.sumOf { it.balance },
        )
    }
}
