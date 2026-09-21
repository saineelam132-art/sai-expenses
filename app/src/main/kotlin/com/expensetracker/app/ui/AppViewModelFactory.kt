package com.expensetracker.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.ui.balancesheet.BalanceSheetViewModel
import com.expensetracker.app.ui.bills.BillsViewModel
import com.expensetracker.app.ui.dashboard.DashboardViewModel
import com.expensetracker.app.ui.friends.FriendsViewModel
import com.expensetracker.app.ui.settings.SettingsViewModel
import com.expensetracker.app.ui.setup.SetupViewModel
import com.expensetracker.app.ui.transactions.TransactionListViewModel

/**
 * No DI framework here on purpose — one small app-scoped factory keeps the dependency graph
 * obvious. Every ViewModel the UI can reach must be listed: an unlisted one throws at the moment
 * its screen opens, which is a crash, not a degraded screen.
 */
class AppViewModelFactory(private val app: ExpenseTrackerApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        DashboardViewModel::class.java -> DashboardViewModel(app) as T
        TransactionListViewModel::class.java -> TransactionListViewModel(app) as T
        SettingsViewModel::class.java -> SettingsViewModel(app) as T
        BalanceSheetViewModel::class.java -> BalanceSheetViewModel(app) as T
        SetupViewModel::class.java -> SetupViewModel(app) as T
        FriendsViewModel::class.java -> FriendsViewModel(app) as T
        BillsViewModel::class.java -> BillsViewModel(app) as T
        else -> throw IllegalArgumentException("Unknown ViewModel class $modelClass")
    }
}
