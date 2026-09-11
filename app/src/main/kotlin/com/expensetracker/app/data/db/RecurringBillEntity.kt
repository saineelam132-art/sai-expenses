package com.expensetracker.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate

enum class RecurringBillStatus { ACTIVE, PAUSED, REVOKED }

/**
 * A recurring charge registered from a UPI AutoPay mandate lifecycle message (created/paused/
 * revoked) — see [com.expensetracker.core.parser.NonTransactionClassifier]. This is
 * *informational* (a mandate existing doesn't mean a charge happened yet); actual charges still
 * arrive as ordinary debit transactions and are matched against these separately by amount/
 * merchant, not created by this entity itself.
 */
@Entity(tableName = "recurring_bills")
data class RecurringBillEntity(
    @PrimaryKey val id: String,
    val merchant: String,
    val expectedAmount: BigDecimal?,
    val sourceLabel: String,
    val status: RecurringBillStatus,
    val validFrom: LocalDate?,
    val validTo: LocalDate?,
    val referenceId: String?,
    val lastUpdated: Long = System.currentTimeMillis(),
)
