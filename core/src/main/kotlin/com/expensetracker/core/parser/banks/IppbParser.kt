package com.expensetracker.core.parser.banks

import com.expensetracker.core.model.ParseConfidence
import com.expensetracker.core.model.ParsedTransaction
import com.expensetracker.core.model.TransactionType
import com.expensetracker.core.parser.ParseUtils
import com.expensetracker.core.parser.TransactionParser

/**
 * India Post Payments Bank. Covers two real transaction templates (AutoPay mandate lifecycle
 * messages, declined-payment notices, and OTPs are NOT transactions at all — those are handled
 * upstream by [com.expensetracker.core.parser.NonTransactionClassifier] before this parser is
 * ever reached in production; this parser only needs to recognize the two shapes below):
 *  Debit:  "Your account has been successfully debited with Rs.500 on 280626 towards Netflix
 *           for Creat REF123456 -IPPB" (date is DDMMYY, no separators)
 *  Credit: "Your IPPB account XXXXXXXX8129 has been credited with Rs.500 on 05-08-2026 14:30:00
 *           by cash deposit at MG Road Branch. Avl Bal Rs. 5000."
 * The credit template's amount is matched with its own anchored pattern (optional currency
 * symbol) rather than the shared [ParseUtils.findAmount] — that function takes the *first*
 * Rs./INR figure in the message, which here would incorrectly grab the trailing "Avl Bal"
 * balance instead of the actual credited amount if the source message ever omits the currency
 * symbol before the amount itself.
 */
class IppbParser : TransactionParser {
    override val sourceLabel = "IPPB"

    // The single dedicated IPPB account this app is configured for — used only as a fallback
    // when a message (like the debit template) doesn't carry an account mask of its own, so
    // every IPPB transaction still attributes to the same account bucket consistently.
    private val defaultAccountHint = "8129"

    private val debitIdentifier = Regex("""successfully\s+debited""", RegexOption.IGNORE_CASE)
    private val cashDepositIdentifier = Regex("""credited\s+with.*cash\s+deposit""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    private val debitMerchant = Regex("""towards\s+([A-Za-z0-9 &.'\-]+?)\s+for\s+Creat\b""", RegexOption.IGNORE_CASE)
    private val debitDate = Regex("""\bon\s+(\d{6})\s+towards\b""", RegexOption.IGNORE_CASE)
    private val debitReference = Regex("""Creat\s+([\w-]+)""", RegexOption.IGNORE_CASE)

    private val creditAmount = Regex(
        """credited\s+with\s+(?:Rs\.?|INR|₹)?\s?([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s+on""",
        RegexOption.IGNORE_CASE,
    )

    override fun tryParse(message: String, sender: String?): ParsedTransaction? {
        val identified = sender?.contains("IPPB", ignoreCase = true) == true || message.contains("IPPB", ignoreCase = true)
        if (!identified) return null

        val isDebit = debitIdentifier.containsMatchIn(message)
        val isCashDeposit = cashDepositIdentifier.containsMatchIn(message)
        if (!isDebit && !isCashDeposit) return null // e.g. a mandate/declined/OTP message — not this parser's job

        return if (isDebit) parseDebit(message) else parseCashDeposit(message)
    }

    private fun parseDebit(message: String): ParsedTransaction {
        val dateRaw = debitDate.find(message)?.groupValues?.get(1)
        val amount = ParseUtils.findAmount(message)
        return ParsedTransaction(
            amount = amount,
            type = TransactionType.DEBIT,
            merchant = ParseUtils.cleanMerchant(debitMerchant.find(message)?.groupValues?.get(1)),
            dateTime = dateRaw?.let(ParseUtils::parseDdMMyy)?.atStartOfDay(),
            availableBalance = null, // this template never carries one
            accountHint = ParseUtils.findAccountHint(message) ?: defaultAccountHint,
            referenceId = debitReference.find(message)?.groupValues?.get(1) ?: ParseUtils.findReferenceId(message),
            sourceLabel = sourceLabel,
            rawMessage = message,
            confidence = if (amount != null) ParseConfidence.HIGH else ParseConfidence.UNPARSED,
        )
    }

    private fun parseCashDeposit(message: String): ParsedTransaction {
        val amount = creditAmount.find(message)?.groupValues?.get(1)?.replace(",", "")?.toBigDecimalOrNull()
            ?: ParseUtils.findAmount(message)
        return ParsedTransaction(
            amount = amount,
            type = TransactionType.CREDIT,
            merchant = null, // a branch name isn't a payee
            dateTime = ParseUtils.findDate(message),
            availableBalance = ParseUtils.findBalance(message),
            accountHint = ParseUtils.findAccountHint(message) ?: defaultAccountHint,
            referenceId = ParseUtils.findReferenceId(message),
            sourceLabel = sourceLabel,
            rawMessage = message,
            confidence = if (amount != null) ParseConfidence.HIGH else ParseConfidence.UNPARSED,
        )
    }
}

private fun String.toBigDecimalOrNull(): java.math.BigDecimal? = try {
    java.math.BigDecimal(this)
} catch (_: NumberFormatException) {
    null
}
