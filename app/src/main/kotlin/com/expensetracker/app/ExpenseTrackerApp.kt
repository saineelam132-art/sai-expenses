package com.expensetracker.app

import android.app.Application
import android.content.Context
import com.expensetracker.app.data.db.AppDatabase
import com.expensetracker.app.data.repository.RoomMerchantRuleStore
import com.expensetracker.app.data.repository.SettingsRepository
import com.expensetracker.app.data.repository.TransactionRepository
import com.expensetracker.app.data.repository.loadOrSeedKeywordRules
import com.expensetracker.app.notify.NotificationChannels
import com.expensetracker.app.worker.WeeklySummaryWorker
import com.expensetracker.core.categorize.CategoryEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/**
 * Composition root. Everything here is built once at process start so the SMS receiver and
 * notification listener (which may be woken by the system with no UI ever having run) can reach
 * a ready [TransactionRepository] and [CategoryEngine] immediately.
 */
class ExpenseTrackerApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: AppDatabase
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var categoryEngine: CategoryEngine
        private set
    lateinit var transactionRepository: TransactionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)

        database = AppDatabase.getInstance(this)
        settingsRepository = SettingsRepository(this)

        val merchantRuleStore = RoomMerchantRuleStore(database.merchantRuleDao(), applicationScope)
        // Small, local reads (a few hundred rows at most) — acceptable to block startup briefly
        // so the capture pipeline never races an empty cache.
        val keywordRules = runBlocking(Dispatchers.IO) {
            merchantRuleStore.preload()
            loadOrSeedKeywordRules(database.keywordRuleDao())
        }
        categoryEngine = CategoryEngine(keywordRules, merchantRuleStore)
        transactionRepository = TransactionRepository(database.transactionDao(), database.accountDao(), categoryEngine)

        WeeklySummaryWorker.schedule(this)
    }

    companion object {
        fun from(context: Context): ExpenseTrackerApp = context.applicationContext as ExpenseTrackerApp
    }
}
