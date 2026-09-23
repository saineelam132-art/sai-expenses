package com.expensetracker.app.accounting

import com.expensetracker.app.data.db.ContactDao
import com.expensetracker.app.data.db.LedgerAccountCategory
import com.expensetracker.app.data.db.LedgerAccountDao
import com.expensetracker.app.data.db.TransactionDao
import com.expensetracker.app.data.db.TransactionEntity
import com.expensetracker.app.data.repository.SettingsRepository
import com.expensetracker.core.categorize.CategoryEngine
import com.expensetracker.core.model.TransactionKind
import com.expensetracker.core.model.TransactionType
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.ZoneId

data class InferredKind(
    val kind: TransactionKind,
    val contactId: String? = null,
    /** Set for kinds funded via a liability rather than a bank account (Slice's credit line, or
     * a formal loan) — see [com.expensetracker.app.accounting.LedgerPostingEngine]. Populated
     * here even for EXPENSE-kind results, which [TransactionKind.requiresLoan] says false for —
     * that flag only gates the *manual* kind-picker UI; this field is an internal signal the
     * posting engine still reads regardless of kind. */
    val loanId: String? = null,
    /** True when this suggestion is uncertain enough that it should land in "needs review" even
     * though a kind was still picked (e.g. a Slice self-disbursement with no matching bank credit
     * found to confirm where the money landed). */
    val forceNeedsReview: Boolean = false,
)

/** Well-known id for the Slice credit-line liability — see [ensureSliceLedgerAccountSeeded] in
 * ExpenseTrackerApp, which creates this bucket at startup so it's always ready to post against. */
const val SLICE_LEDGER_ACCOUNT_ID = "slice"

/**
 * Best-effort [TransactionKind] suggestion at capture time, so most transactions never need
 * manual tagging. Always non-final — [kindConfident][TransactionEntity.kindConfident] stays
 * false for anything this engine picks, and the user can override with one tap. Checked in this
 * order (first match wins):
 *  1. Slice ("sent from a/c xx7003 to <payee>") — a credit line, not a real bank account. Payee
 *     matching one of [SettingsRepository.ownNameVariants] -> Loan-Disbursed against the Slice
 *     liability, with a same-amount SBI credit searched for within a few hours to confirm where
 *     it landed (flagged for review if no match found, per spec, rather than silently assumed).
 *     Any other payee -> Expense, but still funded via the Slice liability (see [InferredKind.loanId]).
 *  2. IPPB "cash deposit" credit -> Cash-Deposit (money moving from cash-on-hand into the
 *     account, not fresh income) — one tap in the kind picker overrides to Income if it wasn't
 *     actually your own cash.
 *  3. Groww payee text -> Investment-Buy/Sell. Placeholder until the dedicated Groww SMS parser
 *     (a later stage, needs real sample messages) exists — this only matches on the payee/source
 *     text containing "groww", not a real buy/sell-format parse.
 *  4. "ATM" in the payee/message -> Cash-Withdrawal.
 *  5. Payee matches a saved Friend contact -> Lent/Borrowed, or Friend-Repaid-Me/I-Repaid-Friend
 *     if that contact already has an outstanding balance in the opposite direction.
 *  6. A same-amount, opposite-direction transaction on a different one of my own accounts within
 *     15 minutes -> Self-Transfer.
 *  7. Default: Expense (debit) / Income (credit).
 */
