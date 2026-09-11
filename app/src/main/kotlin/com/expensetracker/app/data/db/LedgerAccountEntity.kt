package com.expensetracker.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate

/** Which side of the balance sheet an account sits on. */
enum class LedgerSide { ASSET, LIABILITY }

/**
 * What kind of asset/liability bucket this is. Bank accounts are deliberately NOT included here
 * — [AccountEntity] already tracks those, with balances taken straight from the bank's own SMS
 * (ground truth), not computed. Everything in *this* table is a bucket this app computes/tracks
 * itself because no bank SMS reports it directly.
 */
enum class LedgerAccountCategory { CASH, INVESTMENTS, LOAN, RECEIVABLE, PAYABLE, MANUAL_ASSET }

/**
 * One asset or liability bucket outside the SMS-tracked bank accounts: cash on hand, the
 * aggregate investments bucket, one row per loan, one Receivable/Payable per Friend contact
 * (see [ContactEntity]), or a manually-added asset (gold, FD, vehicle, ...).
 *
 * [balance] is this app's own running total, updated by [com.expensetracker.app.accounting.LedgerPostingEngine]
 * as transactions are posted — unlike bank balances, there's no external source of truth to
 * reconcile against, so this must be kept correct by construction (every kind of posting that
 * touches a bucket is centralized in the posting engine, not scattered across the UI).
 *
 * Loan-specific fields ([principal], [interestRatePercent], [disbursedDate], [lastAccrualDate],
 * [emiAmount]) are null for every category except LOAN.
 */
@Entity(tableName = "ledger_accounts")
data class LedgerAccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val side: LedgerSide,
    val category: LedgerAccountCategory,
    val balance: BigDecimal,
    /** For RECEIVABLE/PAYABLE only — which [ContactEntity] this bucket belongs to. */
    val contactId: String? = null,
    val principal: BigDecimal? = null,
    val interestRatePercent: Double? = null,
    val disbursedDate: LocalDate? = null,
    /** Last date interest was accrued/settled against this loan — reducing-balance interest for
     * the next repayment is computed from this date, not [disbursedDate], once it's set. */
    val lastAccrualDate: LocalDate? = null,
    val emiAmount: BigDecimal? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
