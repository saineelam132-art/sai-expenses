package com.expensetracker.core.parser

import com.expensetracker.core.model.MandateAction
import com.expensetracker.core.model.NonTransactionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class NonTransactionClassifierTest {

    @Test
    fun `OTP message is discarded`() {
        val msg = "123456 is your OTP for login. Never Share OTP with anyone -IPPB"
        assertEquals(NonTransactionEvent.Discard, NonTransactionClassifier.classify(msg, "VM-IPPB-S"))
    }

    @Test
    fun `Angel One broker balance disclosure is discarded`() {
        val msg = "Angel One on 01-Sep-2026 reported your Fund bal Rs.500 & Securities bal Rs.10000. " +
            "This excludes your Bank, DP & PMS bal with the broker-NSE"
        assertEquals(NonTransactionEvent.Discard, NonTransactionClassifier.classify(msg, "ANGELONE"))
    }

    @Test
    fun `Groww broker balance disclosure is discarded`() {
        val msg = "Groww on 01-Sep-2026 reported your Fund bal Rs.200 & Securities bal Rs.5000. " +
            "This excludes your Bank, DP & PMS bal with the broker-NSE"
        assertEquals(NonTransactionEvent.Discard, NonTransactionClassifier.classify(msg, "GROWW"))
    }

    @Test
    fun `AutoPay Mandate created is a mandate event not a transaction`() {
        val msg = "UPI AutoPay Mandate with aspresented Frequency is successfully created towards Netflix " +
            "from 280626 to 280627 for Rs.500 - REF123456 -IPPB"
        val result = NonTransactionClassifier.classify(msg, "VM-IPPB-S") as NonTransactionEvent.MandateEvent
        assertEquals(MandateAction.CREATED, result.action)
        assertEquals("Netflix", result.merchant)
        assertEquals(BigDecimal("500"), result.amount)
        assertEquals(LocalDate.of(2026, 6, 28), result.validFrom)
        assertEquals(LocalDate.of(2027, 6, 28), result.validTo)
        assertEquals("REF123456", result.referenceId)
    }

    @Test
    fun `AutoPay mandate paused is a mandate event`() {
        val msg = "UPI AutoPay mandate set towards Netflix with Rs.500 is paused from 280626 to 280627 REF123456 -IPPB"
        val result = NonTransactionClassifier.classify(msg, "VM-IPPB-S") as NonTransactionEvent.MandateEvent
        assertEquals(MandateAction.PAUSED, result.action)
        assertEquals("Netflix", result.merchant)
        assertEquals(BigDecimal("500"), result.amount)
    }

    @Test
    fun `AutoPay Mandate revoked is a mandate event with no date range`() {
        val msg = "UPI AutoPay Mandate is successfully Revoked towards Netflix for Rs.500 REF123456 -IPPB"
        val result = NonTransactionClassifier.classify(msg, "VM-IPPB-S") as NonTransactionEvent.MandateEvent
        assertEquals(MandateAction.REVOKED, result.action)
        assertEquals("Netflix", result.merchant)
        assertNull(result.validFrom)
        assertNull(result.validTo)
    }

    @Test
    fun `declined payment with Avl bal is balance-only not a transaction`() {
        val msg = "Your payment declined due to insufficient funds. Avl bal: Rs.120.00 -IPPB"
        val result = NonTransactionClassifier.classify(msg, "VM-IPPB-S") as NonTransactionEvent.BalanceOnly
        assertEquals(BigDecimal("120.00"), result.balance)
    }

    @Test
    fun `ordinary transaction message is not classified as non-transaction`() {
        val msg = "Rs.500.00 debited from A/c XX1234 on 05-08-24 transfer to MOHAN SHOP Ref No 123456789012 -SBI"
        assertNull(NonTransactionClassifier.classify(msg, "SBIINB"))
    }
}