class TypeInferenceEngine(
    private val contactDao: ContactDao,
    private val ledgerAccountDao: LedgerAccountDao,
    private val transactionDao: TransactionDao,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun infer(candidate: TransactionEntity): InferredKind {
        val amount = candidate.amount ?: return InferredKind(defaultFor(candidate.type))
        val searchText = "${candidate.merchant.orEmpty()} ${candidate.sourceLabel}"

        if (candidate.sourceLabel.equals("slice", ignoreCase = true)) {
            return inferSlice(candidate, amount)
        }

        if (candidate.sourceLabel.equals("IPPB", ignoreCase = true) &&
            candidate.type == TransactionType.CREDIT &&
            candidate.rawMessage.contains("cash deposit", ignoreCase = true)
        ) {
            return InferredKind(TransactionKind.CASH_DEPOSIT)
        }

        if (searchText.contains("groww", ignoreCase = true)) {
            val kind = if (candidate.type == TransactionType.DEBIT) {
                TransactionKind.INVESTMENT_BUY
            } else {
                TransactionKind.INVESTMENT_SELL
            }
            return InferredKind(kind)
        }

        if (candidate.type == TransactionType.DEBIT && searchText.contains("atm", ignoreCase = true)) {
            return InferredKind(TransactionKind.CASH_WITHDRAWAL)
        }

        val normalizedMerchant = CategoryEngine.normalize(candidate.merchant)
        if (normalizedMerchant != null) {
            val contact = contactDao.getAllOnce().firstOrNull { contact ->
                contact.knownIdentifiers.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .any { normalizedMerchant.contains(it) }
            }
            if (contact != null) {
                val kind = when (candidate.type) {
                    TransactionType.DEBIT -> {
                        val payable = ledgerAccountDao.getByCategoryAndContact(LedgerAccountCategory.PAYABLE, contact.id)
                        if (payable != null && payable.balance > BigDecimal.ZERO) {
                            TransactionKind.I_REPAID_FRIEND
                        } else {
                            TransactionKind.LENT
                        }
                    }
                    TransactionType.CREDIT -> {
                        val receivable = ledgerAccountDao.getByCategoryAndContact(LedgerAccountCategory.RECEIVABLE, contact.id)
                        if (receivable != null && receivable.balance > BigDecimal.ZERO) {
                            TransactionKind.FRIEND_REPAID_ME
                        } else {
                            TransactionKind.BORROWED
                        }
                    }
                    TransactionType.UNKNOWN -> TransactionKind.EXPENSE
                }
                return InferredKind(kind, contact.id)
            }
        }

        if (candidate.accountId != null &&
            (candidate.type == TransactionType.DEBIT || candidate.type == TransactionType.CREDIT)
        ) {
            val match = findMatchingTransaction(candidate, amount, SELF_TRANSFER_WINDOW_MINUTES)
            if (match != null) {
                return InferredKind(TransactionKind.SELF_TRANSFER)
            }
        }

        return InferredKind(defaultFor(candidate.type))
    }

    private suspend fun inferSlice(candidate: TransactionEntity, amount: BigDecimal): InferredKind {
        val ownNames = settingsRepository.ownNameVariants.first().mapNotNull { CategoryEngine.normalize(it) }
        val normalizedPayee = CategoryEngine.normalize(candidate.merchant)
        val isSelfPayee = normalizedPayee != null && ownNames.any { variant ->
            normalizedPayee.contains(variant) || variant.contains(normalizedPayee)
        }

        val sliceLoanId = resolveSliceAccountId()

        if (!isSelfPayee) {
            // Spent via the credit line on a merchant: a real Expense, still funded by Slice.
            return InferredKind(TransactionKind.EXPENSE, loanId = sliceLoanId)
        }

        // Sent to myself: this is the loan proceeds landing somewhere — look for a same-amount
        // credit on a different one of my own accounts within a wider window than the plain
        // self-transfer check (Slice disbursements can take longer to reflect than an instant
        // same-bank transfer), and flag for manual confirmation if nothing matches rather than
        // silently guessing which account received it.
        val match = candidate.accountId?.let { findMatchingTransaction(candidate, amount, SLICE_MATCH_WINDOW_MINUTES) }
        return InferredKind(TransactionKind.LOAN_DISBURSED, loanId = sliceLoanId, forceNeedsReview = match == null)
    }

    /**
     * Which ledger account Slice transactions post to — looked up by name rather than assuming
     * [SLICE_LEDGER_ACCOUNT_ID], because a Slice loan the user entered in Setup gets a generated
     * id. Posting to the seeded id regardless is what left the user's own Slice row frozen while
     * an invisible second one absorbed every transaction.
     */
    private suspend fun resolveSliceAccountId(): String =
        ledgerAccountDao.getAllOnce()
            .firstOrNull {
                it.category == LedgerAccountCategory.LOAN &&
                    it.name.trim().equals("slice", ignoreCase = true)
            }
            ?.id
            ?: SLICE_LEDGER_ACCOUNT_ID

    private suspend fun findMatchingTransaction(candidate: TransactionEntity, amount: BigDecimal, windowMinutes: Long): TransactionEntity? {
        val accountId = candidate.accountId ?: return null
        val zone = ZoneId.systemDefault()
        val windowStart = candidate.transactionDateTime.minusMinutes(windowMinutes).atZone(zone).toInstant().toEpochMilli()
        val windowEnd = candidate.transactionDateTime.plusMinutes(windowMinutes).atZone(zone).toInstant().toEpochMilli()
        val oppositeType = if (candidate.type == TransactionType.DEBIT) TransactionType.CREDIT else TransactionType.DEBIT
        return transactionDao.findPotentialSelfTransferMatch(
            excludeAccountId = accountId,
            amountValue = amount.toDouble(),
            oppositeTypeName = oppositeType.name,
            windowStartMillis = windowStart,
            windowEndMillis = windowEnd,
        )
    }

    private fun defaultFor(type: TransactionType) = when (type) {
        TransactionType.DEBIT -> TransactionKind.EXPENSE
        TransactionType.CREDIT -> TransactionKind.INCOME
        TransactionType.UNKNOWN -> TransactionKind.EXPENSE
    }

    companion object {
        private const val SELF_TRANSFER_WINDOW_MINUTES = 15L
        private const val SLICE_MATCH_WINDOW_MINUTES = 4 * 60L
    }
}
