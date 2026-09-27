package com.expensetracker.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.expensetracker.app.accounting.SLICE_LEDGER_ACCOUNT_ID
import com.expensetracker.app.data.db.ContactEntity
import com.expensetracker.app.data.db.LedgerAccountEntity
import com.expensetracker.app.data.db.TransactionEntity
import com.expensetracker.app.data.db.sectorLabel
import com.expensetracker.app.ui.AppViewModelFactory
import com.expensetracker.core.model.Category
import com.expensetracker.core.model.TransactionKind
import com.expensetracker.core.model.TransactionType
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TransactionListScreen(factory: AppViewModelFactory, initialTransactionId: Long? = null) {
    val viewModel: TransactionListViewModel = viewModel(factory = factory)
    val allItems = viewModel.allTransactions.collectAsLazyPagingItems()
    val reviewItems = viewModel.needsReview.collectAsLazyPagingItems()
    val totalCount by viewModel.totalCount.collectAsState()
    val needsReviewCount by viewModel.needsReviewCount.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val customCategories by viewModel.customCategories.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<TransactionEntity?>(null) }
    var editingKind by remember { mutableStateOf<TransactionEntity?>(null) }
    var editingNote by remember { mutableStateOf<TransactionEntity?>(null) }

    // Deep-linked from a notification tap — looked up directly rather than searched for in the
    // (paged) list, since the tapped transaction might not be within the currently-loaded pages.
    val deepLinked by viewModel.deepLinkedTransaction.collectAsState()
    LaunchedEffect(initialTransactionId) {
        if (initialTransactionId != null) viewModel.loadDeepLinkedTransaction(initialTransactionId)
    }
    LaunchedEffect(deepLinked) {
        deepLinked?.let {
            editing = it
            viewModel.clearDeepLinkedTransaction()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("All ($totalCount)") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Needs review ($needsReviewCount)") })
        }

        if (tab == 0) {
            PagedTransactionList(
                allItems,
                onSelectCategory = { editing = it },
                onSelectKind = { editingKind = it },
                onSelectNote = { editingNote = it },
            )
        } else {
            PagedReviewList(
                reviewItems,
                reviewCount = needsReviewCount,
                onSelfTransfer = { viewModel.correctKind(it, TransactionKind.SELF_TRANSFER, null, null) },
                onIncome = { viewModel.correctKind(it, TransactionKind.INCOME, null, null) },
                onIgnore = { viewModel.markReviewed(it) },
                onTagCategory = { editing = it },
            )
        }
    }

    editing?.let { transaction ->
        CategoryPickerDialog(
            transaction = transaction,
            customCategories = customCategories,
            onDismiss = { editing = null },
            onConfirm = { category ->
                viewModel.correctCategory(transaction, category)
                editing = null
            },
            onConfirmCustom = { name ->
                viewModel.setCustomCategory(transaction, name)
                editing = null
            },
        )
    }

    editingKind?.let { transaction ->
        KindPickerDialog(
            transaction = transaction,
            contacts = contacts,
            loans = loans,
            onDismiss = { editingKind = null },
            onConfirm = { kind, contactId, loanId ->
                viewModel.correctKind(transaction, kind, contactId, loanId)
                editingKind = null
            },
            onAddContact = { viewModel.addContact(it) },
        )
    }

    editingNote?.let { transaction ->
        NoteEditorDialog(
            transaction = transaction,
            onDismiss = { editingNote = null },
            onConfirm = { note ->
                viewModel.setNote(transaction, note)
                editingNote = null
            },
        )
    }
}

