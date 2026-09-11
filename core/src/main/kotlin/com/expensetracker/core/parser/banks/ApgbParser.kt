package com.expensetracker.core.parser.banks

import com.expensetracker.core.model.ParseConfidence
import com.expensetracker.core.model.ParsedTransaction
import com.expensetracker.core.model.TransactionType
import com.expensetracker.core.parser.ParseUtils
import com.expensetracker.core.parser.TransactionParser

/**
 * Andhra Pradesh Grameena Bank (a regional rural bank). Covers two distinct real templates:
 *  Debit:  "Your a/c no. XXXXXXXXXXX3077 is debited for Rs.60.00 on 01/08/2026 17:08:19 and
 *           credited to VPA ombk.dqracv567083b67temfrr@mbk (UPI Ref no 192015211858) -APGBank"
 *  Credit: "Dear Customer, An amount of Rs.500/- is credited in your A/c XXXX3077 on
 *           01-08-2026, UPI Ref No:192015211858. At present A/c Balance is Rs.5000 -APGBank"
 * The credit template never names a payer — merchant is null for it. Note the two templates use
 * different date shapes (DD/MM/YYYY HH:MM:SS vs DD-MM-YYYY); [ParseUtils.findDate] already
 * handles both without needing to know which template produced the message.
 */
class ApgbParser : TransactionParser {
    override val sourceLabel = "APGB"

    private val bankIdentifier = Regex("""APGB""", RegexOption.IGNORE_CASE)
    private val debitKeyword = Regex("""is\s+debited""", RegexOption.IGNORE_CASE)
    private val creditKeyword = Regex("""is\s+credited\s+in\s+your""", RegexOption.IGNORE_CASE)
    // VPAs contain '@', which plain [A-Za-z0-9 &.'-] merchant classes can't span — matched explicitly.
    private val vpaCreditedTo = Regex("""credited\s+to\s+VPA\s+(${ParseUtils.VPA_PATTERN})""", RegexOption.IGNORE_CASE)

    override fun tryParse(message: String, sender: String?): ParsedTransaction? {
        val identified = sender?.contains("APGB", ignoreCase = true) == true || bankIdentifier.containsMatchIn(message)
        if (!identified) return null

        val isDebit = debitKeyword.containsMatchIn(message)
        val isCredit = creditKeyword.containsMatchIn(message)
        if (!isDebit && !isCredit) return null

        val type = if (isDebit) TransactionType.DEBIT else TransactionType.CREDIT
        // The credit template never names a payer.
        val merchant = if (isDebit) vpaCreditedTo.find(message)?.groupValues?.get(1) else null

        val amount = ParseUtils.findAmount(message)
        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = ParseUtils.cleanMerchant(merchant),
            dateTime = ParseUtils.findDate(message),
            // The debit template never carries a balance — don't invent one; wait for a message
            // that actually has it (a credit, or a future dedicated balance-check format).
            availableBalance = if (isCredit) ParseUtils.findBalance(message) else null,
            accountHint = ParseUtils.findAccountHint(message),
            referenceId = ParseUtils.findReferenceId(message),
            sourceLabel = sourceLabel,
            rawMessage = message,
            confidence = if (amount != null) ParseConfidence.HIGH else ParseConfidence.UNPARSED,
        )
    }
}
