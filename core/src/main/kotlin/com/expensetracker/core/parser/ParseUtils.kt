package com.expensetracker.core.parser

import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

/** Shared regex/string helpers reused by every bank-specific parser. */
internal object ParseUtils {

    /** Matches "Rs.500.00", "Rs 500", "INR 1,234.50", "₹850" — the amount group is always index 1. */
    private val AMOUNT_PATTERN = Regex(
        """(?:Rs\.?|INR|₹)\s?([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    private val DATE_PATTERNS = listOf(
        "dd-MM-yy", "dd-MM-yyyy", "dd/MM/yy", "dd/MM/yyyy", "ddMMMyy", "dd-MMM-yy", "dd-MMM-yyyy",
    )

    fun findAmount(message: String): BigDecimal? {
        val match = AMOUNT_PATTERN.find(message) ?: return null
        val cleaned = match.groupValues[1].replace(",", "")
        return cleaned.toBigDecimalOrNull()
    }

    /** Finds the *second* amount occurrence, used for balance fields that appear after the tx amount. */
    fun findAllAmounts(message: String): List<BigDecimal> =
        AMOUNT_PATTERN.findAll(message)
            .mapNotNull { it.groupValues[1].replace(",", "").toBigDecimalOrNull() }
            .toList()

    fun findBalance(message: String): BigDecimal? {
        val balanceRegex = Regex(
            """(?:Avl\s*Bal|Available\s*Balance|Bal)\.?:?\s*(?:Rs\.?|INR|₹)?\s?([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE,
        )
        val match = balanceRegex.find(message) ?: return null
        return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
    }

    fun findAccountHint(message: String): String? {
        val acctRegex = Regex(
            """(?:A/c|Acct|Account|AC)\.?\s*(?:No\.?)?\s*[Xx*]{2,}(\d{2,6})""",
            RegexOption.IGNORE_CASE,
        )
        return acctRegex.find(message)?.groupValues?.get(1)
    }

    fun findReferenceId(message: String): String? {
        val refRegex = Regex(
            """(?:Ref\.?\s*No\.?|RRN|UPI\s*Ref\.?\s*No\.?|UPI:)\s*[:\-]?\s*(\w{6,25})""",
            RegexOption.IGNORE_CASE,
        )
        return refRegex.find(message)?.groupValues?.get(1)
    }

    fun findDate(message: String): LocalDateTime? {
        val dateRegex = Regex(
            """\b(\d{1,2}[-/][A-Za-z0-9]{2,4}[-/]\d{2,4})\b""",
        )
        val raw = dateRegex.find(message)?.groupValues?.get(1) ?: return null
        for (pattern in DATE_PATTERNS) {
            try {
                val formatter = DateTimeFormatterBuilder()
                    .appendPattern(pattern)
                    .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                    .toFormatter(Locale.ENGLISH)
                val parsed = java.time.LocalDate.parse(raw, formatter)
                return parsed.atStartOfDay()
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return null
    }

    /** Strips trailing boilerplate ("If not done by you call...") merchants sometimes get glued to. */
    fun cleanMerchant(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw
            .replace(Regex("""[.,;].*$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .takeIf { it.isNotBlank() }
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = try {
    BigDecimal(this)
} catch (_: NumberFormatException) {
    null
}
