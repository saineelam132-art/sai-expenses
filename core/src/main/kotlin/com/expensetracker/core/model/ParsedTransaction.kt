package com.expensetracker.core.model

import java.math.BigDecimal
import java.time.LocalDateTime

/** How much we trust a parse result. */
enum class ParseConfidence {
    /** Matched a known bank/app-specific pattern. */
    HIGH,
    /** Matched only the generic "Rs./INR <amount> debited/credited" fallback. */
    LOW,
    /** Looked like a transaction message but nothing extracted a usable amount. Needs manual review. */
    UNPARSED,
}

/**
 * Result of running a raw SMS/notification body through a [TransactionParser].
 * `merchant` and `balance` are frequently absent depending on the bank's message format —
 * downstream code must treat them as optional, not silently default them.
 */
data class ParsedTransaction(
    val amount: BigDecimal?,
    val type: TransactionType,
    val merchant: String?,
    val dateTime: LocalDateTime?,
    val availableBalance: BigDecimal?,
    val accountHint: String?,
    val referenceId: String?,
    val sourceLabel: String,
    val rawMessage: String,
    val confidence: ParseConfidence,
) {
    val needsReview: Boolean get() = confidence == ParseConfidence.UNPARSED || amount == null
}
