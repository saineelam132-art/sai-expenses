package com.expensetracker.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.ui.dashboard.DashboardViewModel
import com.expensetracker.app.ui.settings.SettingsViewModel
import com.expensetracker.app.ui.transactions.TransactionListViewModel

/** No DI framework here on purpose — one small app-scoped factory keeps the dependency graph obvious. */
class AppViewModelFactory(private val app: ExpenseTrackerApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        DashboardViewModel::class.java -> DashboardViewModel(app) as T
        TransactionListViewModel::class.java -> TransactionListViewModel(app) as T
        SettingsViewModel::class.java -> SettingsViewModel(app) as T
        else -> throw IllegalArgumentException("Unknown ViewModel class $modelClass")
    }
}
