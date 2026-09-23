package com.expensetracker.app.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.expensetracker.core.model.Category
import com.expensetracker.core.model.TransactionKind
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDateTime

/** Spending grouped by the sector it's shown under: [customCategory] when the user typed one,
 * otherwise [category]. */
data class SectorSpend(val category: Category, val customCategory: String?, val total: Double)

/** Month totals per accounting kind — the raw material for the cash-flow card. */
data class KindTotal(val kind: TransactionKind, val total: Double)

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    /**
     * Paged rather than a plain Flow<List<...>> — an unbounded live query over every transaction
     * ever captured (which only grows) was the crash risk: the whole table got loaded into memory
     * and re-diffed on every single DB write, including the flood of once-mis-parsed promotional
     * messages that used to land in "needs review" (see the parser hardening that reduces that
     * flood at the source). Room generates the PagingSource implementation from this query.
     */
    @Query("SELECT * FROM transactions ORDER BY transactionDateTime DESC")
    fun observeAllPaged(): PagingSource<Int, TransactionEntity>

    @Query("SELECT * FROM transactions WHERE needsReview = 1 ORDER BY transactionDateTime DESC")
    fun observeNeedsReviewPaged(): PagingSource<Int, TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    /** Cheap counts for the tab labels — paging no longer gives us the full list size for free. */
    @Query("SELECT COUNT(*) FROM transactions")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE needsReview = 1")
    fun observeNeedsReviewCount(): Flow<Int>

    @Query(
        """SELECT * FROM transactions
           WHERE transactionDateTime BETWEEN :startEpochMillis AND :endEpochMillis
           ORDER BY transactionDateTime DESC""",
    )
    fun observeInRange(startEpochMillis: Long, endEpochMillis: Long): Flow<List<TransactionEntity>>

    // Both queries below filter on kind = 'EXPENSE', not just type = 'DEBIT' — a debit that's
    // really a Self-Transfer, a Lent payment, or a Loan-Disbursed proceeds movement isn't real
    // spending in any sector and must not inflate these figures (see TransactionKind).
    @Query(
        """SELECT category, customCategory, SUM(CAST(amount AS REAL)) as total FROM transactions
           WHERE kind = 'EXPENSE' AND transactionDateTime BETWEEN :startEpochMillis AND :endEpochMillis
           GROUP BY category, customCategory""",
    )
    fun observeSectorSpend(startEpochMillis: Long, endEpochMillis: Long): Flow<List<SectorSpend>>

    @Query(
        """SELECT COALESCE(SUM(CAST(amount AS REAL)), 0) FROM transactions
           WHERE kind = 'EXPENSE' AND category = :category
           AND transactionDateTime BETWEEN :startEpochMillis AND :endEpochMillis""",
    )
    suspend fun sumSpendForCategory(category: Category, startEpochMillis: Long, endEpochMillis: Long): Double

    /** Cash-flow building blocks: Income/Expense/Investment-Buy/Investment-Sell sums by kind, and
     * the interest sliver of loan repayments (which counts as an expense per TransactionKind's
     * docs even though LOAN_REPAYMENT itself is excluded from plain kind-based sums). */
    @Query(
        """SELECT COALESCE(SUM(CAST(amount AS REAL)), 0) FROM transactions
           WHERE kind = :kindName AND transactionDateTime BETWEEN :startEpochMillis AND :endEpochMillis""",
    )
    suspend fun sumAmountForKind(kindName: String, startEpochMillis: Long, endEpochMillis: Long): Double

    /**
     * Live month totals grouped by kind, across every account. One grouped query rather than a
     * sum-per-kind so the cash-flow card recomputes from a single emission whenever any
     * transaction is captured or re-confirmed.
     */
    @Query(
        """SELECT kind, COALESCE(SUM(CAST(amount AS REAL)), 0) as total FROM transactions
           WHERE transactionDateTime BETWEEN :startEpochMillis AND :endEpochMillis
           GROUP BY kind""",
    )
    fun observeTotalsByKind(startEpochMillis: Long, endEpochMillis: Long): Flow<List<KindTotal>>

    @Query(
        """SELECT COALESCE(SUM(CAST(interestPortion AS REAL)), 0) FROM transactions
           WHERE kind = 'LOAN_REPAYMENT' AND transactionDateTime BETWEEN :startEpochMillis AND :endEpochMillis""",
    )
    suspend fun sumLoanInterestInRange(startEpochMillis: Long, endEpochMillis: Long): Double

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

    /**
     * Self-transfer detection: a same-amount, opposite-direction transaction on a *different*
     * one of my own accounts within a short time window suggests both legs are one transfer.
     * Amount is compared numerically (not as stored text) so differing decimal formatting
     * ("500" vs "500.00") between two SMS still matches.
     */
    @Query(
        """SELECT * FROM transactions
           WHERE accountId IS NOT NULL AND accountId != :excludeAccountId
           AND ABS(CAST(amount AS REAL) - :amountValue) < 0.01
           AND type = :oppositeTypeName
           AND transactionDateTime BETWEEN :windowStartMillis AND :windowEndMillis
           ORDER BY transactionDateTime DESC LIMIT 1""",
    )
    suspend fun findPotentialSelfTransferMatch(
        excludeAccountId: String,
        amountValue: Double,
        oppositeTypeName: String,
        windowStartMillis: Long,
        windowEndMillis: Long,
    ): TransactionEntity?

    /** Moves transactions from a ledger account being merged away onto the one that survives. */
    @Query("UPDATE transactions SET linkedLoanId = :newLoanId WHERE linkedLoanId = :oldLoanId")
    suspend fun repointLinkedLoan(oldLoanId: String, newLoanId: String)

    /**
     * Removes the one-time processing/insurance charges logged when a loan was set up, so
     * correcting a mistyped fee (or deleting the loan) doesn't leave a stale expense behind.
     * Matched on the stable "loan-fee:<loanId>:" reference these rows are written with.
     */
    @Query("DELETE FROM transactions WHERE referenceId LIKE 'loan-fee:' || :loanId || ':%'")
    suspend fun deleteLoanSetupFees(loanId: String)

    /** Bills view (section 10): most recent EXPENSE for this merchant, used to flag a bill as
     * possibly overdue by staleness rather than computing/guessing a specific next-due-date. */
    @Query("""SELECT MAX(transactionDateTime) FROM transactions WHERE merchant = :merchant AND kind = 'EXPENSE'""")
    suspend fun getLastExpenseDate(merchant: String): LocalDateTime?

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
