package com.expensetracker.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.data.db.BudgetEntity
import com.expensetracker.app.data.db.KeywordRuleEntity
import com.expensetracker.app.diagnostics.CrashLog
import com.expensetracker.app.export.CsvExporter
import com.expensetracker.core.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class SettingsViewModel(private val app: ExpenseTrackerApp) : ViewModel() {

    val largeTransactionThreshold: StateFlow<BigDecimal> =
        app.settingsRepository.largeTransactionThreshold
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BigDecimal.valueOf(5000))

    val weeklySummaryEnabled: StateFlow<Boolean> =
        app.settingsRepository.weeklySummaryEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val encryptedBackupEnabled: StateFlow<Boolean> =
        app.settingsRepository.encryptedBackupEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val keywordRules: StateFlow<List<KeywordRuleEntity>> =
        app.database.keywordRuleDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgets: StateFlow<List<BudgetEntity>> =
        app.database.budgetDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setLargeTransactionThreshold(value: BigDecimal) {
        viewModelScope.launch { app.settingsRepository.setLargeTransactionThreshold(value) }
    }

    fun setWeeklySummaryEnabled(enabled: Boolean) {
        viewModelScope.launch { app.settingsRepository.setWeeklySummaryEnabled(enabled) }
    }

    fun setEncryptedBackupEnabled(enabled: Boolean) {
        viewModelScope.launch { app.settingsRepository.setEncryptedBackupEnabled(enabled) }
    }

    fun addOrUpdateKeywordRule(keyword: String, category: Category) {
        val normalized = keyword.trim().lowercase()
        if (normalized.isBlank()) return
        viewModelScope.launch {
            app.database.keywordRuleDao().upsert(KeywordRuleEntity(normalized, category))
            app.categoryEngine.addKeywordRule(normalized, category)
        }
    }

    fun removeKeywordRule(keyword: String) {
        viewModelScope.launch {
            app.database.keywordRuleDao().deleteByKeyword(keyword)
            app.categoryEngine.removeKeywordRule(keyword)
        }
    }

    fun setBudget(category: Category, monthlyLimit: BigDecimal) {
        viewModelScope.launch {
            val existing = app.database.budgetDao().getByCategory(category)
            app.database.budgetDao().upsert(
                (existing ?: BudgetEntity(category, monthlyLimit)).copy(monthlyLimit = monthlyLimit),
            )
        }
    }

    /** Exports the given month (or all time if null) and sector (or all if null) to a CSV file and returns its content URI. */
    /** Null on failure (disk I/O, etc.) — logged via [CrashLog] rather than crashing the Settings screen. */
    suspend fun exportCsv(month: YearMonth?, category: Category?): Uri? = withContext(Dispatchers.IO) {
        try {
            val zone = ZoneId.systemDefault()
            val (startMillis, endMillis) = if (month != null) {
                val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = month.atEndOfMonth().atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
                start to end
            } else {
                0L to LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            }
            val transactions = app.database.transactionDao().getInRange(startMillis, endMillis)
                .filter { category == null || it.category == category }

            val label = buildString {
                append("expenses")
                if (month != null) append("_$month")
                if (category != null) append("_${category.name.lowercase()}")
            }
            CsvExporter.writeCsv(app.applicationContext, transactions, label)
        } catch (e: Exception) {
            CrashLog.record(app, "SettingsViewModel.exportCsv", e)
            null
        }
    }
}
