package com.expensetracker.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TransactionEntity::class,
        AccountEntity::class,
        MerchantRuleEntity::class,
        KeywordRuleEntity::class,
        BudgetEntity::class,
        ContactEntity::class,
        LedgerAccountEntity::class,
        RecurringBillEntity::class,
    ],
    // v2: added the accounting-engine tables (contacts, ledger_accounts) and new columns on
    // transactions (kind, linkedContactId, linkedLoanId, principalPortion, interestPortion).
    // v3: added recurring_bills (UPI AutoPay mandate lifecycle tracking).
    // v4: added transactions.notes (free-text note, editable anytime). Destructive migration
    // rather than a hand-written Migration — this project has no released users yet, so
    // preserving old local test data isn't worth the risk of an unverifiable migration (this
    // environment has no Android SDK to actually run one against). Uninstall and reinstall the
    // app after this update.
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun keywordRuleDao(): KeywordRuleDao
    abstract fun budgetDao(): BudgetDao
    abstract fun contactDao(): ContactDao
    abstract fun ledgerAccountDao(): LedgerAccountDao
    abstract fun recurringBillDao(): RecurringBillDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
