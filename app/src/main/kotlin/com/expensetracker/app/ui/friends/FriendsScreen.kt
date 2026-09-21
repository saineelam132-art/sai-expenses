package com.expensetracker.app.ui.friends

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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.unit.dp
import com.expensetracker.app.ui.AppViewModelFactory
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Section 8: who owes the user, who the user owes — excluded from normal cash-flow totals. */
@Composable
fun FriendsScreen(factory: AppViewModelFactory) {
    val viewModel: FriendsViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsState()
    val inr = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            val net = state.balances.sumOf { it.netBalance.toDouble() }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Net across friends", style = MaterialTheme.typography.labelLarge)
                    Text(inr.format(net), style = MaterialTheme.typography.headlineMedium)
                }
            }
        }

        if (state.balances.isEmpty()) {
            item { Text("No friends added yet — add one in Settings.", style = MaterialTheme.typography.bodySmall) }
        }

        items(state.balances, key = { it.contact.id }) { balance ->
            FriendRow(balance, inr)
        }
    }
}

@Composable
private fun FriendRow(balance: FriendBalance, inr: NumberFormat) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(balance.contact.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            val label = when {
                balance.netBalance > BigDecimal.ZERO -> "Owes you ${inr.format(balance.netBalance)}"
                balance.netBalance < BigDecimal.ZERO -> "You owe ${inr.format(balance.netBalance.abs())}"
                else -> "Settled up"
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (balance.netBalance >= BigDecimal.ZERO) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
}
