package com.expensetracker.app.data.repository

import android.content.Context
import com.expensetracker.app.accounting.LedgerPostingEngine
import com.expensetracker.app.accounting.TypeInferenceEngine
import com.expensetracker.app.data.db.AccountDao
import com.expensetracker.app.data.db.AccountEntity
import com.expensetracker.app.data.db.CustomCategoryDao
import com.expensetracker.app.data.db.CustomCategoryEntity
import com.expensetracker.app.data.db.TransactionDao
import com.expensetracker.app.data.db.TransactionEntity
import com.expensetracker.core.categorize.CategoryEngine
import com.expensetracker.core.model.Category
import com.expensetracker.core.model.ParsedTransaction
import com.expensetracker.core.model.TransactionKind
import com.expensetracker.core.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryEngine: CategoryEngine,
    private val typeInferenceEngine: TypeInferenceEngine,
    private val ledgerPostingEngine: LedgerPostingEngine,
    private val customCategoryDao: CustomCategoryDao,
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
            linkedLoanId = inferred.loanId,
            needsReview = draft.needsReview || inferred.forceNeedsReview,
        )
        val id = transactionDao.insert(entity)
        val saved = entity.copy(id = id)

        if (parsed.availableBalance != null && accountId != null) {
            // Re-anchor: this balance is the bank's own figure *including* this transaction, so
            // the anchor timestamp is this transaction's own time — anything strictly later is
            // what gets added on top when the balance is displayed.
            accountDao.upsert(
                AccountEntity(
                    id = accountId,
                    bankLabel = parsed.sourceLabel,
                    lastFourDigits = parsed.accountHint,
                    latestBalance = parsed.availableBalance,
                    balanceAsOfMillis = saved.transactionDateTime.atZone(ZoneId.systemDefault())
                        .toInstant().toEpochMilli(),
                    lastUpdated = System.currentTimeMillis(),
                ),
            )
        }

        ledgerPostingEngine.post(context, saved)
        return saved
    }

    /**
     * User corrected a transaction's category from the review/transaction list screen.
     *
     * Tagging something as Investments also sets its accounting **kind**, because that's the only
     * field the ledger reads: without this, marking a stock purchase "Investments" changed its
     * colour on the pie chart and nothing else — the money never left the account and the
     * Investments asset never grew. A debit becomes a buy, a credit a sale.
     */
    suspend fun correctCategory(context: Context, transaction: TransactionEntity, category: Category) {
        transaction.merchant?.let { categoryEngine.correctCategory(it, category) }
        val recategorized = transaction.copy(
            category = category,
            customCategory = null,
            categoryConfident = true,
            needsReview = false,
            userReviewed = true,
        )

        val investmentKind = when {
            category != Category.INVESTMENTS -> null
            transaction.type == TransactionType.CREDIT -> TransactionKind.INVESTMENT_SELL
            else -> TransactionKind.INVESTMENT_BUY
        }
        if (investmentKind != null && transaction.kind != investmentKind) {
            correctKind(context, recategorized, investmentKind)
            return
        }
        transactionDao.update(recategorized)
    }

    /**
     * User typed a sector of their own (see [com.expensetracker.app.data.db.CustomCategoryEntity]).
     * Stored alongside the built-in enum rather than in it, and remembered so it's offered again.
     */
    suspend fun setCustomCategory(transaction: TransactionEntity, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val existing = customCategoryDao.findByName(trimmed)
        val canonical = existing?.name ?: trimmed
        if (existing == null) customCategoryDao.upsert(CustomCategoryEntity(name = canonical))
        transactionDao.update(
            transaction.copy(
                customCategory = canonical,
                category = Category.UNCATEGORIZED,
                categoryConfident = true,
                needsReview = false,
                userReviewed = true,
            ),
        )
    }

    /**
     * User corrected (or confirmed) a transaction's accounting kind — including via the review
     * screen's one-tap Self-Transfer/Income chips (section 12), which resolve a review-queue
     * entry instantly with no per-transaction screen. Reverses whatever the old kind had posted,
     * then posts the new one — see [LedgerPostingEngine] for why that's always safe regardless of
     * what the old/new kinds were. [contactId]/[loanId] are required for kinds that need them
     * (see [TransactionKind.requiresContact]/[TransactionKind.requiresLoan]); changing the kind
     * always clears any previously-computed loan-repayment principal/interest split, since it no
     * longer applies. Confirming a kind is itself a review action, so this also clears
     * [TransactionEntity.needsReview].
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
            // requiresLoan kinds get the newly-picked loan. A plain Expense that was already
            // funded via a credit line (e.g. auto-inferred Slice spending) keeps that funding
            // when re-confirmed as Expense, rather than silently losing its liability posting —
            // any other kind clears it, since it no longer applies.
            linkedLoanId = when {
                kind.requiresLoan -> loanId
                kind == TransactionKind.EXPENSE -> transaction.linkedLoanId
                else -> null
            },
            principalPortion = null,
            interestPortion = null,
            needsReview = false,
            userReviewed = true,
        )
        transactionDao.update(updated)
        ledgerPostingEngine.post(context, updated)
    }

    /** Sets or clears the user's free-text note on a transaction — available on any transaction,
     * auto-captured or manual, editable anytime. Purely informational: no ledger effect. */
    suspend fun setNote(transaction: TransactionEntity, note: String?) {
        transactionDao.update(transaction.copy(notes = note?.takeIf { it.isNotBlank() }))
    }

    /** The review screen's "Ignore" quick action (section 12) — accepts the auto-tagged
     * category/kind as-is and drops the transaction out of the review queue without changing
     * either. */
    suspend fun markReviewed(transaction: TransactionEntity) {
        transactionDao.update(transaction.copy(needsReview = false, userReviewed = true))
    }

    private fun accountIdFor(parsed: ParsedTransaction): String? {
        val hint = parsed.accountHint ?: return null
        return "${parsed.sourceLabel}-$hint"
    }
}
