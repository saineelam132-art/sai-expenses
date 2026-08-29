package com.expensetracker.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.expensetracker.core.model.Category
import com.expensetracker.core.model.ParseConfidence
import com.expensetracker.core.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * One captured transaction. Mirrors [com.expensetracker.core.model.ParsedTransaction] plus the
 * app-level fields (category, review/large-transaction flags, provenance) that only make sense
 * once persisted.
 */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: BigDecimal?,
    val type: TransactionType,
    val merchant: String?,
    val category: Category,
    /** Whether [category] came from a learned rule, a keyword match, or neither (needs review). */
    val categoryConfident: Boolean,
    val transactionDateTime: LocalDateTime,
    val availableBalance: BigDecimal?,
    val accountId: String?,
    val referenceId: String?,
    val sourceLabel: String,
    val rawMessage: String,
    val parseConfidence: ParseConfidence,
    val needsReview: Boolean,
    val isLargeTransaction: Boolean,
    /** True once the user has confirmed/corrected this transaction's category. */
    val userReviewed: Boolean = false,
    val capturedAt: Long = System.currentTimeMillis(),
)
