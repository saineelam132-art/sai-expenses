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
            ensureSliceLedgerAccountSeeded(database.ledgerAccountDao(), database.transactionDao())
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
 * Slice is a credit line, not a real bank account — there's no SMS balance to seed it from, so
 * unlike bank accounts it must exist before the first Slice transaction is posted, or
 * [LedgerPostingEngine]'s "no linked loan yet — nothing to post" safety check (correct for a
 * user-added loan that genuinely doesn't exist yet) would silently no-op every Slice posting too.
 *
 * It also **merges duplicates**. Seeding created a row with the fixed id "slice" while adding a
 * loan called Slice in Setup created a second one under a random id, so the balance sheet showed
 * two Slice entries and transactions moved the one the user wasn't looking at. The user's own row
 * wins (their entered balance is the real opening figure), every transaction pointing at a
 * discarded row is repointed, and the leftovers are deleted.
 */
private suspend fun ensureSliceLedgerAccountSeeded(
    ledgerAccountDao: LedgerAccountDao,
    transactionDao: TransactionDao,
) {
    val sliceAccounts = ledgerAccountDao.getAllOnce().filter {
        it.category == LedgerAccountCategory.LOAN && it.name.trim().equals("slice", ignoreCase = true)
    }

    if (sliceAccounts.isEmpty()) {
        ledgerAccountDao.upsert(
            LedgerAccountEntity(
                id = SLICE_LEDGER_ACCOUNT_ID,
                name = "Slice",
                side = LedgerSide.LIABILITY,
                category = LedgerAccountCategory.LOAN,
                balance = BigDecimal.ZERO,
            ),
        )
        return
    }

    // Prefer a row the user set up themselves (it carries their opening balance and loan terms)
    // over the placeholder the app seeded; between equals, the larger balance.
    val survivor = sliceAccounts.sortedWith(
        compareByDescending<LedgerAccountEntity> { it.id != SLICE_LEDGER_ACCOUNT_ID }
            .thenByDescending { it.balance },
    ).first()

    sliceAccounts.filter { it.id != survivor.id }.forEach { duplicate ->
        transactionDao.repointLinkedLoan(duplicate.id, survivor.id)
        ledgerAccountDao.delete(duplicate.id)
    }
}
