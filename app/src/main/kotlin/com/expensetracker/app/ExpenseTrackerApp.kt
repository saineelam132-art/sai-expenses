package com.expensetracker.app

import android.app.Application
import android.content.Context
import com.expensetracker.app.accounting.LedgerPostingEngine
import com.expensetracker.app.accounting.SLICE_LEDGER_ACCOUNT_ID
import com.expensetracker.app.accounting.TypeInferenceEngine
import com.expensetracker.app.data.db.AppDatabase
import com.expensetracker.app.data.db.LedgerAccountCategory
import com.expensetracker.app.data.db.LedgerAccountDao
import com.expensetracker.app.data.db.LedgerAccountEntity
import com.expensetracker.app.data.db.LedgerSide
import com.expensetracker.app.data.db.TransactionDao
import com.expensetracker.app.data.repository.RoomMerchantRuleStore
import com.expensetracker.app.data.repository.SettingsRepository
import com.expensetracker.app.data.repository.TransactionRepository
import com.expensetracker.app.data.repository.loadOrSeedKeywordRules
import com.expensetracker.app.diagnostics.CrashLog
import com.expensetracker.app.notify.NotificationChannels
import com.expensetracker.app.worker.WeeklySummaryWorker
import com.expensetracker.core.categorize.CategoryEngine
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal

/**
 * Composition root. Everything here is built once at process start so the SMS receiver and
 * notification listener (which may be woken by the system with no UI ever having run) can reach
 * a ready [TransactionRepository] and [CategoryEngine] immediately.
 */
class ExpenseTrackerApp : Application() {
    // Any uncaught exception from a coroutine launched on this scope (SMS/notification capture,
    // in particular — background work with no UI to show a normal error to) is logged instead of
    // crashing the whole process. SupervisorJob already stops one child's failure from cancelling
    // siblings; this handler is what stops it from reaching the thread's default (crashing)
    // handler at all.
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        CrashLog.record(this, "applicationScope", throwable)
    }
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + coroutineExceptionHandler)

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
        CrashLog.installUncaughtExceptionLogger(this)
        NotificationChannels.createAll(this)

        database = AppDatabase.getInstance(this)
        settingsRepository = SettingsRepository(this)

        val merchantRuleStore = RoomMerchantRuleStore(database.merchantRuleDao(), applicationScope)
        // Small, local reads (a few hundred rows at most) — acceptable to block startup briefly
        // so the capture pipeline never races an empty cache.
        val keywordRules = runBlocking(Dispatchers.IO) {
            merchantRuleStore.preload()
            ensureSingleSliceCreditLine(database.ledgerAccountDao(), database.transactionDao())
            migrateRentEmiCategory(database.transactionDao())
            loadOrSeedKeywordRules(database.keywordRuleDao())
        }
        categoryEngine = CategoryEngine(keywordRules, merchantRuleStore)
        val typeInferenceEngine = TypeInferenceEngine(
            database.contactDao(),
            database.ledgerAccountDao(),
            database.transactionDao(),
            settingsRepository,
        )
        val ledgerPostingEngine = LedgerPostingEngine(
            database.ledgerAccountDao(),
            database.contactDao(),
            database.transactionDao(),
            database.loanScheduleDao(),
        )
        transactionRepository = TransactionRepository(
            database.transactionDao(),
            database.accountDao(),
            categoryEngine,
            typeInferenceEngine,
            ledgerPostingEngine,
            database.customCategoryDao(),
        )

        WeeklySummaryWorker.schedule(this)
    }

    companion object {
        fun from(context: Context): ExpenseTrackerApp = context.applicationContext as ExpenseTrackerApp
    }
}

/**
 * Guarantees **exactly one** Slice record, for the life of the app, under the fixed id
 * [SLICE_LEDGER_ACCOUNT_ID].
 *
 * Slice is a revolving credit line, not a fixed-schedule loan, so it is stored as its own
 * [LedgerAccountCategory.CREDIT_LINE] record that every draw adds to and every repayment
 * subtracts from. Modelling it as a LOAN is what allowed a second record to appear beside the
 * first (Setup's "add a loan" mints a fresh id, and the app separately seeded its own), leaving
 * the balance sheet showing several Slice lines instead of one running total.
 *
 * Anything that looks like Slice — the seeded id, or any ledger account whose name mentions it —
 * is folded into that single record with the balances **summed**, so an install that already
 * accumulated duplicates ends up with one correct figure rather than needing a reinstall. Their
 * transactions are repointed first so nothing is orphaned, and loan-document fields are carried
 * over from whichever duplicate had them.
 */
private suspend fun ensureSingleSliceCreditLine(
    ledgerAccountDao: LedgerAccountDao,
    transactionDao: TransactionDao,
) {
    val all = ledgerAccountDao.getAllOnce()
    val sliceRecords = all.filter {
        it.id == SLICE_LEDGER_ACCOUNT_ID || it.name.contains("slice", ignoreCase = true)
    }

    val merged = LedgerAccountEntity(
        id = SLICE_LEDGER_ACCOUNT_ID,
        name = "Slice",
        side = LedgerSide.LIABILITY,
        category = LedgerAccountCategory.CREDIT_LINE,
        balance = sliceRecords.fold(BigDecimal.ZERO) { sum, it -> sum + it.balance },
        loanAccountNumber = sliceRecords.firstNotNullOfOrNull { it.loanAccountNumber },
        interestRatePercent = sliceRecords.firstNotNullOfOrNull { it.interestRatePercent },
        aprPercent = sliceRecords.firstNotNullOfOrNull { it.aprPercent },
        penalChargeTerms = sliceRecords.firstNotNullOfOrNull { it.penalChargeTerms },
        foreclosureChargeTerms = sliceRecords.firstNotNullOfOrNull { it.foreclosureChargeTerms },
        createdAt = sliceRecords.minOfOrNull { it.createdAt } ?: System.currentTimeMillis(),
    )
    ledgerAccountDao.upsert(merged)

    sliceRecords.filter { it.id != SLICE_LEDGER_ACCOUNT_ID }.forEach { duplicate ->
        transactionDao.repointLinkedLoan(duplicate.id, SLICE_LEDGER_ACCOUNT_ID)
        ledgerAccountDao.delete(duplicate.id)
    }
}

/**
 * One-time cleanup for the sector formerly called "Rent/EMI". EMI payments are a Loan-Repayment
 * kind rather than a spending sector, so the sector is now just Rent — but rows captured under
 * the old constant would otherwise read back as Untagged, since the converter discards names it
 * doesn't recognise.
 */
private suspend fun migrateRentEmiCategory(transactionDao: TransactionDao) {
    transactionDao.renameStoredCategory("RENT_EMI", "RENT")
}
