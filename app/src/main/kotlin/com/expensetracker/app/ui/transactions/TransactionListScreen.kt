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
import com.expensetracker.app.data.db.TransactionEntity
import com.expensetracker.app.ui.AppViewModelFactory
import com.expensetracker.core.model.Category
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
    var tab by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<TransactionEntity?>(null) }

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
            PagedTransactionList(allItems, onSelect = { editing = it })
        } else {
            PagedTransactionList(reviewItems, onSelect = { editing = it })
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
}

@Composable
private fun PagedTransactionList(items: LazyPagingItems<TransactionEntity>, onSelect: (TransactionEntity) -> Unit) {
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
                TransactionRow(transaction, onClick = { onSelect(transaction) })
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
private fun TransactionRow(transaction: TransactionEntity, onClick: () -> Unit) {
    val inr = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM, HH:mm") }
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
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
                Text(transaction.category.displayName, style = MaterialTheme.typography.labelMedium)
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
            }
        }
    }
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
