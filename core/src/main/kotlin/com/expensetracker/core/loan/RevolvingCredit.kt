package com.expensetracker.core.loan

import com.expensetracker.core.model.TransactionKind
import java.math.BigDecimal

/**
 * Balance arithmetic for a revolving credit line (Slice).
 *
 * A credit line is one persistent record with a single running "amount owed" — unlike a
 * fixed-schedule loan, it has no installment table, so a repayment simply reduces what's owed
 * rather than consuming a scheduled row. Every draw adds, every repayment subtracts, against
 * that same record forever; nothing here ever creates a second one.
 */
object RevolvingCredit {

    /**
     * The outstanding balance after [kind] moves [amount] against [current]. [sign] is +1 when
     * applying a transaction and -1 when reversing one, so applying then reversing is a no-op.
     * Kinds that don't touch a credit line leave the balance alone.
     */
    fun apply(current: BigDecimal, kind: TransactionKind, amount: BigDecimal, sign: Int = 1): BigDecimal {
        val signed = amount * BigDecimal(sign)
        val next = when (kind) {
            // Drawing on the line, and spending funded by it, both increase what's owed.
            TransactionKind.LOAN_DISBURSED, TransactionKind.EXPENSE -> current + signed
            TransactionKind.LOAN_REPAYMENT -> current - signed
            else -> current
        }
        return next.coerceAtLeast(BigDecimal.ZERO)
    }

    /** Running balance after applying [postings] in order, starting from [opening]. */
    fun balanceAfter(
        opening: BigDecimal,
        postings: List<Pair<TransactionKind, BigDecimal>>,
    ): BigDecimal = postings.fold(opening) { balance, (kind, amount) -> apply(balance, kind, amount) }
}