@Composable
private fun PagedTransactionList(
    items: LazyPagingItems<TransactionEntity>,
    onSelectCategory: (TransactionEntity) -> Unit,
    onSelectKind: (TransactionEntity) -> Unit,
    onSelectNote: (TransactionEntity) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // key = null (default) rather than item.id: Paging's own placeholder/load-state
        // bookkeeping already keys by position, and a malformed row (see the null-check inside
        // the loop) must never crash the whole list — a bad key derivation could.
        items(items.itemCount) { index ->
            val transaction = items[index]
            if (transaction != null) {
                TransactionRow(
                    transaction,
                    onClickCategory = { onSelectCategory(transaction) },
                    onClickKind = { onSelectKind(transaction) },
                    onClickNote = { onSelectNote(transaction) },
                )
            }
        }

        if (items.loadState.append is LoadState.Loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                }
            }
        }
        if (items.itemCount == 0 && items.loadState.refresh !is LoadState.Loading) {
            item { Text("Nothing here yet.", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/**
 * Bulk-resolve review queue (section 12): "N new transactions to review," each row resolved
 * instantly with a one-tap chip — [Self Transfer] [Income] [Ignore] [Tag category] — no
 * per-transaction screen needed. Still paginated (~20 at a time) like [PagedTransactionList] —
 * this is a UI shape, not an unbounded query.
 */
@Composable
private fun PagedReviewList(
    items: LazyPagingItems<TransactionEntity>,
    reviewCount: Int,
    onSelfTransfer: (TransactionEntity) -> Unit,
    onIncome: (TransactionEntity) -> Unit,
    onIgnore: (TransactionEntity) -> Unit,
    onTagCategory: (TransactionEntity) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                if (reviewCount > 0) "$reviewCount new transactions to review" else "All caught up!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        items(items.itemCount) { index ->
            val transaction = items[index]
            if (transaction != null) {
                ReviewRow(
                    transaction,
                    onSelfTransfer = { onSelfTransfer(transaction) },
                    onIncome = { onIncome(transaction) },
                    onIgnore = { onIgnore(transaction) },
                    onTagCategory = { onTagCategory(transaction) },
                )
            }
        }

        if (items.loadState.append is LoadState.Loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                }
            }
        }
        if (items.itemCount == 0 && items.loadState.refresh !is LoadState.Loading) {
            item { Text("Nothing here yet.", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun ReviewRow(
    transaction: TransactionEntity,
    onSelfTransfer: () -> Unit,
    onIncome: () -> Unit,
    onIgnore: () -> Unit,
    onTagCategory: () -> Unit,
) {
    val inr = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM, HH:mm") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = transaction.amount?.let { inr.format(it) } ?: "Amount unknown",
                    fontWeight = FontWeight.Bold,
                    color = if (transaction.type == TransactionType.CREDIT) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(transaction.transactionDateTime.format(dateFormatter), style = MaterialTheme.typography.bodySmall)
            }
            Text(transaction.merchant ?: "Unknown payee", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Auto-tagged: ${transaction.sectorLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(onClick = onSelfTransfer, label = { Text("Self Transfer") })
                AssistChip(onClick = onIncome, label = { Text("Income") })
                AssistChip(onClick = onIgnore, label = { Text("Ignore") })
                AssistChip(onClick = onTagCategory, label = { Text("Tag category") })
            }
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: TransactionEntity,
    onClickCategory: () -> Unit,
    onClickKind: () -> Unit,
    onClickNote: () -> Unit,
) {
    val inr = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM, HH:mm") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = transaction.amount?.let { inr.format(it) } ?: "Amount unknown",
                    fontWeight = FontWeight.Bold,
                    color = if (transaction.type == TransactionType.CREDIT) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(transaction.transactionDateTime.format(dateFormatter), style = MaterialTheme.typography.bodySmall)
            }
            Text(transaction.merchant ?: "Unknown payee", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onClickCategory) { Text(transaction.sectorLabel, style = MaterialTheme.typography.labelMedium) }
                TextButton(onClick = onClickKind) {
                    Text(
                        transaction.kind.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (transaction.kindConfident) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (transaction.needsReview) {
                    Text(
                        "Needs review",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (transaction.isLargeTransaction) {
                    Text(
                        "Large",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onClickNote) {
                    Text(
                        transaction.notes?.takeIf { it.isNotBlank() } ?: "Add note",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (transaction.notes.isNullOrBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteEditorDialog(transaction: TransactionEntity, onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var text by remember(transaction.id) { mutableStateOf(transaction.notes.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note: ${transaction.merchant ?: "this transaction"}") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * One manually-pickable transaction type. [label] is phrased the way the action is thought about
 * ("Borrowed from Friend") rather than in ledger terms ("Borrowed"); [fixedLoanId] pins the
 * choice to a specific account, which is how "Loan Taken from Slice" skips the loan picker.
 */
private data class KindOption(
    val label: String,
    val kind: TransactionKind,
    val fixedLoanId: String? = null,
)

private val kindOptions = listOf(
    KindOption("Expense", TransactionKind.EXPENSE),
    KindOption("Income", TransactionKind.INCOME),
    KindOption("Self Transaction", TransactionKind.SELF_TRANSFER),
    KindOption("Borrowed from Friend", TransactionKind.BORROWED),
    KindOption("Repaid Friend", TransactionKind.I_REPAID_FRIEND),
    KindOption("Lent to Friend", TransactionKind.LENT),
    KindOption("Friend Repaid Me", TransactionKind.FRIEND_REPAID_ME),
    KindOption("Loan Taken from Slice", TransactionKind.LOAN_DISBURSED, fixedLoanId = SLICE_LEDGER_ACCOUNT_ID),
    KindOption("Loan Taken (other)", TransactionKind.LOAN_DISBURSED),
    KindOption("Loan Repaid", TransactionKind.LOAN_REPAYMENT),
    KindOption("Investment Buy", TransactionKind.INVESTMENT_BUY),
    KindOption("Investment Sell", TransactionKind.INVESTMENT_SELL),
    KindOption("Cash Withdrawal", TransactionKind.CASH_WITHDRAWAL),
    KindOption("Cash Deposit", TransactionKind.CASH_DEPOSIT),
)

@Composable
private fun KindPickerDialog(
    transaction: TransactionEntity,
    contacts: List<ContactEntity>,
    loans: List<LedgerAccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (TransactionKind, contactId: String?, loanId: String?) -> Unit,
    onAddContact: (String) -> Unit,
) {
    // Two-step for types that need a contact or loan: pick the type, then pick which one, rather
    // than one long list mixing types with linkage targets.
    var pending by remember { mutableStateOf<KindOption?>(null) }
    var newFriend by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val option = pending
            Text(
                when {
                    option != null && option.kind.requiresContact -> "Which friend?"
                    option != null && option.kind.requiresLoan -> "Which loan?"
                    else -> "Transaction type: ${transaction.merchant ?: "this transaction"}"
                },
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val option = pending
                when {
                    option == null -> {
                        kindOptions.forEach { candidate ->
                            TextButton(
                                onClick = {
                                    val needsPick = (candidate.kind.requiresContact) ||
                                        (candidate.kind.requiresLoan && candidate.fixedLoanId == null)
                                    if (needsPick) {
                                        pending = candidate
                                    } else {
                                        onConfirm(candidate.kind, null, candidate.fixedLoanId)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(candidate.label, modifier = Modifier.fillMaxWidth()) }
                        }
                    }
                    option.kind.requiresContact -> {
                        // Always asked, never inferred from the payee name — getting the wrong
                        // friend silently moves money against the wrong person's balance.
                        contacts.forEach { contact ->
                            TextButton(
                                onClick = { onConfirm(option.kind, contact.id, null) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(contact.name, modifier = Modifier.fillMaxWidth()) }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        OutlinedTextField(
                            value = newFriend,
                            onValueChange = { newFriend = it },
                            label = { Text("New friend's name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            onClick = { if (newFriend.isNotBlank()) onAddContact(newFriend.trim()) },
                            enabled = newFriend.isNotBlank(),
                        ) { Text("Add friend") }
                    }
                    else -> {
                        if (loans.isEmpty()) {
                            Text("No loans set up yet — add one in Setup first.", style = MaterialTheme.typography.bodySmall)
                        }
                        loans.forEach { loan ->
                            TextButton(
                                onClick = { onConfirm(option.kind, null, loan.id) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(loan.name, modifier = Modifier.fillMaxWidth()) }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The categorize popup. Below the built-in sectors it lists any the user has invented, then a
 * free-text box for a new one — typing a sector here saves this transaction under it *and*
 * remembers it, so it appears in this list for every future transaction.
 */
@Composable
private fun CategoryPickerDialog(
    transaction: TransactionEntity,
    customCategories: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (Category) -> Unit,
    onConfirmCustom: (String) -> Unit,
) {
    var typed by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categorize: ${transaction.merchant ?: "this transaction"}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Category.entries.filter { it != Category.UNCATEGORIZED }.forEach { category ->
                    TextButton(onClick = { onConfirm(category) }, modifier = Modifier.fillMaxWidth()) {
                        Text(category.displayName, modifier = Modifier.fillMaxWidth())
                    }
                }

                if (customCategories.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(
                        "Your categories",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    customCategories.forEach { name ->
                        TextButton(onClick = { onConfirmCustom(name) }, modifier = Modifier.fillMaxWidth()) {
                            Text(name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Other — type your own") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { if (typed.isNotBlank()) onConfirmCustom(typed.trim()) },
                    enabled = typed.isNotBlank(),
                ) { Text("Use this category") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
