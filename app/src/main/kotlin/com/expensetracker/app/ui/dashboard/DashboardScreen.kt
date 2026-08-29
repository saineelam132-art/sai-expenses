package com.expensetracker.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.expensetracker.app.ui.charts.ChartSlice
import com.expensetracker.app.ui.charts.SectorPieChart
import com.expensetracker.app.ui.theme.SectorChartColors
import com.expensetracker.core.model.Category
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DashboardScreen(factory: AppViewModelFactory) {
    val viewModel: DashboardViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsState()
    val inr = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Total balance", style = MaterialTheme.typography.labelLarge)
                    Text(inr.format(state.totalBalance), style = MaterialTheme.typography.headlineMedium)
                    state.accounts.forEach { account ->
                        Text(
                            "${account.bankLabel} ••${account.lastFourDigits ?: "----"}: " +
                                (account.latestBalance?.let { inr.format(it) } ?: "—"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (state.accounts.isEmpty()) {
                        Text(
                            "No balance yet — it appears after the first bank SMS with a balance field.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("This month by sector", style = MaterialTheme.typography.titleMedium)
                    val slices = state.currentMonthSpend.map { (category, amount) ->
                        ChartSlice(category.displayName, amount, colorFor(category))
                    }
                    SectorPieChart(slices, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        if (state.trends.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Spending jumped vs last month",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        state.trends.forEach { trend ->
                            Text(
                                "${trend.category.displayName}: ${inr.format(trend.thisMonth)} " +
                                    "(was ${inr.format(trend.lastMonth)})",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        if (state.recurringPayments.isNotEmpty()) {
            item {
                Text("Recurring payments", style = MaterialTheme.typography.titleMedium)
            }
            items(state.recurringPayments) { payment ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(payment.merchant, fontWeight = FontWeight.Bold)
                        Text(
                            "${inr.format(payment.amount)} · seen ${payment.occurrences} times",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private fun colorFor(category: Category) = SectorChartColors[category.ordinal % SectorChartColors.size]
