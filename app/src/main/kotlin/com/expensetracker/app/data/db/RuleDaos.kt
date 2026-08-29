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

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    @Query("SELECT * FROM accounts ORDER BY bankLabel ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?
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
