package com.expensetracker.core.parser.banks

import com.expensetracker.core.model.ParseConfidence
import com.expensetracker.core.model.ParsedTransaction
import com.expensetracker.core.model.TransactionType
import com.expensetracker.core.parser.ParseUtils
import com.expensetracker.core.parser.TransactionParser

/**
 * Covers SBI's two common templates:
 *  "Rs.500.00 debited from A/c XX1234 on 05-08-24 transfer to MOHAN SHOP Ref No 123456789012 -SBI"
 *  "A/C X1234 debited by 500.0 on date 05Aug24 trf to Mohan Shop Refno 123456789012 -SBI"
 *  "Rs.5000.00 credited to A/c XX1234 on 05-08-24 by transfer from EMPLOYER LTD Ref No ... -SBI"
 */
class SbiParser : TransactionParser {
    override val sourceLabel = "SBI"

    private val bankIdentifier = Regex("""(-SBI\b|\bSBI\b)""", RegexOption.IGNORE_CASE)
    private val debitKeyword = Regex("""\bdebited\b""", RegexOption.IGNORE_CASE)
    private val creditKeyword = Regex("""\bcredited\b""", RegexOption.IGNORE_CASE)
    private val merchantDebit = Regex(
        """(?:trf|transfer)\s+to\s+([A-Za-z0-9 &.'\-]+?)\s+Ref""",
        RegexOption.IGNORE_CASE,
    )
    private val merchantCredit = Regex(
        """from\s+([A-Za-z0-9 &.'\-]+?)\s+Ref""",
        RegexOption.IGNORE_CASE,
    )

    override fun tryParse(message: String, sender: String?): ParsedTransaction? {
        val identifiedBySender = sender?.contains("SBI", ignoreCase = true) == true
        val identifiedByBody = bankIdentifier.containsMatchIn(message)
        if (!identifiedBySender && !identifiedByBody) return null

        val isDebit = debitKeyword.containsMatchIn(message)
        val isCredit = creditKeyword.containsMatchIn(message)
        if (!isDebit && !isCredit) return null

        val amount = ParseUtils.findAmount(message)
        val type = if (isDebit) TransactionType.DEBIT else TransactionType.CREDIT
        val merchant = if (isDebit) {
            merchantDebit.find(message)?.groupValues?.get(1)
        } else {
            merchantCredit.find(message)?.groupValues?.get(1)
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
