package com.expensetracker.core.parser.banks

import com.expensetracker.core.model.ParseConfidence
import com.expensetracker.core.model.ParsedTransaction
import com.expensetracker.core.model.TransactionType
import com.expensetracker.core.parser.ParseUtils
import com.expensetracker.core.parser.TransactionParser

/**
 * Covers UPI app *notifications* (GPay, PhonePe, Paytm, BHIM) rather than SMS — these apps
 * often don't send SMS at all, so the NotificationListenerService is the only capture path.
 * Formats are less standardized than bank SMS, so this matches on "paid"/"received" verbs:
 *  GPay:    "You paid ₹500 to Mohan Shop" / "₹500 paid to Mohan Shop using Bank XXXX"
 *  PhonePe: "You paid Rs.500 to Mohan Shop via UPI" / "You received Rs.500 from Mohan"
 *  Paytm:   "Paid Rs.500 to Mohan Shop successfully." / "You received Rs. 500 from Mohan"
 */
class UpiAppNotificationParser : TransactionParser {
    override val sourceLabel = "UPI App"

    private val knownPackages = listOf(
        "nbu.paisa.user", // Google Pay
        "phonepe",
        "one97.paytm", // Paytm
        "bhim",
        "npci",
    )

    private val paidKeyword = Regex("""\bpaid\b""", RegexOption.IGNORE_CASE)
    private val receivedKeyword = Regex("""\breceived\b""", RegexOption.IGNORE_CASE)
    private val merchantPaidTo = Regex("""\bto\s+([A-Za-z0-9 &.'\-]+?)(?:\s+using|\s+via|\.|$)""", RegexOption.IGNORE_CASE)
    private val merchantReceivedFrom = Regex("""\bfrom\s+([A-Za-z0-9 &.'\-]+?)(?:\s+using|\s+via|\.|$)""", RegexOption.IGNORE_CASE)

    override fun tryParse(message: String, sender: String?): ParsedTransaction? {
        val isKnownApp = sender != null && knownPackages.any { sender.contains(it, ignoreCase = true) }
        val hasVerb = paidKeyword.containsMatchIn(message) || receivedKeyword.containsMatchIn(message)
        // Only claim messages either from a known UPI app package, or that clearly look like a
        // payment notification body — avoids swallowing unrelated bank SMS as "UPI App".
        if (!isKnownApp && !hasVerb) return null
        if (!hasVerb) return null

        val amount = ParseUtils.findAmount(message)
        val isPaid = paidKeyword.containsMatchIn(message)
        val type = if (isPaid) TransactionType.DEBIT else TransactionType.CREDIT
        val merchant = if (isPaid) {
            merchantPaidTo.find(message)?.groupValues?.get(1)
        } else {
            merchantReceivedFrom.find(message)?.groupValues?.get(1)
        }

        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = ParseUtils.cleanMerchant(merchant),
            dateTime = null, // notifications are timestamped by the OS at capture time instead
            availableBalance = ParseUtils.findBalance(message),
            accountHint = ParseUtils.findAccountHint(message),
            referenceId = ParseUtils.findReferenceId(message),
            sourceLabel = sourceLabel,
            rawMessage = message,
            confidence = if (amount != null) ParseConfidence.HIGH else ParseConfidence.UNPARSED,
        )
    }
}
