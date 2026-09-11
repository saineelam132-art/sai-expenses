package com.expensetracker.app.accounting

import android.content.Context
import com.expensetracker.app.data.db.ContactDao
import com.expensetracker.app.data.db.LedgerAccountCategory
import com.expensetracker.app.data.db.LedgerAccountDao
import com.expensetracker.app.data.db.LedgerAccountEntity
import com.expensetracker.app.data.db.LedgerSide
import com.expensetracker.app.data.db.TransactionDao
import com.expensetracker.app.data.db.TransactionEntity
import com.expensetracker.app.diagnostics.CrashLog
import com.expensetracker.core.model.TransactionKind
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.temporal.ChronoUnit

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
    private val transactionDao: TransactionDao,
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
                // Expense/Income/Self-Transfer only ever move bank balances, which the bank's own
                // SMS already reports — nothing for this engine to post.
                TransactionKind.EXPENSE, TransactionKind.INCOME, TransactionKind.SELF_TRANSFER -> Unit
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
     * Splits a repayment into principal/interest (reducing-balance method) if the transaction
     * doesn't already carry an explicit split, persists that split onto the transaction row (so
     * cash-flow reporting can count only the interest portion as an expense), and reduces the
     * loan's outstanding balance by the principal portion.
     */
    private suspend fun postLoanRepayment(transaction: TransactionEntity, sign: Int) {
        val loanId = transaction.linkedLoanId ?: return
        val loan = ledgerAccountDao.getById(loanId) ?: return
        val amount = transaction.amount ?: return

        var principalPortion = transaction.principalPortion
        var interestPortion = transaction.interestPortion

        if (principalPortion == null || interestPortion == null) {
            // Only compute+persist a fresh split when posting for the first time (sign == 1) —
            // a reversal (sign == -1) must undo exactly what was originally posted, using the
            // split already stored on the transaction, not a newly recomputed one (the loan's
            // balance/dates have since moved, which would give a different number).
            if (sign < 0) return
            val rate = loan.interestRatePercent ?: 0.0
            val since = loan.lastAccrualDate ?: loan.disbursedDate
            val days = since?.let { ChronoUnit.DAYS.between(it, transaction.transactionDateTime.toLocalDate()) } ?: 0L
            val interest = loan.balance
                .multiply(BigDecimal.valueOf(rate / 100.0))
                .multiply(BigDecimal.valueOf(days.coerceAtLeast(0) / 365.0))
                .setScale(2, RoundingMode.HALF_UP)
                .coerceAtMost(amount) // a payment can never count more interest than it actually paid
            interestPortion = interest
            principalPortion = (amount - interest).coerceAtLeast(BigDecimal.ZERO)

            transactionDao.update(transaction.copy(principalPortion = principalPortion, interestPortion = interestPortion))
            save(loan.copy(lastAccrualDate = transaction.transactionDateTime.toLocalDate()))
        }

        val finalPrincipalPortion = principalPortion ?: BigDecimal.ZERO
        val updatedLoan = ledgerAccountDao.getById(loanId) ?: loan
        save(updatedLoan.copy(balance = clampNonNegative(updatedLoan.balance - finalPrincipalPortion * sign.toBigDecimal())))
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
