package com.expensetracker.app.ui.bills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.app.data.db.RecurringBillStatus
import com.expensetracker.app.ui.AppViewModelFactory
import java.text.NumberFormat
import java.util.Locale

/** Section 10: recurring charges registered from AutoPay mandate lifecycle messages — a
 * dedicated view separate from one-off spending. */
@Composable
fun BillsScreen(factory: AppViewModelFactory) {
    val viewModel: BillsViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsState()
    val inr = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.bills.isEmpty()) {
            item {
                Text(
                    "No recurring bills registered yet — these appear automatically from AutoPay " +
                        "mandate SMS (created/paused/revoked).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        items(state.bills, key = { it.bill.id }) { row -> BillRowCard(row, inr) }
    }
}

@Composable
private fun BillRowCard(row: BillRow, inr: NumberFormat) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.bill.merchant, fontWeight = FontWeight.Bold)
                Text(
                    row.bill.expectedAmount?.let { inr.format(it) } ?: "Amount unknown",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(statusText(row.bill.status), style = MaterialTheme.typography.bodySmall)
            Text(row.lastChargedText, style = MaterialTheme.typography.bodySmall)
            if (row.possiblyOverdue) {
                Text(
                    "Might be worth checking — no charge seen in a while",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun statusText(status: RecurringBillStatus) = when (status) {
    RecurringBillStatus.ACTIVE -> "Active"
    RecurringBillStatus.PAUSED -> "Paused"
    RecurringBillStatus.REVOKED -> "Cancelled"
}
