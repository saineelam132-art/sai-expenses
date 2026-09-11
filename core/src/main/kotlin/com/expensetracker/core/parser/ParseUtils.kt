package com.expensetracker.core.parser

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

/** Shared regex/string helpers reused by every bank-specific parser. */
internal object ParseUtils {

    /**
     * Local-part@domain fragment for VPA/email extraction regexes. The domain half is
     * `[\w-]+(?:\.[\w-]+)*` rather than a flat `[\w.-]+` so it never swallows a sentence-ending
     * '.' that happens to follow the VPA with no space (e.g. "...from VPA employer@okicici. Avl
     * bal..." — a flat class would capture "okicici." including that trailing dot).
     */
    const val VPA_PATTERN = """[\w.\-]+@[\w-]+(?:\.[\w-]+)*"""

    /** Matches "Rs.500.00", "Rs 500", "INR 1,234.50", "₹850" — the amount group is always index 1. */
    private val AMOUNT_PATTERN = Regex(
        """(?:Rs\.?|INR|₹)\s?([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Some real SBI SMS state the amount as "debited by 5.00"/"credited by Rs.2901.51" — the
     * currency symbol is optional in this phrasing. Tried before the generic [AMOUNT_PATTERN]
     * so it wins when present, since it's anchored to the actual debit/credit verb rather than
     * just "the first currency-looking number in the message" (which could be a balance, ref, etc).
     */
    private val VERB_ANCHORED_AMOUNT_PATTERN = Regex(
        """(?:debited|credited)\s+(?:by|for)\s+(?:Rs\.?|INR|₹)?\s?([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    private val DATE_PATTERNS = listOf(
        "dd-MM-yy", "dd-MM-yyyy", "dd/MM/yy", "dd/MM/yyyy", "ddMMMyy", "dd-MMM-yy", "dd-MMM-yyyy",
    )

    fun findAmount(message: String): BigDecimal? {
        val match = VERB_ANCHORED_AMOUNT_PATTERN.find(message) ?: AMOUNT_PATTERN.find(message) ?: return null
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
            // "A/c Balance is Rs.X" / "Balance is Rs.X" (APGB's credit format) need the literal
            // "is" consumed as part of the label — without it, the gap between "Balance" and the
            // amount ("...Balance is Rs.5000") stops the match right before the connective word.
            """(?:Avl\.?\s*Bal|Available\s*Balance|A/c\s*Balance\s+is|Balance\s+is|Bal)\.?:?\s*(?:Rs\.?|INR|₹)?\s?([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE,
        )
        val match = balanceRegex.find(message) ?: return null
        return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
    }

    fun findAccountHint(message: String): String? {
        // Masking varies by bank: some use a single "X" ("A/C X2436"), others many ("A/c XX1234"
        // or "a/c no. XXXXXXXXXXX3077") — {1,} instead of {2,} covers both.
        val acctRegex = Regex(
            """(?:A/c|Acct|Account|AC)\.?\s*(?:No\.?)?\s*[Xx*]{1,}(\d{2,6})""",
            RegexOption.IGNORE_CASE,
        )
        return acctRegex.find(message)?.groupValues?.get(1)
    }

    fun findReferenceId(message: String): String? {
        val refRegex = Regex(
            """(?:Ref\.?\s*No\.?|Ref\s*ID|Ref\s*#|RRN|UPI\s*Ref\.?\s*No\.?|UPI\s*Ref|UPI:)\s*[:\-]?\s*(\w{6,25})""",
            RegexOption.IGNORE_CASE,
        )
        return refRegex.find(message)?.groupValues?.get(1)
    }

    // Separator-free "26Aug26" (seen in real SBI UPI SMS: "on date 26Aug26") alongside the
    // separator-based "05-08-24"/"05/08/2024"/"05-Aug-24" forms.
    private val SEPARATED_DATE_REGEX = Regex("""\b(\d{1,2}[-/][A-Za-z0-9]{2,4}[-/]\d{2,4})\b""")
    private val COMPACT_DATE_REGEX = Regex("""\b(\d{1,2}[A-Za-z]{3}\d{2,4})\b""")

    fun findDate(message: String): LocalDateTime? {
        val raw = SEPARATED_DATE_REGEX.find(message)?.groupValues?.get(1)
            ?: COMPACT_DATE_REGEX.find(message)?.groupValues?.get(1)
            ?: return null
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

    /**
     * Parses a bare 6-digit DDMMYY date (e.g. IPPB's "280626" = 28-Jun-26) with no separators.
     * Not part of [findDate] — a bare 6-digit run is too ambiguous to search for generically
     * across bank formats (reference numbers, etc. also look like this) — callers that know
     * their message uses this exact shape extract the digits themselves first.
     */
    fun parseDdMMyy(raw: String): LocalDate? = try {
        LocalDate.parse(raw, DateTimeFormatter.ofPattern("ddMMyy", Locale.ENGLISH))
    } catch (_: Exception) {
        null
    }

    /**
     * Strips trailing boilerplate ("If not done by you call...") merchants sometimes get glued
     * to. VPAs/emails (e.g. "ombk.dqracv567083b67temfrr@mbk") legitimately contain '.', so for
     * those we only cut at ',' or ';' — cutting at '.' would truncate the VPA's domain right
     * after its first dot.
     */
    fun cleanMerchant(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val truncationPattern = if (trimmed.contains('@')) Regex("""[,;].*$""") else Regex("""[.,;].*$""")
        return trimmed
            .replace(truncationPattern, "")
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
