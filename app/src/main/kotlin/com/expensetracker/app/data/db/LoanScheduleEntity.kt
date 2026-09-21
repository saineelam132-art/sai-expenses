package com.expensetracker.app.data.db

import androidx.room.Entity
import java.math.BigDecimal
import java.time.LocalDate

/**
 * One installment row from a loan's repayment schedule, exactly as printed in the loan
 * agreement (e.g. Schedule-I of a Slice loan document).
 *
 * The split between [principalPortion] and [interestPortion] is **stored, never computed**: real
 * BNPL/personal-loan schedules are fixed upfront by the lender and don't reliably follow a clean
 * reducing-balance formula (a verified real Slice schedule has month-2 interest *higher* than
 * month-1 despite a lower outstanding principal, from a partial first-period adjustment). Any
 * formula this app derived would therefore drift from what the borrower actually owes.
 *
 * [matchedTransactionId] links the row to the captured repayment that paid it, so each row is
 * consumed at most once and a reversal can put it back.
 */
@Entity(tableName = "loan_schedule", primaryKeys = ["loanId", "installmentNumber"])
data class LoanScheduleEntity(
    /** [LedgerAccountEntity.id] of the LOAN this schedule belongs to. */
    val loanId: String,
    val installmentNumber: Int,
    val dueDate: LocalDate,
    val principalPortion: BigDecimal,
    val interestPortion: BigDecimal,
    /** The installment's total EMI — what a captured repayment is matched against. */
    val totalAmount: BigDecimal,
    /** Outstanding principal the lender shows *after* this installment is paid. */
    val remainingPrincipalAfter: BigDecimal,
    val matchedTransactionId: Long? = null,
)
