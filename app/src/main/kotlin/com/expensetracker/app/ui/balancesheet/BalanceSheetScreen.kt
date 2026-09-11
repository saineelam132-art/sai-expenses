package com.expensetracker.app.ui.balancesheet

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
import com.expensetracker.app.ui.AppViewModelFactory
import java.text.NumberFormat
import java.util.Locale

@Composable
fun BalanceSheetScreen(factory: AppViewModelFactory) {
    val viewModel: BalanceSheetViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsState()
    val inr = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Net worth", style = MaterialTheme.typography.labelLarge)
                    Text(inr.format(state.netWorth), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Computed live from every account below — never entered manually.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item { SectionHeader("Assets", state.assets.sumOf { it.balance }, inr) }
        items(state.assets) { row -> BalanceRow(row.name, row.balance, inr) }
        if (state.assets.isEmpty()) {
            item { Text("No assets yet.", style = MaterialTheme.typography.bodySmall) }
        }

        item { SectionHeader("Liabilities", state.liabilities.sumOf { it.balance }, inr) }
        items(state.liabilities) { row -> BalanceRow(row.name, row.balance, inr) }
        if (state.liabilities.isEmpty()) {
            item { Text("No liabilities — nice.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, total: Double, inr: NumberFormat) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(inr.format(total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BalanceRow(name: String, balance: Double, inr: NumberFormat) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(inr.format(balance), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}
