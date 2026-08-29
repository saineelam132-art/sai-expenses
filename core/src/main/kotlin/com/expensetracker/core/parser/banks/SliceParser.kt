package com.expensetracker.core.parser.banks

import com.expensetracker.core.model.ParseConfidence
import com.expensetracker.core.model.ParsedTransaction
import com.expensetracker.core.model.TransactionType
import com.expensetracker.core.parser.ParseUtils
import com.expensetracker.core.parser.TransactionParser

/**
 * "slice" (a card/lending fintech app) SMS. Uses "sent"/"received" like Kotak's app, but from a
 * different sender, so it needs its own identifier rather than extending KotakParser. Covers:
 *  "Rs. 297 sent from a/c xx7003 on 17-Jul-26 to Neelam Sai Ram Ganesh (UPI Ref: 619880922297).
 *   Not you? Call 08048329999 - slice"
 *  "Rs. 2,000 received in A/c xx7003 on 25-Aug-26 from NORTH EAST SMALL FINANCE BANK via IMPS
 *   (Ref ID: 623701181758). Avl. Bal. Rs. 2,000.05 - slice"
 *
 * Note: registered before [GenericFallbackParser] — that fallback's debit/credit keyword list
 * doesn't include "sent", so without this dedicated parser these messages would go unrecognized
 * entirely rather than merely mis-categorized.
 */
class SliceParser : TransactionParser {
    override val sourceLabel = "slice"

    private val appIdentifier = Regex("""-\s*slice\s*$""", RegexOption.IGNORE_CASE)
    private val sentKeyword = Regex("""\bsent\b""", RegexOption.IGNORE_CASE)
    private val receivedKeyword = Regex("""\breceived\b""", RegexOption.IGNORE_CASE)
    private val merchantSent = Regex("""\bto\s+([A-Za-z0-9 &.'\-]+?)\s*\(""", RegexOption.IGNORE_CASE)
    private val merchantReceivedVia = Regex("""\bfrom\s+([A-Za-z0-9 &.'\-]+?)\s+via\b""", RegexOption.IGNORE_CASE)
    private val merchantReceivedParen = Regex("""\bfrom\s+([A-Za-z0-9 &.'\-]+?)\s*\(""", RegexOption.IGNORE_CASE)

    override fun tryParse(message: String, sender: String?): ParsedTransaction? {
        val identified = appIdentifier.containsMatchIn(message) || sender?.contains("SLC", ignoreCase = true) == true
        if (!identified) return null

        val isSent = sentKeyword.containsMatchIn(message)
        val isReceived = receivedKeyword.containsMatchIn(message)
        if (!isSent && !isReceived) return null

        val type = if (isSent) TransactionType.DEBIT else TransactionType.CREDIT
        val merchant = if (isSent) {
            merchantSent.find(message)?.groupValues?.get(1)
        } else {
            merchantReceivedVia.find(message)?.groupValues?.get(1)
                ?: merchantReceivedParen.find(message)?.groupValues?.get(1)
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
