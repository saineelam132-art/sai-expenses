package com.expensetracker.core.loan

import com.expensetracker.core.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class RevolvingCreditTest {

    private fun draw(amount: String) = TransactionKind.LOAN_DISBURSED to BigDecimal(amount)
    private fun repay(amount: String) = TransactionKind.LOAN_REPAYMENT to BigDecimal(amount)

    @Test
    fun `three separate draws accumulate into one running balance`() {
        // The reported bug: three Slice disbursements showed as three liabilities instead of one
        // line owing their total.
        val balance = RevolvingCredit.balanceAfter(
            opening = BigDecimal.ZERO,
            postings = listOf(draw("1000"), draw("2000"), draw("500")),
        )
        assertEquals(BigDecimal("3500"), balance)
    }

    @Test
    fun `draws add to an opening balance rather than replacing it`() {
        val balance = RevolvingCredit.balanceAfter(BigDecimal("10200"), listOf(draw("1000")))
        assertEquals(BigDecimal("11200"), balance)
    }

    @Test
    fun `a repayment reduces the same running balance`() {
        val balance = RevolvingCredit.balanceAfter(
            opening = BigDecimal.ZERO,
            postings = listOf(draw("3500"), repay("500")),
        )
        assertEquals(BigDecimal("3000"), balance)
    }

    @Test
    fun `spending funded by the credit line also increases what is owed`() {
        val balance = RevolvingCredit.balanceAfter(
            opening = BigDecimal.ZERO,
            postings = listOf(TransactionKind.EXPENSE to BigDecimal("250")),
        )
        assertEquals(BigDecimal("250"), balance)
    }

    @Test
    fun `reversing a posting undoes it exactly`() {
        val afterDraw = RevolvingCredit.apply(BigDecimal("1000"), TransactionKind.LOAN_DISBURSED, BigDecimal("500"))
        val reversed = RevolvingCredit.apply(afterDraw, TransactionKind.LOAN_DISBURSED, BigDecimal("500"), sign = -1)
        assertEquals(BigDecimal("1000"), reversed)
    }

    @Test
    fun `repaying more than is owed settles at zero rather than going negative`() {
        val balance = RevolvingCredit.balanceAfter(BigDecimal("100"), listOf(repay("250")))
        assertEquals(BigDecimal.ZERO, balance)
    }

    @Test
    fun `kinds unrelated to the credit line leave the balance untouched`() {
        val balance = RevolvingCredit.apply(BigDecimal("3500"), TransactionKind.INCOME, BigDecimal("900"))
        assertEquals(BigDecimal("3500"), balance)
    }
}
