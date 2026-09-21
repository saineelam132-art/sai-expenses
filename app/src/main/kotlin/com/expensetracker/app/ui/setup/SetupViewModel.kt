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
            app.database.ledgerAccountDao().upsert(
                LedgerAccountEntity(
                    id = loanId,
                    name = details.name,
                    side = LedgerSide.LIABILITY,
                    category = LedgerAccountCategory.LOAN,
                    balance = details.outstandingBalance,
                    principal = details.principal,
                    interestRatePercent = details.interestRatePercent,
                    disbursedDate = details.disbursedDate,
                    emiAmount = details.emiAmount,
                    loanAccountNumber = details.loanAccountNumber,
                    sanctionedAmount = details.sanctionedAmount,
                    tenureMonths = details.tenureMonths,
                    aprPercent = details.aprPercent,
                    processingFee = details.processingFee,
                    insuranceCharge = details.insuranceCharge,
                    penalChargeTerms = details.penalChargeTerms,
                    foreclosureChargeTerms = details.foreclosureChargeTerms,
                ),
            )
            logOneTimeCharges(details)
        }
    }

    private suspend fun logOneTimeCharges(details: LoanDetails) {
        val charges = listOfNotNull(
            details.processingFee?.takeIf { it > BigDecimal.ZERO }?.let { "Processing fee" to it },
            details.insuranceCharge?.takeIf { it > BigDecimal.ZERO }?.let { "Insurance charge" to it },
        )
        val disbursedAt = (details.disbursedDate ?: LocalDate.now()).atStartOfDay()
        charges.forEach { (label, amount) ->
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
                    referenceId = null,
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

    /** One installment row of a loan's agreement schedule (section 6). */
    fun addScheduleRow(row: LoanScheduleEntity) {
        viewModelScope.launch { app.database.loanScheduleDao().upsert(row) }
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

    fun deleteLedgerAccount(id: String) {
        viewModelScope.launch {
            // Schedule rows are meaningless without their loan — drop them together so a
            // re-added loan can't inherit a stale schedule under a recycled id.
            app.database.loanScheduleDao().deleteForLoan(id)
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

    fun deleteContact(id: String) {
        viewModelScope.launch { app.database.contactDao().delete(id) }
    }
}
