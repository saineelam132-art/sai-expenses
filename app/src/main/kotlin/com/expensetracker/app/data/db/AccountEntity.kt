package com.expensetracker.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

/**
 * A bank account inferred from SMS, keyed by "<bank label>-<last 4 digits>" (e.g. "SBI-1234").
 *
 * [latestBalance] is an **anchor**, not the displayed figure: it's the last balance the bank
 * itself reported, as of [balanceAsOfMillis]. The balance shown to the user is that anchor plus
 * every transaction on this account since — necessary because several banks here (SBI and APGB
 * debits, Slice) report no balance at all on a debit, which would otherwise freeze the figure
 * between the rare messages that do carry one. Re-anchoring on each reported balance means a
 * missed SMS causes drift only until the bank next states the truth, rather than forever.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val bankLabel: String,
    val lastFourDigits: String?,
    val latestBalance: BigDecimal?,
    /** When [latestBalance] was reported by the bank. Transactions after this move the displayed
     * balance; transactions at or before it are already baked into the anchor. */
    val balanceAsOfMillis: Long? = null,
    val lastUpdated: Long,
)
