package com.expensetracker.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionKindTest {
    @Test
    fun `contact-requiring kinds are flagged correctly`() {
        assertTrue(TransactionKind.LENT.requiresContact)
        assertTrue(TransactionKind.BORROWED.requiresContact)
        assertTrue(TransactionKind.FRIEND_REPAID_ME.requiresContact)
        assertTrue(TransactionKind.I_REPAID_FRIEND.requiresContact)
        assertFalse(TransactionKind.EXPENSE.requiresContact)
        assertFalse(TransactionKind.LOAN_REPAYMENT.requiresContact)
    }

    @Test
    fun `loan-requiring kinds are flagged correctly`() {
        assertTrue(TransactionKind.LOAN_DISBURSED.requiresLoan)
        assertTrue(TransactionKind.LOAN_REPAYMENT.requiresLoan)
        assertFalse(TransactionKind.EXPENSE.requiresLoan)
        assertFalse(TransactionKind.LENT.requiresLoan)
    }
}
