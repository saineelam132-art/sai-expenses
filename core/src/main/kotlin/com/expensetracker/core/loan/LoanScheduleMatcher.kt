package com.expensetracker.core.loan

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.abs

/** The fields of one installment row needed to match a payment to it. */
data class ScheduleInstallment(
    val installmentNumber: Int,
    val dueDate: LocalDate,
    val totalAmount: BigDecimal,
)

/**
 * Matches a captured loan repayment to the installment it paid.
 *
 * The principal/interest split is never derived — it's read off the matched row (see the app's
 * LoanScheduleEntity) — so this matching step is the only judgement the app makes about a
 * repayment, and it deliberately errs towards "no match" over a wrong one: an unmatched payment
 * is surfaced for manual matching, while a wrongly matched one silently misstates the balance.
 */
object LoanScheduleMatcher {

    /**
     * Payments run a few days early or late, so some slack is needed — but not half a monthly
     * cycle: at ±15 days on monthly installments *every* calendar date sits near some due date,
     * so the date check would never reject anything and an off-cycle payment that happened to
     * equal the EMI would silently consume the next installment. A week keeps normal early/late
     * payments matching while leaving a real gap mid-cycle, where an unrecognized payment is
     * flagged for manual matching instead.
     */
    const val MATCH_WINDOW_DAYS = 7L

    /** Installment amounts are exact to the paisa in the agreement (a final installment often
     * differs from the rest by a few paisa), so only floating-point dust is tolerated. */
    private val AMOUNT_TOLERANCE = BigDecimal("0.01")

    /**
     * The unpaid installment whose amount equals [amount] and whose due date is nearest [paidOn]
     * within [MATCH_WINDOW_DAYS], or null when nothing matches. Ties resolve to the earliest
     * installment, so a repeated amount is always consumed in schedule order.
     */
    fun match(unpaid: List<ScheduleInstallment>, amount: BigDecimal, paidOn: LocalDate): ScheduleInstallment? =
        unpaid
            .filter { (it.totalAmount - amount).abs() < AMOUNT_TOLERANCE }
            .filter { daysBetween(it.dueDate, paidOn) <= MATCH_WINDOW_DAYS }
            .minWithOrNull(compareBy({ daysBetween(it.dueDate, paidOn) }, { it.installmentNumber }))

    private fun daysBetween(a: LocalDate, b: LocalDate) = abs(a.toEpochDay() - b.toEpochDay())
}
