package com.expensetracker.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.expensetracker.core.model.Category
import com.expensetracker.core.model.ParseConfidence
import com.expensetracker.core.model.TransactionKind
import com.expensetracker.core.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * One captured transaction. Mirrors [com.expensetracker.core.model.ParsedTransaction] plus the
 * app-level fields (category, review/large-transaction flags, provenance) that only make sense
 * once persisted.
 *
 * [kind] is the accounting classification (Expense/Income/Self-Transfer/...) — separate from
 * [category] (which sector) and [type] (raw debit/credit direction from the SMS). It drives
 * [com.expensetracker.app.accounting.LedgerPostingEngine]: which asset/liability buckets move,
 * and whether this counts toward income/expense/investment totals at all. [linkedContactId] and
 * [linkedLoanId] are set only for kinds that need them (see [TransactionKind.requiresContact] /
 * [TransactionKind.requiresLoan]); [principalPortion]/[interestPortion] only for LOAN_REPAYMENT.
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
    val kind: TransactionKind = TransactionKind.EXPENSE,
    /** Whether [kind] was auto-suggested (default) or explicitly confirmed/changed by the user. */
    val kindConfident: Boolean = false,
    val linkedContactId: String? = null,
    val linkedLoanId: String? = null,
    val principalPortion: BigDecimal? = null,
    val interestPortion: BigDecimal? = null,
)
