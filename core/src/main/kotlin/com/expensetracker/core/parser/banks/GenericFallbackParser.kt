package com.expensetracker.core.parser.banks

import com.expensetracker.core.model.ParseConfidence
import com.expensetracker.core.model.ParsedTransaction
import com.expensetracker.core.model.TransactionType
import com.expensetracker.core.parser.ParseUtils
import com.expensetracker.core.parser.TransactionParser

/**
 * Last-resort parser tried when no bank/app-specific parser recognizes the message.
 * Handles the generic "Rs./INR <amount> debited/credited" shape that most banks fall back to
 * for formats we haven't written a dedicated parser for yet. If the message clearly reads like
 * a transaction alert but we can't confidently pull the amount, we still return a result (with
 * ParseConfidence.UNPARSED) instead of returning null — the caller surfaces this as
 * "Uncategorized/Needs review" rather than silently dropping the message. Messages that don't
 * even look transactional (OTPs, promos) return null so they never enter the transaction list.
 */
class GenericFallbackParser : TransactionParser {
    override val sourceLabel = "Unknown"

    private val excludeKeywords = Regex(
        """\b(OTP|one[\s-]?time\s*password|verification\s*code)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val debitKeywords = Regex(
        """\b(debited|spent|withdrawn|debit\s*of|paid|purchase\s*of)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val creditKeywords = Regex(
        """\b(credited|received|deposit\s*of|credit\s*of)\b""",
        RegexOption.IGNORE_CASE,
    )
    // Tried in priority order: "at"/"to"/"towards" reliably introduce the actual payee in most
    // bank phrasing, while "from" is just as likely to precede boilerplate ("debited from your
    // account") as an actual sender name — so it's tried last, and its boilerplate matches
    // ("your account", "your a/c") are rejected outright rather than stored as a fake merchant.
    private val merchantGuessPatterns = listOf(
        Regex("""\bat\s+([A-Za-z0-9 &.'\-]{2,40}?)(?:\s+on\b|\s+for\b|[.,;]|$)""", RegexOption.IGNORE_CASE),
        Regex("""\bto\s+([A-Za-z0-9 &.'\-]{2,40}?)(?:\s+on\b|\s+for\b|[.,;]|$)""", RegexOption.IGNORE_CASE),
        Regex("""\btowards\s+([A-Za-z0-9 &.'\-]{2,40}?)(?:\s+on\b|\s+for\b|[.,;]|$)""", RegexOption.IGNORE_CASE),
        Regex("""\bfrom\s+([A-Za-z0-9 &.'\-]{2,40}?)(?:\s+on\b|\s+for\b|[.,;]|$)""", RegexOption.IGNORE_CASE),
    )
    private val boilerplateMerchant = Regex(
        """^(your|the)\s+(a/?c|account|bank\s*account)\b""",
        RegexOption.IGNORE_CASE,
    )

    private fun guessMerchant(message: String): String? {
        for (pattern in merchantGuessPatterns) {
            val candidate = pattern.find(message)?.groupValues?.get(1) ?: continue
            if (!boilerplateMerchant.containsMatchIn(candidate.trim())) return candidate
        }
        return null
    }

    override fun tryParse(message: String, sender: String?): ParsedTransaction? {
        if (excludeKeywords.containsMatchIn(message)) return null

        val isDebit = debitKeywords.containsMatchIn(message)
        val isCredit = creditKeywords.containsMatchIn(message)
        if (!isDebit && !isCredit) return null // doesn't look like a transaction message at all

        val amount = ParseUtils.findAmount(message)
        val type = when {
            isDebit -> TransactionType.DEBIT
            isCredit -> TransactionType.CREDIT
            else -> TransactionType.UNKNOWN
        }

        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = ParseUtils.cleanMerchant(guessMerchant(message)),
            dateTime = ParseUtils.findDate(message),
            availableBalance = ParseUtils.findBalance(message),
            accountHint = ParseUtils.findAccountHint(message),
            referenceId = ParseUtils.findReferenceId(message),
            sourceLabel = sourceLabel,
            rawMessage = message,
            confidence = if (amount != null) ParseConfidence.LOW else ParseConfidence.UNPARSED,
        )
    }
}
