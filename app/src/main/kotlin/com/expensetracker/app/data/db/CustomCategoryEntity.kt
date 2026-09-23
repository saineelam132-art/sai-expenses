package com.expensetracker.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A sector the user invented (e.g. "Debt", "Credit"), alongside the built-in
 * [com.expensetracker.core.model.Category] set.
 *
 * Custom sectors live here as text rather than extending that enum: the enum is compiled into
 * the core module and its Room converter falls back to Uncategorized for anything it doesn't
 * recognise, so a user-typed value could never survive a round-trip through it. A transaction
 * records its custom sector in [TransactionEntity.customCategory]; rows here are just the
 * remembered list, so a sector typed once is offered again next time.
 *
 * [name] is the primary key in its display form, matched case-insensitively on entry so "Debt"
 * and "debt" don't both end up in the list.
 */
@Entity(tableName = "custom_categories")
data class CustomCategoryEntity(
    @PrimaryKey val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface CustomCategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CustomCategoryEntity)

    @Query("SELECT * FROM custom_categories ORDER BY name ASC")
    fun observeAll(): Flow<List<CustomCategoryEntity>>

    @Query("SELECT * FROM custom_categories WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByName(name: String): CustomCategoryEntity?

    @Query("DELETE FROM custom_categories WHERE name = :name")
    suspend fun delete(name: String)
}
