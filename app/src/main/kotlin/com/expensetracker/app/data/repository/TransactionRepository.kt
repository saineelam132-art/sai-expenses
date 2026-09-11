package com.expensetracker.app.data.repository

import android.content.Context
import com.expensetracker.app.accounting.LedgerPostingEngine
import com.expensetracker.app.accounting.TypeInferenceEngine
import com.expensetracker.app.data.db.AccountDao
import com.expensetracker.app.data.db.AccountEntity
import com.expensetracker.app.data.db.TransactionDao
import com.expensetracker.app.data.db.TransactionEntity
import com.expensetracker.core.categorize.CategoryEngine
import com.expensetracker.core.model.Category
import com.expensetracker.core.model.ParsedTransaction
import com.expensetracker.core.model.TransactionKind
import com.expensetracker.core.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryEngine: CategoryEngine,
    private val typeInferenceEngine: TypeInferenceEngine,
    private val ledgerPostingEngine: LedgerPostingEngine,
) {
    /**
     * Runs a freshly parsed message through categorization + kind inference, persists it, posts
     * its accounting effect to the ledger, and updates the account's running balance if the
     * message carried one. Returns the saved row (with its generated id) so the caller can fire
     * the instant notification.
     */
    suspend fun recordTransaction(context: Context, parsed: ParsedTransaction, largeTransactionThreshold: BigDecimal): TransactionEntity {
        val categorization = categoryEngine.categorize(parsed.merchant)
        val accountId = accountIdFor(parsed)

        val amount = parsed.amount
        val isLarge = amount != null &&
            parsed.type == TransactionType.DEBIT &&
            amount >= largeTransactionThreshold

        val draft = TransactionEntity(
            amount = parsed.amount,
            type = parsed.type,
            merchant = parsed.merchant,
            category = categorization.category,
            categoryConfident = categorization.confident,
            transactionDateTime = parsed.dateTime ?: LocalDateTime.now(),
            availableBalance = parsed.availableBalance,
            accountId = accountId,
            referenceId = parsed.referenceId,
            sourceLabel = parsed.sourceLabel,
            rawMessage = parsed.rawMessage,
            parseConfidence = parsed.confidence,
            needsReview = parsed.needsReview || !categorization.confident,
            isLargeTransaction = isLarge,
        )

        val inferred = typeInferenceEngine.infer(draft)
        val entity = draft.copy(
            kind = inferred.kind,
            kindConfident = false,
            linkedContactId = inferred.contactId,
        )
        val id = transactionDao.insert(entity)
        val saved = entity.copy(id = id)

        if (parsed.availableBalance != null && accountId != null) {
            accountDao.upsert(
                AccountEntity(
                    id = accountId,
                    bankLabel = parsed.sourceLabel,
                    lastFourDigits = parsed.accountHint,
                    latestBalance = parsed.availableBalance,
                    lastUpdated = System.currentTimeMillis(),
                ),
            )
        }

        ledgerPostingEngine.post(context, saved)
        return saved
    }

    /** User corrected a transaction's category from the review/transaction list screen. */
    suspend fun correctCategory(transaction: TransactionEntity, category: Category) {
        transaction.merchant?.let { categoryEngine.correctCategory(it, category) }
        transactionDao.update(
            transaction.copy(category = category, categoryConfident = true, needsReview = false, userReviewed = true),
        )
    }

    /**
     * User corrected (or confirmed) a transaction's accounting kind. Reverses whatever the old
     * kind had posted, then posts the new one — see [LedgerPostingEngine] for why that's always
     * safe regardless of what the old/new kinds were. [contactId]/[loanId] are required for kinds
     * that need them (see [TransactionKind.requiresContact]/[TransactionKind.requiresLoan]);
     * changing the kind always clears any previously-computed loan-repayment principal/interest
     * split, since it no longer applies.
     */
    suspend fun correctKind(
        context: Context,
        transaction: TransactionEntity,
        kind: TransactionKind,
        contactId: String? = null,
        loanId: String? = null,
    ) {
        ledgerPostingEngine.reverse(context, transaction)
        val updated = transaction.copy(
            kind = kind,
            kindConfident = true,
            linkedContactId = if (kind.requiresContact) contactId else null,
            linkedLoanId = if (kind.requiresLoan) loanId else null,
            principalPortion = null,
            interestPortion = null,
        )
        transactionDao.update(updated)
        ledgerPostingEngine.post(context, updated)
    }

    private fun accountIdFor(parsed: ParsedTransaction): String? {
        val hint = parsed.accountHint ?: return null
        return "${parsed.sourceLabel}-$hint"
    }
}
