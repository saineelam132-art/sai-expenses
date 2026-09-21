package com.expensetracker.app.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.data.db.ContactEntity
import com.expensetracker.app.data.db.LedgerAccountCategory
import com.expensetracker.app.diagnostics.CrashLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** [netBalance] is receivable minus payable: positive means [contact] owes the user, negative
 * means the user owes [contact]. */
data class FriendBalance(val contact: ContactEntity, val netBalance: BigDecimal)

data class FriendsUiState(val balances: List<FriendBalance> = emptyList())

class FriendsViewModel(private val app: ExpenseTrackerApp) : ViewModel() {

    // Supervised retry rather than combine(...).catch{}.stateIn(...) — see DashboardViewModel's
    // docs for why: .catch{} does not guard against an exception thrown while combine() itself
    // is (re)starting collection, which can otherwise escape as a fatal, uncaught exception on
    // the main thread via collectAsState().
    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { collectState() }
    }

    private suspend fun collectState() {
        while (true) {
            try {
                buildState().collect { _uiState.value = it }
                return
            } catch (c: CancellationException) {
                throw c
            } catch (e: Throwable) {
                CrashLog.record(app, "FriendsViewModel.buildState", e)
                _uiState.value = FriendsUiState()
                delay(1000)
            }
        }
    }

    private fun buildState() = combine(
        app.database.contactDao().observeAll(),
        app.database.ledgerAccountDao().observeAll(),
    ) { contacts, ledgerAccounts ->
        val balances = contacts.map { contact ->
            val receivable = ledgerAccounts
                .firstOrNull { it.category == LedgerAccountCategory.RECEIVABLE && it.contactId == contact.id }
                ?.balance ?: BigDecimal.ZERO
            val payable = ledgerAccounts
                .firstOrNull { it.category == LedgerAccountCategory.PAYABLE && it.contactId == contact.id }
                ?.balance ?: BigDecimal.ZERO
            FriendBalance(contact, receivable - payable)
        }
        FriendsUiState(balances = balances)
    }
}
