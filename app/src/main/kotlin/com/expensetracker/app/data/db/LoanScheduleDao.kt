package com.expensetracker.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: LoanScheduleEntity)

    @Update
    suspend fun update(row: LoanScheduleEntity)

    @Query("SELECT * FROM loan_schedule WHERE loanId = :loanId ORDER BY installmentNumber ASC")
    fun observeForLoan(loanId: String): Flow<List<LoanScheduleEntity>>

    @Query("SELECT * FROM loan_schedule WHERE loanId = :loanId ORDER BY installmentNumber ASC")
    suspend fun getForLoan(loanId: String): List<LoanScheduleEntity>

    /** Unpaid rows only — a repayment is matched against these, so one row is never paid twice. */
    @Query(
        """SELECT * FROM loan_schedule WHERE loanId = :loanId AND matchedTransactionId IS NULL
           ORDER BY installmentNumber ASC""",
    )
    suspend fun getUnmatchedForLoan(loanId: String): List<LoanScheduleEntity>

    @Query("SELECT * FROM loan_schedule WHERE matchedTransactionId = :transactionId LIMIT 1")
    suspend fun getMatchedTo(transactionId: Long): LoanScheduleEntity?

    @Query("DELETE FROM loan_schedule WHERE loanId = :loanId")
    suspend fun deleteForLoan(loanId: String)

    @Query("DELETE FROM loan_schedule WHERE loanId = :loanId AND installmentNumber = :installmentNumber")
    suspend fun delete(loanId: String, installmentNumber: Int)
}
