package com.expensetracker.app.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.data.db.ContactEntity
import com.expensetracker.app.data.db.LedgerAccountCategory
import com.expensetracker.app.data.db.LedgerAccountEntity
import com.expensetracker.app.data.db.LedgerSide
import com.expensetracker.core.categorize.CategoryEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

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

    fun addLoan(
        name: String,
        principal: BigDecimal,
        interestRatePercent: Double,
        disbursedDate: LocalDate,
        outstandingBalance: BigDecimal,
        emiAmount: BigDecimal?,
    ) {
        viewModelScope.launch {
            app.database.ledgerAccountDao().upsert(
                LedgerAccountEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    side = LedgerSide.LIABILITY,
                    category = LedgerAccountCategory.LOAN,
                    balance = outstandingBalance,
                    principal = principal,
                    interestRatePercent = interestRatePercent,
                    disbursedDate = disbursedDate,
                    lastAccrualDate = disbursedDate,
                    emiAmount = emiAmount,
                ),
            )
        }
    }

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
        viewModelScope.launch { app.database.ledgerAccountDao().delete(id) }
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
