package com.expensetracker.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerAccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: LedgerAccountEntity)

    @Query("SELECT * FROM ledger_accounts ORDER BY category ASC, name ASC")
    fun observeAll(): Flow<List<LedgerAccountEntity>>

    @Query("SELECT * FROM ledger_accounts ORDER BY category ASC, name ASC")
    suspend fun getAllOnce(): List<LedgerAccountEntity>

    @Query("SELECT * FROM ledger_accounts WHERE id = :id")
    suspend fun getById(id: String): LedgerAccountEntity?

    @Query("SELECT * FROM ledger_accounts WHERE category = :category AND contactId = :contactId LIMIT 1")
    suspend fun getByCategoryAndContact(category: LedgerAccountCategory, contactId: String): LedgerAccountEntity?

    @Query("SELECT * FROM ledger_accounts WHERE category = 'CASH' LIMIT 1")
    suspend fun getCashBucket(): LedgerAccountEntity?

    @Query("SELECT * FROM ledger_accounts WHERE category = 'INVESTMENTS' LIMIT 1")
    suspend fun getInvestmentsBucket(): LedgerAccountEntity?

    @Query("SELECT COALESCE(SUM(CAST(balance AS REAL)), 0) FROM ledger_accounts WHERE side = 'ASSET'")
    fun observeTotalAssets(): Flow<Double>

    @Query("SELECT COALESCE(SUM(CAST(balance AS REAL)), 0) FROM ledger_accounts WHERE side = 'LIABILITY'")
    fun observeTotalLiabilities(): Flow<Double>

    @Query("DELETE FROM ledger_accounts WHERE id = :id")
    suspend fun delete(id: String)
}
