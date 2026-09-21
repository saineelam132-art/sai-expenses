package com.expensetracker.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.app.ui.AppViewModelFactory
import com.expensetracker.app.ui.charts.ChartSlice
import com.expensetracker.app.ui.charts.SectorBarChart
import com.expensetracker.app.ui.charts.SectorDonutChart
import com.expensetracker.app.ui.charts.foldTail
import com.expensetracker.app.ui.theme.SectorColors
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Everything the user wants at a glance lives here rather than behind a second tab: balances,
 * this month's cash flow (with cash-on-hand editable in place), the sector breakdown, and the
 * donut. All of it is driven by one live state, so a captured or re-confirmed transaction
 * updates every card at once.
 */
@Composable
fun DashboardScreen(factory: AppViewModelFactory) {
    val viewModel: DashboardViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsState()
    val inr = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    // Top sectors keep their own color; the tail folds into one "Other" slice so the donut never
    // shows more colors at once than the palette is validated for.
    val slices = remember(state.spendingBySector) {
        state.spendingBySector
            .map { ChartSlice(it.category.displayName, it.amount, SectorColors.forCategory(it.category)) }
            .foldTail(keep = 7, otherColor = SectorColors.Untagged)
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { BalanceCard(state, inr) }
        item { CashFlowCard(state, inr, onSaveCash = viewModel::setCashOnHand) }
        item { SpendingSummaryCard(state, inr) }
        item { SpendBySectorCard(slices, state.monthTotalSpend, inr) }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun BalanceCard(state: DashboardUiState, inr: NumberFormat) {
    SectionCard {
        Text("Total balance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            inr.format(state.totalBalance),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (state.accounts.isEmpty()) {
            Text(
                "No balance yet — it appears after the first bank SMS with a balance field.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.accounts.forEach { account ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${account.bankLabel} ••${account.lastFourDigits ?: "----"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    account.latestBalance?.let { inr.format(it) } ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CashFlowCard(state: DashboardUiState, inr: NumberFormat, onSaveCash: (BigDecimal) -> Unit) {
    SectionCard {
        Text("Cash flow this month", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Across all accounts. Transfers between your own accounts and money lent are excluded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow("Incoming", inr.format(state.cashFlow.incoming))
        FlowRow("Outgoing", inr.format(state.cashFlow.outgoing))
        FlowRow("Invested", inr.format(state.cashFlow.invested))
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        FlowRow("Left", inr.format(state.cashFlow.left), emphasize = true)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        CashOnHandField(state.cashOnHand, onSaveCash)
    }
}

@Composable
private fun FlowRow(label: String, value: String, emphasize: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            value,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Cash on hand lives inside the cash-flow card because it is the one balance no SMS reports —
 * it only changes when the user says so. */
@Composable
private fun CashOnHandField(current: BigDecimal, onSave: (BigDecimal) -> Unit) {
    var text by remember(current) { mutableStateOf(current.toPlainString()) }
    Text("Cash on hand", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("₹") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = {
            val parsed = runCatching { BigDecimal(text.trim()) }.getOrNull()
            if (parsed != null) onSave(parsed)
        }) { Text("Save") }
    }
}

@Composable
private fun SpendingSummaryCard(state: DashboardUiState, inr: NumberFormat) {
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Spending summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                inr.format(state.monthTotalSpend),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (state.spendingBySector.isEmpty()) {
            Text(
                "No spending recorded this month yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@SectionCard
        }
        SectorBarChart(
            slices = state.spendingBySector.map {
                ChartSlice(it.category.displayName, it.amount, SectorColors.forCategory(it.category))
            },
            valueLabel = { inr.format(it) },
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun SpendBySectorCard(slices: List<ChartSlice>, monthTotal: Double, inr: NumberFormat) {
    SectionCard {
        Text("Spend by sector", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        SectorDonutChart(
            slices = slices,
            centerLabel = "This month",
            centerValue = inr.format(monthTotal),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
