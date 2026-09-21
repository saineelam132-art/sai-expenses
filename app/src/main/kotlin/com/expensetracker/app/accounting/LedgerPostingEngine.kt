package com.expensetracker.app.accounting

import android.content.Context
import com.expensetracker.app.data.db.ContactDao
import com.expensetracker.app.data.db.LedgerAccountCategory
import com.expensetracker.app.data.db.LedgerAccountDao
import com.expensetracker.app.data.db.LedgerAccountEntity
import com.expensetracker.app.data.db.LedgerSide
import com.expensetracker.app.data.db.TransactionEntity
import com.expensetracker.app.diagnostics.CrashLog
import com.expensetracker.core.model.TransactionKind
import java.math.BigDecimal

/**
 * Applies a transaction's accounting effect to the ledger's asset/liability buckets
 * ([LedgerAccountEntity] — cash, investments, loans, per-contact receivables/payables).
 *
 * Bank account balances are deliberately **not** touched here — those come straight from the
 * bank's own SMS ("Avl Bal") via [com.expensetracker.app.data.repository.TransactionRepository],
 * which is ground truth. This engine only maintains the buckets no bank SMS reports: everything
 * a [TransactionKind] implies happened *in addition to* the bank balance already having moved.
 *
 * [post] and [reverse] are exact inverses of each other (same logic, opposite sign) so that
 * changing a transaction's kind after the fact — [reverse] the old posting, then [post] the new
 * one — always leaves the ledger in the state it would be in had the final kind been correct
 * from the start.
 */
