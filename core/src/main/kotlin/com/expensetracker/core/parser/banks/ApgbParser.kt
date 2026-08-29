package com.expensetracker.core.parser.banks

import com.expensetracker.core.model.ParseConfidence
import com.expensetracker.core.model.ParsedTransaction
import com.expensetracker.core.model.TransactionType
import com.expensetracker.core.parser.ParseUtils
import com.expensetracker.core.parser.TransactionParser

/**
 * Andhra Pradesh Grameena Bank (a regional rural bank). Covers:
 *  "Your a/c no. XXXXXXXXXXX3077 is debited for Rs.60.00 on 01/08/2026 17:08:19 and credited to
 *   VPA ombk.dqracv567083b67temfrr@mbk (UPI Ref no 192015211858) -APGBank"
 * and the mirrored credit phrasing ("is credited for Rs.X ... debited from VPA Y").
 */
class ApgbParser : TransactionParser {
    override val sourceLabel = "APGB"

    private val bankIdentifier = Regex("""APGB""", RegexOption.IGNORE_CASE)
    private val debitKeyword = Regex("""\bdebited\b""", RegexOption.IGNORE_CASE)
    private val creditKeyword = Regex("""\bcredited\b""", RegexOption.IGNORE_CASE)
    // VPAs contain '@', which plain [A-Za-z0-9 &.'-] merchant classes can't span — matched explicitly.
    private val vpaCreditedTo = Regex("""credited\s+to\s+VPA\s+(${ParseUtils.VPA_PATTERN})""", RegexOption.IGNORE_CASE)
    private val vpaDebitedFrom = Regex("""debited\s+from\s+VPA\s+(${ParseUtils.VPA_PATTERN})""", RegexOption.IGNORE_CASE)

    override fun tryParse(message: String, sender: String?): ParsedTransaction? {
        val identified = sender?.contains("APGB", ignoreCase = true) == true || bankIdentifier.containsMatchIn(message)
        if (!identified) return null

        val isDebit = debitKeyword.containsMatchIn(message)
        val isCredit = creditKeyword.containsMatchIn(message)
        if (!isDebit && !isCredit) return null

        // "is debited for X and credited to Y" mentions both verbs — the leading "is <verb>"
        // right after the account number is the actual transaction direction.
        val isActuallyDebit = Regex("""is\s+debited""", RegexOption.IGNORE_CASE).containsMatchIn(message)
        val type = if (isActuallyDebit) TransactionType.DEBIT else TransactionType.CREDIT
        val merchant = if (type == TransactionType.DEBIT) {
            vpaCreditedTo.find(message)?.groupValues?.get(1)
        } else {
            vpaDebitedFrom.find(message)?.groupValues?.get(1)
        }

        val amount = ParseUtils.findAmount(message)
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
