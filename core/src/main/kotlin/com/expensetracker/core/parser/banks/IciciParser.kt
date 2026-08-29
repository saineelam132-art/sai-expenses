package com.expensetracker.core.parser.banks

import com.expensetracker.core.model.ParseConfidence
import com.expensetracker.core.model.ParsedTransaction
import com.expensetracker.core.model.TransactionType
import com.expensetracker.core.parser.ParseUtils
import com.expensetracker.core.parser.TransactionParser

/**
 * Covers ICICI's templates:
 *  "ICICI Bank Acct XX123 debited with Rs 500.00 on 05-Aug-24; Mohan Shop credited. UPI:123456789012."
 *  "Acct XX123 is debited with INR 500.00 on 05-Aug-24 and credited to mohan@okaxis (UPI Ref no 123456789012)."
 *  "ICICI Bank Acct XX123 credited with Rs 5000.00 on 05-Aug-24 from Mohan Shop (UPI Ref no 123456789012)."
 */
class IciciParser : TransactionParser {
    override val sourceLabel = "ICICI Bank"

    private val bankIdentifier = Regex("""ICICI""", RegexOption.IGNORE_CASE)
    private val debitKeyword = Regex("""\bdebited\b""", RegexOption.IGNORE_CASE)
    private val creditKeyword = Regex("""\bcredited\b""", RegexOption.IGNORE_CASE)
    private val merchantAfterSemicolon = Regex(""";\s*([A-Za-z0-9 &.'\-]+?)\s+credited""", RegexOption.IGNORE_CASE)
    private val merchantCreditedTo = Regex("""credited\s+to\s+([\w.\-@]+)""", RegexOption.IGNORE_CASE)
    private val merchantFrom = Regex("""from\s+([A-Za-z0-9 &.'\-@]+?)\s*\(""", RegexOption.IGNORE_CASE)

    override fun tryParse(message: String, sender: String?): ParsedTransaction? {
        val identified = sender?.contains("ICICI", ignoreCase = true) == true ||
            bankIdentifier.containsMatchIn(message)
        if (!identified) return null

        val isDebit = debitKeyword.containsMatchIn(message)
        val isCredit = creditKeyword.containsMatchIn(message)
        if (!isDebit && !isCredit) return null

        val amount = ParseUtils.findAmount(message)
        // "debited with Rs X; Mohan Shop credited" — treat as a debit even though "credited" appears too.
        val type = if (isDebit) TransactionType.DEBIT else TransactionType.CREDIT
        val merchant = when {
            isDebit -> merchantAfterSemicolon.find(message)?.groupValues?.get(1)
                ?: merchantCreditedTo.find(message)?.groupValues?.get(1)
            else -> merchantFrom.find(message)?.groupValues?.get(1)
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
