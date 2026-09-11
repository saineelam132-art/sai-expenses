package com.expensetracker.core.parser

import com.expensetracker.core.model.MandateAction
import com.expensetracker.core.model.NonTransactionEvent

/**
 * Runs *before* [ParserRegistry] — messages this recognizes are never transactions at all (not
 * even a low-confidence "needs review" one), so they must never reach the transaction parsers or
 * the transaction/review list. Handles, in order:
 *  1. OTPs — discarded outright.
 *  2. Broker SEBI balance-disclosure pings (Angel One, Groww "Fund bal ... Securities bal") —
 *     periodic regulatory pings, not transactions, from *any* sender.
 *  3. UPI AutoPay mandate lifecycle messages (created/paused/revoked) — informational about a
 *     *future* recurring charge, not something that happened.
 *  4. A payment declined message that still carries a fresh balance figure — nothing moved, but
 *     the balance is worth recording.
 *
 * Returns null if the message doesn't match any of these — the caller should fall through to
 * [ParserRegistry] as normal.
 */
object NonTransactionClassifier {
    private val otpPattern = Regex(
        """\b(OTP|one[\s-]?time\s*password|verification\s*code|Never\s*Share\s*OTP)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val brokerDisclosurePattern = Regex(
        """reported\s+your\s+Fund\s+bal.*Securities\s+bal""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val mandateKeyword = Regex("""AutoPay\s*Mandate""", RegexOption.IGNORE_CASE)
    private val mandateMerchant = Regex("""towards\s+([A-Za-z0-9 &.'\-]+?)\s+(?:from|with|for)\b""", RegexOption.IGNORE_CASE)
    private val mandateDateRange = Regex("""from\s+(\d{6})\s+to\s+(\d{6})""", RegexOption.IGNORE_CASE)
    private val mandateReference = Regex("""(\w{6,20})\s+-\s*IPPB\s*$""", RegexOption.IGNORE_CASE)

    private val declinedPattern = Regex("""declined""", RegexOption.IGNORE_CASE)
    private val avlBalPattern = Regex("""Avl\s*bal""", RegexOption.IGNORE_CASE)

    fun classify(message: String, sender: String?): NonTransactionEvent? {
        if (otpPattern.containsMatchIn(message)) return NonTransactionEvent.Discard
        if (brokerDisclosurePattern.containsMatchIn(message)) return NonTransactionEvent.Discard

        if (mandateKeyword.containsMatchIn(message)) {
            return parseMandateEvent(message, sender)
        }

        if (declinedPattern.containsMatchIn(message) && avlBalPattern.containsMatchIn(message)) {
            val balance = ParseUtils.findBalance(message) ?: return NonTransactionEvent.Discard
            return NonTransactionEvent.BalanceOnly(balance, ParseUtils.findAccountHint(message), sender ?: "Unknown")
        }

        return null
    }

    private fun parseMandateEvent(message: String, sender: String?): NonTransactionEvent.MandateEvent {
        val action = when {
            message.contains("paused", ignoreCase = true) -> MandateAction.PAUSED
            message.contains("revoked", ignoreCase = true) -> MandateAction.REVOKED
            else -> MandateAction.CREATED
        }
        val merchant = ParseUtils.cleanMerchant(mandateMerchant.find(message)?.groupValues?.get(1))
        val amount = ParseUtils.findAmount(message)
        val dateRange = mandateDateRange.find(message)
        val validFrom = dateRange?.groupValues?.get(1)?.let(ParseUtils::parseDdMMyy)
        val validTo = dateRange?.groupValues?.get(2)?.let(ParseUtils::parseDdMMyy)
        val referenceId = mandateReference.find(message)?.groupValues?.get(1)

        return NonTransactionEvent.MandateEvent(
            action = action,
            merchant = merchant,
            amount = amount,
            referenceId = referenceId,
            validFrom = validFrom,
            validTo = validTo,
            sourceLabel = sender ?: "IPPB",
        )
    }
}
