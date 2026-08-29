package com.expensetracker.app.data.db

import androidx.room.TypeConverter
import com.expensetracker.core.model.Category
import com.expensetracker.core.model.ParseConfidence
import com.expensetracker.core.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime

class Converters {
    @TypeConverter
    fun bigDecimalToString(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun stringToBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }

    @TypeConverter
    fun dateTimeToEpochMillis(value: LocalDateTime?): Long? =
        value?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

    @TypeConverter
    fun epochMillisToDateTime(value: Long?): LocalDateTime? =
        value?.let {
            java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        }

    @TypeConverter
    fun transactionTypeToString(value: TransactionType): String = value.name

    @TypeConverter
    fun stringToTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

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
    fun stringToConfidence(value: String): ParseConfidence = ParseConfidence.valueOf(value)
}
