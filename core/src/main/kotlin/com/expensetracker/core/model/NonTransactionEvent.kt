package com.expensetracker.core.model

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Result of [com.expensetracker.core.parser.NonTransactionClassifier.classify] — a message that
 * is clearly *not* a transaction, so it must never reach [com.expensetracker.core.parser.ParserRegistry]
 * or land in the transaction/review list at all. Distinct from a low-confidence
 * [ParsedTransaction] with `needsReview = true`, which *is* still a transaction, just an
 * uncertain one.
 */
sealed class NonTransactionEvent {
    /** OTPs, promotional/reminder text, broker SEBI balance-disclosure pings — discard silently,
     * not even logged to "needs review". */
    data object Discard : NonTransactionEvent()

    /** Nothing was debited/credited, but the message carries a fresh balance figure worth
     * recording against the account (e.g. a declined-payment notice). */
    data class BalanceOnly(val balance: BigDecimal, val accountHint: String?, val sourceLabel: String) : NonTransactionEvent()

    /** A UPI AutoPay mandate lifecycle message — informational about a *future* recurring
     * charge, not a transaction that happened. */
    data class MandateEvent(
        val action: MandateAction,
        val merchant: String?,
        val amount: BigDecimal?,
        val referenceId: String?,
        val validFrom: LocalDate?,
        val validTo: LocalDate?,
        val sourceLabel: String,
    ) : NonTransactionEvent()
}

enum class MandateAction { CREATED, PAUSED, REVOKED }
