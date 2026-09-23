package com.expensetracker.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRuleEntity)

    @Query("SELECT category FROM merchant_rules WHERE normalizedMerchant = :key")
    suspend fun getCategory(key: String): com.expensetracker.core.model.Category?

    @Query("SELECT * FROM merchant_rules")
    suspend fun getAllOnce(): List<MerchantRuleEntity>

    @Query("SELECT * FROM merchant_rules ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MerchantRuleEntity>>
}

@Dao
interface KeywordRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: KeywordRuleEntity)

    @Delete
    suspend fun delete(rule: KeywordRuleEntity)

    @Query("DELETE FROM keyword_rules WHERE keyword = :keyword")
    suspend fun deleteByKeyword(keyword: String)

    @Query("SELECT * FROM keyword_rules")
    suspend fun getAllOnce(): List<KeywordRuleEntity>

    @Query("SELECT * FROM keyword_rules ORDER BY keyword ASC")
    fun observeAll(): Flow<List<KeywordRuleEntity>>

    @Query("SELECT COUNT(*) FROM keyword_rules")
    suspend fun count(): Int
}

/** Net movement on one account since its balance was last anchored — see [AccountEntity]. */
data class AccountDelta(val accountId: String, val delta: Double)

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    @Query("SELECT * FROM accounts ORDER BY bankLabel ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    /**
     * Signed sum of every transaction recorded against each account *after* that account's
     * anchored balance timestamp. Direction comes from the SMS's own debit/credit flag, which is
     * already right for every kind that moves a bank balance (a card spend, salary in, one leg of
     * a transfer, an ATM withdrawal, a stock purchase), so no kind-specific rules are needed here.
     * An account with no anchor yet counts its whole history.
     */
    @Query(
        """SELECT t.accountId AS accountId,
                  COALESCE(SUM(CASE WHEN t.type = 'CREDIT' THEN CAST(t.amount AS REAL)
                                    ELSE -CAST(t.amount AS REAL) END), 0) AS delta
           FROM transactions t
           WHERE t.accountId IS NOT NULL
             AND t.amount IS NOT NULL
             AND t.transactionDateTime >
                 COALESCE((SELECT a.balanceAsOfMillis FROM accounts a WHERE a.id = t.accountId), 0)
           GROUP BY t.accountId""",
    )
    fun observeBalanceDeltas(): Flow<List<AccountDelta>>
}

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE category = :category")
    suspend fun getByCategory(category: com.expensetracker.core.model.Category): BudgetEntity?
}
