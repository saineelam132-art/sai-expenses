package com.expensetracker.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringBillDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bill: RecurringBillEntity)

    @Query("SELECT * FROM recurring_bills ORDER BY merchant ASC")
    fun observeAll(): Flow<List<RecurringBillEntity>>

    @Query("SELECT * FROM recurring_bills WHERE id = :id")
    suspend fun getById(id: String): RecurringBillEntity?
}
