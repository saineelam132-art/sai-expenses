package com.expensetracker.core.parser

import com.expensetracker.core.model.ParsedTransaction

/**
 * One parser per bank/app SMS or notification format. Implementations must be pure
 * (no I/O, no Android APIs) so they stay testable on plain JVM and safe to run entirely
 * on-device — this project never uploads message content anywhere.
 */
interface TransactionParser {
    /** Human-readable label, e.g. "SBI", "HDFC Bank", "Generic UPI". Stored with the transaction. */
    val sourceLabel: String

    /**
     * Attempt to parse [message]. [sender] is the SMS sender ID or notification package/app
     * name, used as a cheap pre-filter. Return null if this parser doesn't recognize the format
     * at all — the registry will try the next one. Return a [ParsedTransaction] (possibly with
     * confidence UNPARSED) once you're confident this message *is* a transaction alert.
     */
    fun tryParse(message: String, sender: String?): ParsedTransaction?
}
