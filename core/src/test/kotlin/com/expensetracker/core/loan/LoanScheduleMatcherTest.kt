package com.expensetracker.core.loan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Built against the real Slice loan schedule (account BW00000082654652, ₹2,082 sanctioned,
 * 12 installments) — including its final installment of 194.29, which differs from the other
 * eleven 194.24 rows by 5 paise and must not be matched interchangeably with them.
 */
class LoanScheduleMatcherTest {

    private val schedule = listOf(
        ScheduleInstallment(1, LocalDate.of(2026, 10, 5), BigDecimal("194.24")),
        ScheduleInstallment(2, LocalDate.of(2026, 11, 5), BigDecimal("194.24")),
        ScheduleInstallment(3, LocalDate.of(2026, 12, 5), BigDecimal("194.24")),
        ScheduleInstallment(4, LocalDate.of(2027, 1, 5), BigDecimal("194.24")),
        ScheduleInstallment(5, LocalDate.of(2027, 2, 5), BigDecimal("194.24")),
        ScheduleInstallment(6, LocalDate.of(2027, 3, 5), BigDecimal("194.24")),
        ScheduleInstallment(7, LocalDate.of(2027, 4, 5), BigDecimal("194.24")),
        ScheduleInstallment(8, LocalDate.of(2027, 5, 5), BigDecimal("194.24")),
        ScheduleInstallment(9, LocalDate.of(2027, 6, 5), BigDecimal("194.24")),
        ScheduleInstallment(10, LocalDate.of(2027, 7, 5), BigDecimal("194.24")),
        ScheduleInstallment(11, LocalDate.of(2027, 8, 5), BigDecimal("194.24")),
        ScheduleInstallment(12, LocalDate.of(2027, 9, 5), BigDecimal("194.29")),
    )

    @Test
    fun `payment on the due date matches that installment`() {
        val match = LoanScheduleMatcher.match(schedule, BigDecimal("194.24"), LocalDate.of(2026, 12, 5))
        assertEquals(3, match?.installmentNumber)
    }

    @Test
    fun `payment a few days late still matches its installment`() {
        val match = LoanScheduleMatcher.match(schedule, BigDecimal("194.24"), LocalDate.of(2027, 2, 9))
        assertEquals(5, match?.installmentNumber)
    }

    @Test
    fun `payment a few days early still matches its installment`() {
        val match = LoanScheduleMatcher.match(schedule, BigDecimal("194.24"), LocalDate.of(2027, 3, 2))
        assertEquals(6, match?.installmentNumber)
    }

    @Test
    fun `final installment matches on its own exact amount, not the common one`() {
        val match = LoanScheduleMatcher.match(schedule, BigDecimal("194.29"), LocalDate.of(2027, 9, 5))
        assertEquals(12, match?.installmentNumber)
    }

    @Test
    fun `the common amount never matches the differently-priced final installment`() {
        // 194.24 on the final due date: every 194.24 row is months away, so nothing is in range.
        val match = LoanScheduleMatcher.match(schedule, BigDecimal("194.24"), LocalDate.of(2027, 9, 5))
        assertNull(match)
    }

    @Test
    fun `already-paid installments are not offered again`() {
        val remaining = schedule.filter { it.installmentNumber > 3 }
        val match = LoanScheduleMatcher.match(remaining, BigDecimal("194.24"), LocalDate.of(2027, 1, 5))
        assertEquals(4, match?.installmentNumber)
    }

    @Test
    fun `an off-schedule amount matches nothing rather than guessing`() {
        // A part-payment/foreclosure/penal charge has no split this app can honestly infer.
        val match = LoanScheduleMatcher.match(schedule, BigDecimal("500.00"), LocalDate.of(2026, 12, 5))
        assertNull(match)
    }

    @Test
    fun `a payment far from any due date matches nothing`() {
        val match = LoanScheduleMatcher.match(schedule, BigDecimal("194.24"), LocalDate.of(2026, 12, 25))
        assertNull(match)
    }

    @Test
    fun `an empty schedule matches nothing`() {
        assertNull(LoanScheduleMatcher.match(emptyList(), BigDecimal("194.24"), LocalDate.of(2026, 12, 5)))
    }
}