class LedgerPostingEngine(
    private val ledgerAccountDao: LedgerAccountDao,
    private val contactDao: ContactDao,
) {
    suspend fun post(context: Context, transaction: TransactionEntity) {
        applyPosting(context, transaction, sign = 1)
    }

    suspend fun reverse(context: Context, transaction: TransactionEntity) {
        applyPosting(context, transaction, sign = -1)
    }

    private suspend fun applyPosting(context: Context, transaction: TransactionEntity, sign: Int) {
        val amount = transaction.amount ?: return // nothing concrete to post
        try {
            when (transaction.kind) {
                TransactionKind.CASH_WITHDRAWAL -> adjustCash(amount, sign)
                TransactionKind.CASH_DEPOSIT -> adjustCash(amount, -sign)
                TransactionKind.INVESTMENT_BUY -> adjustInvestments(amount, sign)
                TransactionKind.INVESTMENT_SELL -> adjustInvestments(amount, -sign)
                TransactionKind.LOAN_DISBURSED -> adjustLoan(transaction.linkedLoanId, amount, sign)
                TransactionKind.LOAN_REPAYMENT -> postLoanRepayment(transaction, sign)
                TransactionKind.LENT -> adjustContactBucket(
                    transaction.linkedContactId, LedgerAccountCategory.RECEIVABLE, LedgerSide.ASSET, amount, sign,
                )
                TransactionKind.BORROWED -> adjustContactBucket(
                    transaction.linkedContactId, LedgerAccountCategory.PAYABLE, LedgerSide.LIABILITY, amount, sign,
                )
                TransactionKind.FRIEND_REPAID_ME -> adjustContactBucket(
                    transaction.linkedContactId, LedgerAccountCategory.RECEIVABLE, LedgerSide.ASSET, amount, -sign,
                )
                TransactionKind.I_REPAID_FRIEND -> adjustContactBucket(
                    transaction.linkedContactId, LedgerAccountCategory.PAYABLE, LedgerSide.LIABILITY, amount, -sign,
                )
                // An Expense usually only ever moves a bank balance, which the bank's own SMS
                // already reports — nothing to post. The exception is an expense funded via a
                // credit line rather than a real bank account (e.g. Slice): linkedLoanId is set
                // for those even though EXPENSE doesn't normally carry one (see InferredKind's
                // docs), and increasing that liability *is* this engine's job.
                TransactionKind.EXPENSE -> if (transaction.linkedLoanId != null) {
                    adjustLoan(transaction.linkedLoanId, amount, sign)
                }
                // Income/Self-Transfer only ever move bank balances, which the bank's own SMS
                // already reports — nothing for this engine to post.
                TransactionKind.INCOME, TransactionKind.SELF_TRANSFER -> Unit
            }
        } catch (e: Exception) {
            CrashLog.record(context, "LedgerPostingEngine", e)
        }
    }

    private suspend fun adjustCash(amount: BigDecimal, sign: Int) {
        val bucket = getOrCreate(LedgerAccountCategory.CASH, LedgerSide.ASSET, "cash-on-hand", "Cash on hand")
        save(bucket.copy(balance = clampNonNegative(bucket.balance + amount * sign.toBigDecimal())))
    }

    private suspend fun adjustInvestments(amount: BigDecimal, sign: Int) {
        val bucket = getOrCreate(LedgerAccountCategory.INVESTMENTS, LedgerSide.ASSET, "investments", "Investments")
        save(bucket.copy(balance = clampNonNegative(bucket.balance + amount * sign.toBigDecimal())))
    }

    private suspend fun adjustLoan(loanId: String?, amount: BigDecimal, sign: Int) {
        val loan = loanId?.let { ledgerAccountDao.getById(it) } ?: return // no linked loan yet — nothing to post
        save(loan.copy(balance = clampNonNegative(loan.balance + amount * sign.toBigDecimal())))
    }

    private suspend fun adjustContactBucket(
        contactId: String?,
        category: LedgerAccountCategory,
        side: LedgerSide,
        amount: BigDecimal,
        sign: Int,
    ) {
        if (contactId == null) return // not yet linked to a Friend — nothing to post
        val contact = contactDao.getById(contactId) ?: return
        val bucket = getOrCreate(category, side, "$category-$contactId", "${contact.name} (${category.name.lowercase()})", contactId)
        save(bucket.copy(balance = clampNonNegative(bucket.balance + amount * sign.toBigDecimal())))
    }

    /**
     * Reduces the loan's outstanding balance by the *full* repayment amount — no reducing-balance
     * or flat-interest formula is assumed here. [TransactionEntity.principalPortion]/
     * [TransactionEntity.interestPortion] stay null until real loan terms (this loan's actual
     * amortization schedule) are provided; guessing a split would misstate the outstanding
     * balance in a way that's hard to notice and worse than not splitting at all. Because there's
     * no derived state to persist, post and reverse are trivially exact inverses (same amount,
     * opposite sign).
     */
    private suspend fun postLoanRepayment(transaction: TransactionEntity, sign: Int) {
        val loanId = transaction.linkedLoanId ?: return
        val loan = ledgerAccountDao.getById(loanId) ?: return
        val amount = transaction.amount ?: return
        save(loan.copy(balance = clampNonNegative(loan.balance - amount * sign.toBigDecimal())))
    }

    private suspend fun getOrCreate(
        category: LedgerAccountCategory,
        side: LedgerSide,
        id: String,
        name: String,
        contactId: String? = null,
    ): LedgerAccountEntity {
        val existing = if (contactId != null) {
            ledgerAccountDao.getByCategoryAndContact(category, contactId)
        } else {
            ledgerAccountDao.getById(id)
        }
        return existing ?: LedgerAccountEntity(
            id = id,
            name = name,
            side = side,
            category = category,
            balance = BigDecimal.ZERO,
            contactId = contactId,
        ).also { ledgerAccountDao.upsert(it) }
    }

    private suspend fun save(account: LedgerAccountEntity) = ledgerAccountDao.upsert(account)

    private fun clampNonNegative(value: BigDecimal): BigDecimal = value.coerceAtLeast(BigDecimal.ZERO)

    private fun Int.toBigDecimal() = BigDecimal(this)
}
