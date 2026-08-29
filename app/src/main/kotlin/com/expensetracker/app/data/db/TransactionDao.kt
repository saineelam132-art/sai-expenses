package com.expensetracker.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.expensetracker.core.model.Category
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

data class SectorSpend(val category: Category, val total: Double)

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY transactionDateTime DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE needsReview = 1 ORDER BY transactionDateTime DESC")
    fun observeNeedsReview(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query(
        """SELECT * FROM transactions
           WHERE transactionDateTime BETWEEN :startEpochMillis AND :endEpochMillis
           ORDER BY transactionDateTime DESC""",
    )
    fun observeInRange(startEpochMillis: Long, endEpochMillis: Long): Flow<List<TransactionEntity>>

    @Query(
        """SELECT category, SUM(CAST(amount AS REAL)) as total FROM transactions
           WHERE type = 'DEBIT' AND transactionDateTime BETWEEN :startEpochMillis AND :endEpochMillis
           GROUP BY category""",
    )
    fun observeSectorSpend(startEpochMillis: Long, endEpochMillis: Long): Flow<List<SectorSpend>>

    @Query(
        """SELECT COALESCE(SUM(CAST(amount AS REAL)), 0) FROM transactions
           WHERE type = 'DEBIT' AND category = :category
           AND transactionDateTime BETWEEN :startEpochMillis AND :endEpochMillis""",
    )
    suspend fun sumSpendForCategory(category: Category, startEpochMillis: Long, endEpochMillis: Long): Double

    @Query(
        """SELECT * FROM transactions
           WHERE transactionDateTime BETWEEN :startEpochMillis AND :endEpochMillis
           ORDER BY transactionDateTime DESC""",
    )
    suspend fun getInRange(startEpochMillis: Long, endEpochMillis: Long): List<TransactionEntity>

    /** Best-effort "balance a week ago" for the weekly summary's balance-trend line. */
    @Query(
        """SELECT availableBalance FROM transactions
           WHERE accountId = :accountId AND availableBalance IS NOT NULL AND transactionDateTime >= :startEpochMillis
           ORDER BY transactionDateTime ASC LIMIT 1""",
    )
    suspend fun getEarliestBalanceSince(accountId: String, startEpochMillis: Long): BigDecimal?

    /** Recurring-payment detection: merchants billed roughly monthly at a near-identical amount. */
    @Query(
        """SELECT merchant, CAST(amount AS TEXT) as amountText, COUNT(*) as occurrences FROM transactions
           WHERE type = 'DEBIT' AND merchant IS NOT NULL
           GROUP BY merchant, CAST(amount AS TEXT)
           HAVING occurrences >= 2""",
    )
    suspend fun getRepeatedMerchantAmounts(): List<RepeatedMerchantAmount>
}

data class RepeatedMerchantAmount(val merchant: String, val amountText: String, val occurrences: Int)
