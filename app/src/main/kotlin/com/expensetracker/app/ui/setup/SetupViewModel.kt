package com.expensetracker.app.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.data.db.ContactEntity
import com.expensetracker.app.data.db.LedgerAccountCategory
import com.expensetracker.app.data.db.LedgerAccountEntity
import com.expensetracker.app.data.db.LedgerSide
import com.expensetracker.app.data.db.LoanScheduleEntity
import com.expensetracker.app.data.db.TransactionEntity
import com.expensetracker.core.categorize.CategoryEngine
import com.expensetracker.core.model.Category
import com.expensetracker.core.model.ParseConfidence
import com.expensetracker.core.model.TransactionKind
import com.expensetracker.core.model.TransactionType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * A loan as its agreement states it. Only [name], [outstandingBalance] and [disbursedDate] are
 * needed for a loan with no paperwork to hand; everything else is filled in when the document is
 * available. The installment schedule itself is entered separately as [LoanScheduleEntity] rows.
 */
data class LoanDetails(
    val name: String,
    val outstandingBalance: BigDecimal,
    val disbursedDate: LocalDate?,
    val principal: BigDecimal? = null,
    val interestRatePercent: Double? = null,
    val emiAmount: BigDecimal? = null,
    val loanAccountNumber: String? = null,
    val sanctionedAmount: BigDecimal? = null,
    val tenureMonths: Int? = null,
    val aprPercent: Double? = null,
    val processingFee: BigDecimal? = null,
    val insuranceCharge: BigDecimal? = null,
    val penalChargeTerms: String? = null,
    val foreclosureChargeTerms: String? = null,
)

class SetupViewModel(private val app: ExpenseTrackerApp) : ViewModel() {

