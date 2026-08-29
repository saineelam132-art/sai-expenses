package com.expensetracker.app.data.repository

import com.expensetracker.app.data.db.AccountDao
import com.expensetracker.app.data.db.AccountEntity
import com.expensetracker.app.data.db.TransactionDao
import com.expensetracker.app.data.db.TransactionEntity
import com.expensetracker.core.categorize.CategoryEngine
import com.expensetracker.core.model.Category
import com.expensetracker.core.model.ParsedTransaction
import com.expensetracker.core.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryEngine: CategoryEngine,
) {
    /**
     * Runs a freshly parsed message through categorization, persists it, and updates the
     * account's running balance if the message carried one. Returns the saved row (with its
     * generated id) so the caller can fire the instant notification.
     */
    suspend fun recordTransaction(parsed: ParsedTransaction, largeTransactionThreshold: BigDecimal): TransactionEntity {
        val categorization = categoryEngine.categorize(parsed.merchant)
        val accountId = accountIdFor(parsed)

        val isLarge = parsed.amount != null &&
            parsed.type == TransactionType.DEBIT &&
            parsed.amount >= largeTransactionThreshold

        val entity = TransactionEntity(
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
        val id = transactionDao.insert(entity)

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

        return entity.copy(id = id)
    }

    /** User corrected a transaction's category from the review/transaction list screen. */
    suspend fun correctCategory(transaction: TransactionEntity, category: Category) {
        transaction.merchant?.let { categoryEngine.correctCategory(it, category) }
        transactionDao.update(
            transaction.copy(category = category, categoryConfident = true, needsReview = false, userReviewed = true),
        )
    }

    private fun accountIdFor(parsed: ParsedTransaction): String? {
        val hint = parsed.accountHint ?: return null
        return "${parsed.sourceLabel}-$hint"
    }
}
