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
 * Loan-specific fields are null for every category except LOAN. They mirror what a real loan
 * agreement states, so nothing about a loan has to be derived: [interestRatePercent] and
 * [aprPercent] are deliberately separate (APR includes fees, so they differ), and the
 * installment-by-installment repayment schedule lives in [LoanScheduleEntity] rather than being
 * computed from these figures.
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
    val emiAmount: BigDecimal? = null,
    /** Lender's own loan/account number, as printed on the agreement. */
    val loanAccountNumber: String? = null,
    val sanctionedAmount: BigDecimal? = null,
    val tenureMonths: Int? = null,
    /** Annual Percentage Rate — includes fees, so it differs from [interestRatePercent]. */
    val aprPercent: Double? = null,
    val processingFee: BigDecimal? = null,
    val insuranceCharge: BigDecimal? = null,
    /** Free text, quoted from the agreement (e.g. "Rs.500 or 30% of EMI, whichever is lower"). */
    val penalChargeTerms: String? = null,
    val foreclosureChargeTerms: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
