package com.expensetracker.core.parser

import com.expensetracker.core.model.ParseConfidence
import com.expensetracker.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class ApgbAndIppbParserTest {
    private val registry = ParserRegistry()

    @Test
    fun `APGB credit has no merchant but updates balance`() {
        val msg = "Dear Customer, An amount of Rs.500/- is credited in your A/c XXXX3077 on " +
            "01-08-2026, UPI Ref No:192015211858. At present A/c Balance is Rs.5000 -APGBank"
        val result = registry.parse(msg, sender = "JD-APGB-T")!!
        assertEquals(BigDecimal("500"), result.amount)
        assertEquals(TransactionType.CREDIT, result.type)
        assertNull(result.merchant)
        assertEquals(BigDecimal("5000"), result.availableBalance)
        assertEquals("3077", result.accountHint)
        assertEquals("192015211858", result.referenceId)
        assertEquals("APGB", result.sourceLabel)
    }

    @Test
    fun `APGB debit never carries a balance`() {
        val msg = "Your a/c no. XXXXXXXXXXX3077 is debited for Rs.60.00 on 01/08/2026 17:08:19 and " +
            "credited to VPA ombk.dqracv567083b67temfrr@mbk (UPI Ref no 192015211858) -APGBank"
        val result = registry.parse(msg, sender = "JD-APGB-T")!!
        assertNull(result.availableBalance)
    }

    @Test
    fun `IPPB debit extracts merchant, DDMMYY date, and Creat reference`() {
        val msg = "Your account has been successfully debited with Rs.199.00 on 280626 towards Netflix " +
            "for Creat REF7654321 -IPPB"
        val result = registry.parse(msg, sender = "VM-IPPB-S")!!
        assertEquals(BigDecimal("199.00"), result.amount)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals("Netflix", result.merchant)
        assertEquals("REF7654321", result.referenceId)
        assertEquals(2026, result.dateTime?.year)
        assertEquals(6, result.dateTime?.monthValue)
        assertEquals(28, result.dateTime?.dayOfMonth)
        assertEquals("8129", result.accountHint) // no mask in this template — falls back to the known IPPB account
        assertNull(result.availableBalance)
        assertEquals("IPPB", result.sourceLabel)
    }

    @Test
    fun `IPPB cash deposit is parsed as a credit with balance`() {
        val msg = "Your IPPB account XXXXXXXX8129 has been credited with Rs.2000 on 05-08-2026 14:30:00 " +
            "by cash deposit at MG Road Branch. Avl Bal Rs. 7000."
        val result = registry.parse(msg, sender = "VM-IPPB-S")!!
        assertEquals(BigDecimal("2000"), result.amount)
        assertEquals(TransactionType.CREDIT, result.type)
        assertNull(result.merchant)
        assertEquals(BigDecimal("7000"), result.availableBalance)
        assertEquals("8129", result.accountHint)
    }

    @Test
    fun `IPPB unparseable message is flagged for review not silently dropped`() {
        val msg = "Your account has been successfully debited with Rs. on 280626 towards Netflix for Creat REF7654321 -IPPB"
        val result = registry.parse(msg, sender = "VM-IPPB-S")!!
        assertEquals(ParseConfidence.UNPARSED, result.confidence)
        assertNull(result.amount)
    }
}
