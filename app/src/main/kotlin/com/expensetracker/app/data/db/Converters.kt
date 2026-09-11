package com.expensetracker.app.data.db

import androidx.room.TypeConverter
import com.expensetracker.core.model.Category
import com.expensetracker.core.model.ParseConfidence
import com.expensetracker.core.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Every string->enum converter here fails soft (falls back to a safe default) instead of
 * throwing. Room runs these during every row's deserialization as a Flow/PagingSource query
 * emits — one row with an unexpected/corrupt stored value throwing here would fail the *entire*
 * list read, not just that row, crashing whatever screen was observing it. A future app update
 * adding/renaming an enum constant is exactly the kind of thing that could otherwise produce
 * such a value in an existing database.
 */
class Converters {
    @TypeConverter
    fun bigDecimalToString(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun stringToBigDecimal(value: String?): BigDecimal? =
        value?.let {
            try {
                BigDecimal(it)
            } catch (_: NumberFormatException) {
                null
            }
        }

    @TypeConverter
    fun dateTimeToEpochMillis(value: LocalDateTime?): Long? =
        value?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

    @TypeConverter
    fun epochMillisToDateTime(value: Long?): LocalDateTime? =
        value?.let {
            try {
                java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            } catch (_: Exception) {
                null
            }
        }

    @TypeConverter
    fun transactionTypeToString(value: TransactionType): String = value.name

    @TypeConverter
    fun stringToTransactionType(value: String): TransactionType =
        try {
            TransactionType.valueOf(value)
        } catch (_: IllegalArgumentException) {
            TransactionType.UNKNOWN
        }

    @TypeConverter
    fun categoryToString(value: Category): String = value.name

    @TypeConverter
    fun stringToCategory(value: String): Category =
        try {
            Category.valueOf(value)
        } catch (_: IllegalArgumentException) {
            Category.UNCATEGORIZED
        }

    @TypeConverter
    fun confidenceToString(value: ParseConfidence): String = value.name

    @TypeConverter
    fun stringToConfidence(value: String): ParseConfidence =
        try {
            ParseConfidence.valueOf(value)
        } catch (_: IllegalArgumentException) {
            ParseConfidence.UNPARSED
        }
}
