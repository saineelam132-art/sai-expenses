package com.expensetracker.core.parser.banks

import com.expensetracker.core.model.ParseConfidence
import com.expensetracker.core.model.ParsedTransaction
import com.expensetracker.core.model.TransactionType
import com.expensetracker.core.parser.ParseUtils
import com.expensetracker.core.parser.TransactionParser

/**
 * Covers Axis Bank's template:
 *  "INR 500.00 debited from A/c no. XX1234 on 05-08-2024 towards UPI/P2M/123456789012/Mohan Shop. Avl Bal INR 23400.00"
 */
class AxisParser : TransactionParser {
    override val sourceLabel = "Axis Bank"

    private val bankIdentifier = Regex("""AXIS""", RegexOption.IGNORE_CASE)
    private val debitKeyword = Regex("""\bdebited\b""", RegexOption.IGNORE_CASE)
    private val creditKeyword = Regex("""\bcredited\b""", RegexOption.IGNORE_CASE)
    private val merchantTowardsUpi = Regex(
        """towards\s+UPI/[^/]+/\d+/([A-Za-z0-9 &'\-]+)""",
        RegexOption.IGNORE_CASE,
    )

    override fun tryParse(message: String, sender: String?): ParsedTransaction? {
        val identified = sender?.contains("AXIS", ignoreCase = true) == true ||
            bankIdentifier.containsMatchIn(message)
        if (!identified) return null

        val isDebit = debitKeyword.containsMatchIn(message)
        val isCredit = creditKeyword.containsMatchIn(message)
        if (!isDebit && !isCredit) return null

        val amount = ParseUtils.findAmount(message)
        val type = if (isDebit) TransactionType.DEBIT else TransactionType.CREDIT
        val merchant = merchantTowardsUpi.find(message)?.groupValues?.get(1)

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
