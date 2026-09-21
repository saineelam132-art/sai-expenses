package com.expensetracker.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import com.expensetracker.app.data.db.ContactEntity
import com.expensetracker.app.data.db.LedgerAccountEntity
import com.expensetracker.app.data.db.TransactionEntity
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
            PagedTransactionList(
                reviewItems,
                onSelectCategory = { editing = it },
                onSelectKind = { editingKind = it },
                onSelectNote = { editingNote = it },
            )
        }
    }

    editing?.let { transaction ->
        CategoryPickerDialog(
            transaction = transaction,
            onDismiss = { editing = null },
            onConfirm = { category ->
                viewModel.correctCategory(transaction, category)
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
                TextButton(onClick = onClickCategory) { Text(transaction.category.displayName, style = MaterialTheme.typography.labelMedium) }
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

@Composable
private fun KindPickerDialog(
    transaction: TransactionEntity,
    contacts: List<ContactEntity>,
    loans: List<LedgerAccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (TransactionKind, contactId: String?, loanId: String?) -> Unit,
) {
    // Two-step for kinds that need a contact/loan: pick the kind, then pick which one — rather
    // than one giant list mixing kinds and linkage targets.
    var pendingKind by remember { mutableStateOf<TransactionKind?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val kind = pendingKind
            Text(
                when {
                    kind != null && kind.requiresContact -> "Which friend?"
                    kind != null && kind.requiresLoan -> "Which loan?"
                    else -> "Transaction type: ${transaction.merchant ?: "this transaction"}"
                },
            )
        },
        text = {
            Column {
                val kind = pendingKind
                when {
                    kind == null -> {
                        TransactionKind.entries.forEach { candidate ->
                            TextButton(
                                onClick = {
                                    if (candidate.requiresContact || candidate.requiresLoan) {
                                        pendingKind = candidate
                                    } else {
                                        onConfirm(candidate, null, null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(candidate.displayName, modifier = Modifier.fillMaxWidth()) }
                        }
                    }
                    kind.requiresContact -> {
                        if (contacts.isEmpty()) {
                            Text("No friends added yet — add one in Settings first.", style = MaterialTheme.typography.bodySmall)
                        }
                        contacts.forEach { contact ->
                            TextButton(onClick = { onConfirm(kind, contact.id, null) }, modifier = Modifier.fillMaxWidth()) {
                                Text(contact.name, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    kind.requiresLoan -> {
                        if (loans.isEmpty()) {
                            Text("No loans added yet — add one in Settings first.", style = MaterialTheme.typography.bodySmall)
                        }
                        loans.forEach { loan ->
                            TextButton(onClick = { onConfirm(kind, null, loan.id) }, modifier = Modifier.fillMaxWidth()) {
                                Text(loan.name, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CategoryPickerDialog(transaction: TransactionEntity, onDismiss: () -> Unit, onConfirm: (Category) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categorize: ${transaction.merchant ?: "this transaction"}") },
        text = {
            Column {
                Category.entries.filter { it != Category.UNCATEGORIZED }.forEach { category ->
                    TextButton(onClick = { onConfirm(category) }, modifier = Modifier.fillMaxWidth()) {
                        Text(category.displayName, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
