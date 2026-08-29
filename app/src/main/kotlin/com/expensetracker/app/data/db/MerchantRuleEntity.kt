package com.expensetracker.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.expensetracker.core.model.Category

/** A learned exact merchant -> category override, created when the user corrects a transaction. */
@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey val normalizedMerchant: String,
    val category: Category,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** One row of the user-editable keyword -> category table shown in Settings. */
@Entity(tableName = "keyword_rules")
data class KeywordRuleEntity(
    @PrimaryKey val keyword: String,
    val category: Category,
    val updatedAt: Long = System.currentTimeMillis(),
)
