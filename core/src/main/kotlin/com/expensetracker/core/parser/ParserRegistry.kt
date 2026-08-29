package com.expensetracker.core.parser

import com.expensetracker.core.parser.banks.ApgbParser
import com.expensetracker.core.parser.banks.AxisParser
import com.expensetracker.core.parser.banks.GenericFallbackParser
import com.expensetracker.core.parser.banks.HdfcParser
import com.expensetracker.core.parser.banks.IciciParser
import com.expensetracker.core.parser.banks.KotakParser
import com.expensetracker.core.parser.banks.SbiParser
import com.expensetracker.core.parser.banks.SliceParser
import com.expensetracker.core.parser.banks.UpiAppNotificationParser
import com.expensetracker.core.model.ParsedTransaction

/**
 * Tries every registered parser, most specific first, and returns the first match.
 * The [GenericFallbackParser] is always tried last and never rejects a message outright unless
 * it truly doesn't look transactional — this is what implements "log as Uncategorized/Needs
 * review instead of silently dropping it" for format drift.
 *
 * To add coverage for a new bank: write a new TransactionParser implementation (see
 * SbiParser/HdfcParser for the pattern) and add it to [defaultParsers] before the fallback.
 */
class ParserRegistry(private val parsers: List<TransactionParser> = defaultParsers()) {

    fun parse(message: String, sender: String?): ParsedTransaction? {
        for (parser in parsers) {
            val result = parser.tryParse(message, sender)
            if (result != null) return result
        }
        return null
    }

    companion object {
        fun defaultParsers(): List<TransactionParser> = listOf(
            SbiParser(),
            HdfcParser(),
            IciciParser(),
            AxisParser(),
            KotakParser(),
            ApgbParser(),
            SliceParser(),
            UpiAppNotificationParser(),
            GenericFallbackParser(),
        )
    }
}
