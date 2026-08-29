package com.expensetracker.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

/**
 * A bank account inferred from SMS, keyed by "<bank label>-<last 4 digits>" (e.g. "SBI-1234").
 * Balance is only ever set from a bank SMS's "Avl Bal" field — we never compute it ourselves,
 * since drift between our running total and the bank's actual figure (fees, missed messages)
 * would make the number untrustworthy.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val bankLabel: String,
    val lastFourDigits: String?,
    val latestBalance: BigDecimal?,
    val lastUpdated: Long,
)
