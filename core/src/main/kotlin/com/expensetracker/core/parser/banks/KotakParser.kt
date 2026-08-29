package com.expensetracker.core.parser.banks

import com.expensetracker.core.model.ParseConfidence
import com.expensetracker.core.model.ParsedTransaction
import com.expensetracker.core.model.TransactionType
import com.expensetracker.core.parser.ParseUtils
import com.expensetracker.core.parser.TransactionParser

/**
 * Covers Kotak's templates, which use "Sent"/"Received" rather than "debited"/"credited":
 *  "Sent Rs.500.00 from Kotak Bank AC X1234 to mohan@okhdfcbank on 05-08-24.UPI Ref 123456789012."
 *  "Received Rs.5000.00 in your Kotak Bank AC X1234 from EMPLOYER on 05-08-24."
 */
class KotakParser : TransactionParser {
    override val sourceLabel = "Kotak Bank"

    private val bankIdentifier = Regex("""KOTAK""", RegexOption.IGNORE_CASE)
    private val sentKeyword = Regex("""^\s*Sent\b""", RegexOption.IGNORE_CASE)
    private val receivedKeyword = Regex("""^\s*Received\b""", RegexOption.IGNORE_CASE)
    private val merchantSent = Regex("""\bto\s+([\w.\-@]+)\s+on\b""", RegexOption.IGNORE_CASE)
    private val merchantReceived = Regex("""\bfrom\s+([A-Za-z0-9 &.'\-]+?)\s+on\b""", RegexOption.IGNORE_CASE)

    override fun tryParse(message: String, sender: String?): ParsedTransaction? {
        val identified = sender?.contains("KOTAK", ignoreCase = true) == true ||
            bankIdentifier.containsMatchIn(message)
        if (!identified) return null

        val isSent = sentKeyword.containsMatchIn(message)
        val isReceived = receivedKeyword.containsMatchIn(message)
        if (!isSent && !isReceived) return null

        val amount = ParseUtils.findAmount(message)
        val type = if (isSent) TransactionType.DEBIT else TransactionType.CREDIT
        val merchant = if (isSent) {
            merchantSent.find(message)?.groupValues?.get(1)
        } else {
            merchantReceived.find(message)?.groupValues?.get(1)
        }

        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = ParseUtils.cleanMerchant(merchant),
            dateTime = ParseUtils.findDate(message),
            availableBalance = ParseUtils.findBalance(message),
            accountHint = ParseUtils.findAccountHint(message),
            referenceId = ParseUtils.findReferenceId(message),
            sourceLabel = sourceLabel,
            rawMessage = message,
            confidence = if (amount != null) ParseConfidence.HIGH else ParseConfidence.UNPARSED,
        )
    }
}