    private val ledgerAccounts = app.database.ledgerAccountDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cashBalance: StateFlow<BigDecimal> = ledgerAccounts
        .map { it.firstOrNull { a -> a.category == LedgerAccountCategory.CASH }?.balance ?: BigDecimal.ZERO }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BigDecimal.ZERO)

    val loans: StateFlow<List<LedgerAccountEntity>> = ledgerAccounts
        .map { it.filter { a -> a.category == LedgerAccountCategory.LOAN } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val manualAssets: StateFlow<List<LedgerAccountEntity>> = ledgerAccounts
        .map { it.filter { a -> a.category == LedgerAccountCategory.MANUAL_ASSET } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contacts: StateFlow<List<ContactEntity>> = app.database.contactDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Used to tell a Slice loan disbursement (sent to myself) apart from a Slice-funded payment
     * to a merchant — see TypeInferenceEngine. */
    val ownNameVariants: StateFlow<Set<String>> = app.settingsRepository.ownNameVariants
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun setOwnNameVariants(names: Set<String>) {
        viewModelScope.launch { app.settingsRepository.setOwnNameVariants(names) }
    }

    fun setCashBalance(amount: BigDecimal) {
        viewModelScope.launch {
            val existing = app.database.ledgerAccountDao().getCashBucket()
            app.database.ledgerAccountDao().upsert(
                (existing ?: LedgerAccountEntity(
                    id = "cash-on-hand",
                    name = "Cash on hand",
                    side = LedgerSide.ASSET,
                    category = LedgerAccountCategory.CASH,
                    balance = BigDecimal.ZERO,
                )).copy(balance = amount),
            )
        }
    }

    /**
     * Adds a loan exactly as its agreement states. Everything here is entered, never derived —
     * see [LoanScheduleEntity] for why the repayment schedule in particular can't be computed.
     * A loan with no known schedule is still valid (simple principal + rate + tenure); its
     * schedule rows can be added later via [addScheduleRow].
     *
     * One-time [processingFee]/[insuranceCharge] are logged as a separate one-time Expense at
     * disbursal — they're a real cost, but not part of the EMI schedule.
     */
    fun addLoan(details: LoanDetails) {
        viewModelScope.launch {
            val loanId = UUID.randomUUID().toString()
            app.database.ledgerAccountDao().upsert(details.toEntity(loanId))
            logOneTimeCharges(loanId, details)
        }
    }

    /**
     * Corrects an existing loan in place — a mistyped figure shouldn't cost the whole entry (and
     * its schedule, which is keyed by loan id and so survives untouched). The one-time fee
     * transactions are re-logged from the corrected figures, since a fee entered wrong would
     * otherwise leave a stale expense in the ledger forever.
     */
    fun updateLoan(loanId: String, details: LoanDetails) {
        viewModelScope.launch {
            val existing = app.database.ledgerAccountDao().getById(loanId)
            app.database.ledgerAccountDao().upsert(
                details.toEntity(loanId, createdAt = existing?.createdAt ?: System.currentTimeMillis()),
            )
            app.database.transactionDao().deleteLoanSetupFees(loanId)
            logOneTimeCharges(loanId, details)
        }
    }

    private fun LoanDetails.toEntity(loanId: String, createdAt: Long = System.currentTimeMillis()) =
        LedgerAccountEntity(
            id = loanId,
            name = name,
            side = LedgerSide.LIABILITY,
            category = LedgerAccountCategory.LOAN,
            balance = outstandingBalance,
            principal = principal,
            interestRatePercent = interestRatePercent,
            disbursedDate = disbursedDate,
            emiAmount = emiAmount,
            loanAccountNumber = loanAccountNumber,
            sanctionedAmount = sanctionedAmount,
            tenureMonths = tenureMonths,
            aprPercent = aprPercent,
            processingFee = processingFee,
            insuranceCharge = insuranceCharge,
            penalChargeTerms = penalChargeTerms,
            foreclosureChargeTerms = foreclosureChargeTerms,
            createdAt = createdAt,
        )

    private suspend fun logOneTimeCharges(loanId: String, details: LoanDetails) {
        val charges = listOfNotNull(
            details.processingFee?.takeIf { it > BigDecimal.ZERO }?.let { Triple("processing", "Processing fee", it) },
            details.insuranceCharge?.takeIf { it > BigDecimal.ZERO }?.let { Triple("insurance", "Insurance charge", it) },
        )
        val disbursedAt = (details.disbursedDate ?: LocalDate.now()).atStartOfDay()
        charges.forEach { (key, label, amount) ->
            app.database.transactionDao().insert(
                TransactionEntity(
                    amount = amount,
                    type = TransactionType.DEBIT,
                    merchant = "${details.name} — $label",
                    category = Category.BILLS_UTILITIES,
                    categoryConfident = true,
                    transactionDateTime = disbursedAt,
                    availableBalance = null,
                    accountId = null,
                    // Stable marker so these rows can be found again and replaced when the loan
                    // is edited, or removed with it.
                    referenceId = "$LOAN_FEE_PREFIX$loanId:$key",
                    sourceLabel = "Manual",
                    rawMessage = "Loan setup: $label for ${details.name}",
                    parseConfidence = ParseConfidence.HIGH,
                    needsReview = false,
                    isLargeTransaction = false,
                    userReviewed = true,
                    kind = TransactionKind.EXPENSE,
                    kindConfident = true,
                ),
            )
        }
    }

    /** One installment row of a loan's agreement schedule (section 6). Also used to correct a
     * row: the primary key is (loanId, installmentNumber), so re-submitting that pair replaces it. */
    fun addScheduleRow(row: LoanScheduleEntity) {
        viewModelScope.launch { app.database.loanScheduleDao().upsert(row) }
    }

    fun deleteScheduleRow(loanId: String, installmentNumber: Int) {
        viewModelScope.launch { app.database.loanScheduleDao().delete(loanId, installmentNumber) }
    }

    fun scheduleFor(loanId: String): StateFlow<List<LoanScheduleEntity>> =
        app.database.loanScheduleDao().observeForLoan(loanId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addManualAsset(name: String, value: BigDecimal) {
        viewModelScope.launch {
            app.database.ledgerAccountDao().upsert(
                LedgerAccountEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    side = LedgerSide.ASSET,
                    category = LedgerAccountCategory.MANUAL_ASSET,
                    balance = value,
                ),
            )
        }
    }

    /**
     * Manually moves a ledger account's balance — a fresh draw on a credit line, a repayment that
     * never arrived as an SMS, or a correction. Same manual-entry precedent as cash on hand:
     * the user is asserting the figure, so it's applied directly rather than inferred.
     */
    fun adjustLedgerBalance(id: String, delta: BigDecimal) {
        viewModelScope.launch {
            val existing = app.database.ledgerAccountDao().getById(id) ?: return@launch
            val updated = (existing.balance + delta).coerceAtLeast(BigDecimal.ZERO)
            app.database.ledgerAccountDao().upsert(existing.copy(balance = updated))
        }
    }

    fun updateManualAsset(id: String, name: String, value: BigDecimal) {
        viewModelScope.launch {
            val existing = app.database.ledgerAccountDao().getById(id) ?: return@launch
            app.database.ledgerAccountDao().upsert(existing.copy(name = name, balance = value))
        }
    }

    fun deleteLedgerAccount(id: String) {
        viewModelScope.launch {
            // A loan's schedule rows and its one-time setup fees are meaningless without it —
            // drop them together, so a re-added loan can't inherit either under a recycled id.
            app.database.loanScheduleDao().deleteForLoan(id)
            app.database.transactionDao().deleteLoanSetupFees(id)
            app.database.ledgerAccountDao().delete(id)
        }
    }

    /** [identifiers] are raw merchant/VPA text as the user recalls it (e.g. "ravi@okhdfcbank",
     * "Ravi Kumar") — normalized the same way merchant names are, so auto-detection matches. */
    fun addContact(name: String, identifiers: List<String>) {
        viewModelScope.launch {
            val normalized = identifiers.mapNotNull { CategoryEngine.normalize(it) }.filter { it.isNotBlank() }
            app.database.contactDao().upsert(
                ContactEntity(id = UUID.randomUUID().toString(), name = name, knownIdentifiers = normalized.joinToString(",")),
            )
        }
    }

    fun updateContact(id: String, name: String, identifiers: List<String>) {
        viewModelScope.launch {
            val normalized = identifiers.mapNotNull { CategoryEngine.normalize(it) }.filter { it.isNotBlank() }
            app.database.contactDao().upsert(
                ContactEntity(id = id, name = name, knownIdentifiers = normalized.joinToString(",")),
            )
        }
    }

    fun deleteContact(id: String) {
        viewModelScope.launch { app.database.contactDao().delete(id) }
    }

    companion object {
        private const val LOAN_FEE_PREFIX = "loan-fee:"
    }
}

/** Pre-fills the edit form from what's already stored. */
fun LedgerAccountEntity.toLoanDetails() = LoanDetails(
    name = name,
    outstandingBalance = balance,
    disbursedDate = disbursedDate,
    principal = principal,
    interestRatePercent = interestRatePercent,
    emiAmount = emiAmount,
    loanAccountNumber = loanAccountNumber,
    sanctionedAmount = sanctionedAmount,
    tenureMonths = tenureMonths,
    aprPercent = aprPercent,
    processingFee = processingFee,
    insuranceCharge = insuranceCharge,
    penalChargeTerms = penalChargeTerms,
    foreclosureChargeTerms = foreclosureChargeTerms,
)
