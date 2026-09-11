package com.expensetracker.app.accounting

import com.expensetracker.app.data.db.ContactDao
import com.expensetracker.app.data.db.LedgerAccountCategory
import com.expensetracker.app.data.db.LedgerAccountDao
import com.expensetracker.app.data.db.TransactionDao
import com.expensetracker.app.data.db.TransactionEntity
import com.expensetracker.core.categorize.CategoryEngine
import com.expensetracker.core.model.TransactionKind
import com.expensetracker.core.model.TransactionType
import java.math.BigDecimal
import java.time.ZoneId

data class InferredKind(val kind: TransactionKind, val contactId: String? = null)

/**
 * Best-effort [TransactionKind] suggestion at capture time, so most transactions never need
 * manual tagging. Always non-final — [kindConfident][TransactionEntity.kindConfident] stays
 * false for anything this engine picks, and the user can override with one tap. Checked in this
 * order (first match wins):
 *  1. Groww payee text -> Investment-Buy/Sell. Placeholder until the dedicated Groww SMS parser
 *     (a later stage, needs real sample messages) exists — this only matches on the payee/source
 *     text containing "groww", not a real buy/sell-format parse.
 *  2. "ATM" in the payee/message -> Cash-Withdrawal.
 *  3. Payee matches a saved Friend contact -> Lent/Borrowed, or Friend-Repaid-Me/I-Repaid-Friend
 *     if that contact already has an outstanding balance in the opposite direction.
 *  4. A same-amount, opposite-direction transaction on a different one of my own accounts within
 *     15 minutes -> Self-Transfer.
 *  5. Default: Expense (debit) / Income (credit).
 */
class TypeInferenceEngine(
    private val contactDao: ContactDao,
    private val ledgerAccountDao: LedgerAccountDao,
    private val transactionDao: TransactionDao,
) {
    suspend fun infer(candidate: TransactionEntity): InferredKind {
        val amount = candidate.amount ?: return InferredKind(defaultFor(candidate.type))
        val searchText = "${candidate.merchant.orEmpty()} ${candidate.sourceLabel}"

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
            val zone = ZoneId.systemDefault()
            val windowStart = candidate.transactionDateTime.minusMinutes(SELF_TRANSFER_WINDOW_MINUTES)
                .atZone(zone).toInstant().toEpochMilli()
            val windowEnd = candidate.transactionDateTime.plusMinutes(SELF_TRANSFER_WINDOW_MINUTES)
                .atZone(zone).toInstant().toEpochMilli()
            val oppositeType = if (candidate.type == TransactionType.DEBIT) TransactionType.CREDIT else TransactionType.DEBIT
            val match = transactionDao.findPotentialSelfTransferMatch(
                excludeAccountId = candidate.accountId,
                amountValue = amount.toDouble(),
                oppositeTypeName = oppositeType.name,
                windowStartMillis = windowStart,
                windowEndMillis = windowEnd,
            )
            if (match != null) {
                return InferredKind(TransactionKind.SELF_TRANSFER)
            }
        }

        return InferredKind(defaultFor(candidate.type))
    }

    private fun defaultFor(type: TransactionType) = when (type) {
        TransactionType.DEBIT -> TransactionKind.EXPENSE
        TransactionType.CREDIT -> TransactionKind.INCOME
        TransactionType.UNKNOWN -> TransactionKind.EXPENSE
    }

    companion object {
        private const val SELF_TRANSFER_WINDOW_MINUTES = 15L
    }
}
