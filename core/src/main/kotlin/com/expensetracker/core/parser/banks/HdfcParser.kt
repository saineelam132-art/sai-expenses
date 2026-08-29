package com.expensetracker.core.parser.banks

import com.expensetracker.core.model.ParseConfidence
import com.expensetracker.core.model.ParsedTransaction
import com.expensetracker.core.model.TransactionType
import com.expensetracker.core.parser.ParseUtils
import com.expensetracker.core.parser.TransactionParser

/**
 * Covers HDFC's UPI templates:
 *  "Rs 500.00 debited from HDFC Bank A/c **1234 on 05-08-24 to VPA mohan@okhdfcbank (UPI Ref No 123456789012)"
 *  "Rs.5000 credited to HDFC Bank A/c **1234 on 05-08-24 from VPA employer@okicici. Avl bal Rs.23400.00"
 */
class HdfcParser : TransactionParser {
    override val sourceLabel = "HDFC Bank"

    private val bankIdentifier = Regex("""HDFC""", RegexOption.IGNORE_CASE)
    private val debitKeyword = Regex("""\bdebited\b""", RegexOption.IGNORE_CASE)
    private val creditKeyword = Regex("""\bcredited\b""", RegexOption.IGNORE_CASE)
    private val vpaDebit = Regex("""to\s+VPA\s+(${ParseUtils.VPA_PATTERN})""", RegexOption.IGNORE_CASE)
    private val vpaCredit = Regex("""from\s+VPA\s+(${ParseUtils.VPA_PATTERN})""", RegexOption.IGNORE_CASE)
    private val plainMerchantDebit = Regex(
        """to\s+([A-Za-z0-9 &.'\-]+?)\s*(?:on|\()""",
        RegexOption.IGNORE_CASE,
    )

    override fun tryParse(message: String, sender: String?): ParsedTransaction? {
        val identified = sender?.contains("HDFC", ignoreCase = true) == true ||
            bankIdentifier.containsMatchIn(message)
        if (!identified) return null

        val isDebit = debitKeyword.containsMatchIn(message)
        val isCredit = creditKeyword.containsMatchIn(message)
        if (!isDebit && !isCredit) return null

        val amount = ParseUtils.findAmount(message)
        val type = if (isDebit) TransactionType.DEBIT else TransactionType.CREDIT
        val merchant = if (isDebit) {
            vpaDebit.find(message)?.groupValues?.get(1) ?: plainMerchantDebit.find(message)?.groupValues?.get(1)
        } else {
            vpaCredit.find(message)?.groupValues?.get(1)
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
