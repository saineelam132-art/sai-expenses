package com.expensetracker.core.parser

import com.expensetracker.core.model.ParseConfidence
import com.expensetracker.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class BankParserTest {
    private val registry = ParserRegistry()

    @Test
    fun `SBI debit with transfer to merchant`() {
        val msg = "Dear Customer, Rs.500.00 debited from A/c XX1234 on 05-08-24 transfer to MOHAN SHOP Ref No 123456789012. If not done by you, forward this SMS to 09449112211 -SBI"
        val result = registry.parse(msg, sender = "AD-SBIINB")!!
        assertEquals(BigDecimal("500.00"), result.amount)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals("MOHAN SHOP", result.merchant)
        assertEquals("1234", result.accountHint)
        assertEquals("123456789012", result.referenceId)
        assertEquals(ParseConfidence.HIGH, result.confidence)
        assertEquals("SBI", result.sourceLabel)
    }

    @Test
    fun `SBI credit from employer`() {
        val msg = "Dear Customer, Rs.50000.00 credited to A/c XX1234 on 05-08-24 by transfer from EMPLOYER LTD Ref No 123456789099 -SBI"
        val result = registry.parse(msg, sender = "SBIINB")!!
        assertEquals(TransactionType.CREDIT, result.type)
        assertEquals(BigDecimal("50000.00"), result.amount)
        assertEquals("EMPLOYER LTD", result.merchant)
    }

    @Test
    fun `HDFC debit to VPA with balance`() {
        val msg = "Rs 850.00 debited from HDFC Bank A/c **1234 on 05-08-24 to VPA swiggy@okhdfcbank (UPI Ref No 123456789012). Avl Bal Rs.23400.00"
        val result = registry.parse(msg, sender = "HDFCBK")!!
        assertEquals(BigDecimal("850.00"), result.amount)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals("swiggy@okhdfcbank", result.merchant)
        assertEquals(BigDecimal("23400.00"), result.availableBalance)
        assertEquals("HDFC Bank", result.sourceLabel)
    }

    @Test
    fun `HDFC credit from VPA`() {
        val msg = "Rs.50000 credited to HDFC Bank A/c **1234 on 05-08-24 from VPA employer@okicici. Avl bal Rs.73400.00"
        val result = registry.parse(msg, sender = "HDFCBK")!!
        assertEquals(TransactionType.CREDIT, result.type)
        assertEquals("employer@okicici", result.merchant)
    }

    @Test
    fun `ICICI debit with semicolon merchant`() {
        val msg = "ICICI Bank Acct XX123 debited with Rs 500.00 on 05-Aug-24; Mohan Shop credited. UPI:123456789012. Call 18002662 for dispute"
        val result = registry.parse(msg, sender = "ICICIB")!!
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals("Mohan Shop", result.merchant)
        assertEquals(BigDecimal("500.00"), result.amount)
    }

    @Test
    fun `ICICI debit credited to VPA form`() {
        val msg = "Dear Customer, Acct XX123 is debited with INR 500.00 on 05-Aug-24 and credited to mohan@okaxis (UPI Ref no 123456789012)."
        val result = registry.parse(msg, sender = "ICICIB")!!
        assertEquals("mohan@okaxis", result.merchant)
    }

    @Test
    fun `Axis debit towards UPI P2M`() {
        val msg = "INR 500.00 debited from A/c no. XX1234 on 05-08-2024 towards UPI/P2M/123456789012/Mohan Shop. Avl Bal INR 23400.00"
        val result = registry.parse(msg, sender = "AXISBK")!!
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals("Mohan Shop", result.merchant)
        assertEquals(BigDecimal("23400.00"), result.availableBalance)
    }

    @Test
    fun `Kotak sent to VPA`() {
        val msg = "Sent Rs.500.00 from Kotak Bank AC X1234 to mohan@okhdfcbank on 05-08-24.UPI Ref 123456789012."
        val result = registry.parse(msg, sender = "KOTAKB")!!
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals("mohan@okhdfcbank", result.merchant)
    }

    @Test
    fun `Kotak received from name`() {
        val msg = "Received Rs.50000.00 in your Kotak Bank AC X1234 from EMPLOYER on 05-08-24."
        val result = registry.parse(msg, sender = "KOTAKB")!!
        assertEquals(TransactionType.CREDIT, result.type)
        assertEquals("EMPLOYER", result.merchant)
    }

    @Test
    fun `GPay style notification you paid`() {
        val msg = "You paid ₹500 to Mohan Shop"
        val result = registry.parse(msg, sender = "com.google.android.apps.nbu.paisa.user")!!
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals(BigDecimal("500"), result.amount)
        assertEquals("Mohan Shop", result.merchant)
        assertEquals("UPI App", result.sourceLabel)
    }

    @Test
    fun `PhonePe received from`() {
        val msg = "You received Rs.500 from Mohan"
        val result = registry.parse(msg, sender = "com.phonepe.app")!!
        assertEquals(TransactionType.CREDIT, result.type)
        assertEquals("Mohan", result.merchant)
    }

    @Test
    fun `generic fallback catches unknown bank format`() {
        val msg = "Rs. 999.00 debited from your account for a purchase at Local Store on 01-01-25"
        val result = registry.parse(msg, sender = "XX-UNKNWN")!!
        assertEquals(BigDecimal("999.00"), result.amount)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals(ParseConfidence.LOW, result.confidence)
    }

    @Test
    fun `generic fallback prefers the real merchant after 'at' over 'your account' boilerplate`() {
        val msg = "Rs. 2500.00 debited from your account for a purchase at CITY ELECTRONICS on 29-08-26"
        val result = registry.parse(msg, sender = "XX-UNKNWN")!!
        assertEquals("CITY ELECTRONICS", result.merchant)
    }

    @Test
    fun `unparsed transaction-like message flagged for review not dropped`() {
        val msg = "Your account has been debited. Contact bank for details."
        val result = registry.parse(msg, sender = "XX-UNKNWN")!!
        assertNull(result.amount)
        assertTrue(result.needsReview)
        assertEquals(ParseConfidence.UNPARSED, result.confidence)
    }

    @Test
    fun `OTP message is not treated as a transaction`() {
        val msg = "123456 is your OTP for transaction of Rs.500. Do not share with anyone."
        val result = registry.parse(msg, sender = "XX-BANK")
        assertNull(result)
    }

    @Test
    fun `promotional message is not treated as a transaction`() {
        val msg = "Get 50% off on your next Swiggy order! Use code SAVE50."
        val result = registry.parse(msg, sender = "XX-PROMO")
        assertNull(result)
    }

    @Test
    fun `large transaction amount parses correctly for threshold checks`() {
        val msg = "Rs.10000.00 debited from A/c XX1234 on 05-08-24 transfer to LAPTOP STORE Ref No 123456789012 -SBI"
        val result = registry.parse(msg, sender = "SBIINB")!!
        assertNotNull(result.amount)
        assertTrue(result.amount!! >= BigDecimal("5000"))
    }

    // --- Real-world messages below (captured from actual devices, personal names swapped for
    // placeholders) that surfaced format-drift gaps in the original six-bank/app coverage. ---

    @Test
    fun `APGB debit with VPA merchant containing at-sign`() {
        val msg = "Your a/c no. XXXXXXXXXXX3077 is debited for Rs.60.00 on 01/08/2026 17:08:19 and credited to VPA ombk.dqracv567083b67temfrr@mbk (UPI Ref no 192015211858) -APGBank"
        val result = registry.parse(msg, sender = "JD-APGB-T")!!
        assertEquals(BigDecimal("60.00"), result.amount)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals("ombk.dqracv567083b67temfrr@mbk", result.merchant)
        assertEquals("3077", result.accountHint)
        assertEquals("APGB", result.sourceLabel)
    }

    @Test
    fun `SBI UPI-user debit with bare amount (no currency prefix) and single-X account mask`() {
        val msg = "Dear UPI user A/C X2436 debited by 5.00 on date 26Aug26 trf to JOHN DOE Refno 181829650372 If not u? call-1800111109 for other services-18001234-SBI"
        val result = registry.parse(msg, sender = "VA-SBIUPI-S")!!
        assertEquals(BigDecimal("5.00"), result.amount)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals("JOHN DOE", result.merchant)
        assertEquals("2436", result.accountHint)
    }

    @Test
    fun `SBI credit via IMPS from a mobile-linked account (no 'from name' phrasing)`() {
        val msg = "Dear Customer, Your a/c no. XXXXXXXX2436 is credited by Rs.2901.51 on 30-06-26 by a/c linked to mobile 6XXXXXX111-GROWW INVE (IMPS Ref# 618117321894)-SBI"
        val result = registry.parse(msg, sender = "JK-SBIPSG-T")!!
        assertEquals(BigDecimal("2901.51"), result.amount)
        assertEquals(TransactionType.CREDIT, result.type)
        assertEquals("GROWW INVE", result.merchant)
    }

    @Test
    fun `slice debit uses 'sent to' phrasing not covered by the generic fallback`() {
        val msg = "Rs. 1,200 sent from a/c xx7003 on 19-Jul-26 to PHYSICSWALLAH (UPI Ref: 620008432795). Not you? Call 08048329999 - slice"
        val result = registry.parse(msg, sender = "VA-SLCBNK-S")!!
        assertEquals(BigDecimal("1200"), result.amount)
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals("PHYSICSWALLAH", result.merchant)
        assertEquals("7003", result.accountHint)
        assertEquals("slice", result.sourceLabel)
    }

    @Test
    fun `slice credit via IMPS with balance field using dotted Avl Bal punctuation`() {
        val msg = "Rs. 2,000 received in A/c xx7003 on 25-Aug-26 from NORTH EAST SMALL FINANCE BANK via IMPS (Ref ID: 623701181758). Avl. Bal. Rs. 2,000.05 - slice"
        val result = registry.parse(msg, sender = "VA-SLCBNK-S")!!
        assertEquals(BigDecimal("2000"), result.amount)
        assertEquals(TransactionType.CREDIT, result.type)
        assertEquals("NORTH EAST SMALL FINANCE BANK", result.merchant)
        assertEquals(BigDecimal("2000.05"), result.availableBalance)
    }
}
