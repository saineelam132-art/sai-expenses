package com.expensetracker.app.ui.balancesheet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.data.db.LedgerSide
import com.expensetracker.app.diagnostics.CrashLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class BalanceSheetRow(val name: String, val balance: Double)

data class BalanceSheetUiState(
    val assets: List<BalanceSheetRow> = emptyList(),
    val liabilities: List<BalanceSheetRow> = emptyList(),
    val netWorth: Double = 0.0,
)

class BalanceSheetViewModel(private val app: ExpenseTrackerApp) : ViewModel() {

    val uiState: StateFlow<BalanceSheetUiState> = combine(
        app.database.accountDao().observeAll(),
        app.database.ledgerAccountDao().observeAll(),
    ) { bankAccounts, ledgerAccounts ->
        // Bank balances are SMS ground truth; everything else comes from LedgerPostingEngine's
        // running totals. Net worth is always the live sum of both — never a stored/overridable
        // number — so it can't drift from what the underlying accounts actually show.
        val bankAssetRows = bankAccounts.map {
            BalanceSheetRow("${it.bankLabel} ••${it.lastFourDigits ?: "----"}", it.latestBalance?.toDouble() ?: 0.0)
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
        .catch { e ->
            CrashLog.record(app, "BalanceSheetViewModel", e)
            emit(BalanceSheetUiState())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BalanceSheetUiState())
}
