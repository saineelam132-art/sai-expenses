package com.expensetracker.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.app.ui.AppViewModelFactory
import com.expensetracker.core.model.Category
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.YearMonth

@Composable
fun SettingsScreen(factory: AppViewModelFactory, onOpenSetup: () -> Unit = {}) {
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val threshold by viewModel.largeTransactionThreshold.collectAsState()
    val weeklySummaryEnabled by viewModel.weeklySummaryEnabled.collectAsState()
    val encryptedBackupEnabled by viewModel.encryptedBackupEnabled.collectAsState()
    val keywordRules by viewModel.keywordRules.collectAsState()
    val budgets by viewModel.budgets.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("Accounts & money") }
        item {
            TextButton(onClick = onOpenSetup) {
                Text("Cash, loans, assets & friends setup")
            }
        }

        item { HorizontalDivider() }
        item { SectionTitle("Alert thresholds") }
        item {
            var thresholdText by remember(threshold) { mutableStateOf(threshold.toPlainString()) }
            OutlinedTextField(
                value = thresholdText,
                onValueChange = { thresholdText = it },
                label = { Text("Large transaction threshold (₹)") },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = {
                thresholdText.toBigDecimalOrNull()?.let { viewModel.setLargeTransactionThreshold(it) }
            }) { Text("Save threshold") }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Weekly summary notification")
                Switch(checked = weeklySummaryEnabled, onCheckedChange = viewModel::setWeeklySummaryEnabled)
            }
        }

        item {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Encrypted backup (optional)")
                    Switch(checked = encryptedBackupEnabled, onCheckedChange = viewModel::setEncryptedBackupEnabled)
                }
                Text(
                    "Off by default — all data stays local. When enabled, use \"Export CSV\" below; " +
                        "a fully automatic encrypted device backup is on the roadmap (see README).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item { HorizontalDivider() }
        item { SectionTitle("Budgets (per month, per sector)") }
        items(Category.entries.filter { it != Category.UNCATEGORIZED }) { category ->
            val existing = budgets.firstOrNull { it.category == category }
            var text by remember(existing?.monthlyLimit) { mutableStateOf(existing?.monthlyLimit?.toPlainString() ?: "") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(category.displayName, modifier = Modifier.padding(top = 12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("₹/month") },
                    modifier = Modifier.padding(start = 8.dp),
                    trailingIcon = {
                        TextButton(onClick = { text.toBigDecimalOrNull()?.let { viewModel.setBudget(category, it) } }) {
                            Text("Save")
                        }
                    },
                )
            }
        }

        item { HorizontalDivider() }
        item { SectionTitle("Merchant keyword rules") }
        item { AddKeywordRuleRow(onAdd = viewModel::addOrUpdateKeywordRule) }
        items(keywordRules, key = { it.keyword }) { rule ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${rule.keyword} → ${rule.category.displayName}")
                IconButton(onClick = { viewModel.removeKeywordRule(rule.keyword) }) {
                    Text("✕")
                }
            }
        }

        item { HorizontalDivider() }
        item { SectionTitle("Export") }
        item {
            var monthText by remember { mutableStateOf(YearMonth.now().toString()) }
            var exportError by remember { mutableStateOf<String?>(null) }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = monthText,
                        onValueChange = { monthText = it },
                        label = { Text("Month (YYYY-MM), blank = all time") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                exportError = null
                                val month = runCatching { YearMonth.parse(monthText) }.getOrNull()
                                val uri = viewModel.exportCsv(month, category = null)
                                if (uri != null) {
                                    context.startActivity(
                                        Intent.createChooser(
                                            com.expensetracker.app.export.CsvExporter.shareIntent(uri),
                                            "Export transactions",
                                        ),
                                    )
                                } else {
                                    exportError = "Export failed — check the diagnostic log in Settings for details."
                                }
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Export CSV") }
                    exportError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item { HorizontalDivider() }
        item { SectionTitle("Diagnostics") }
        item {
            var noLogYet by remember { mutableStateOf(false) }
            Column {
                TextButton(onClick = {
                    val intent = com.expensetracker.app.diagnostics.CrashLog.shareIntent(context)
                    if (intent != null) {
                        context.startActivity(Intent.createChooser(intent, "Share diagnostic log"))
                    } else {
                        noLogYet = true
                    }
                }) { Text("Share diagnostic log") }
                if (noLogYet) {
                    Text(
                        "No errors logged yet — nothing to share.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun AddKeywordRuleRow(onAdd: (String, Category) -> Unit) {
    var keyword by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(Category.MISCELLANEOUS) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            label = { Text("Keyword") },
            modifier = Modifier,
        )
        Box {
            TextButton(onClick = { expanded = true }) { Text(selected.displayName) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                Category.entries.filter { it != Category.UNCATEGORIZED }.forEach { category ->
                    DropdownMenuItem(text = { Text(category.displayName) }, onClick = {
                        selected = category
                        expanded = false
                    })
                }
            }
        }
        TextButton(onClick = {
            if (keyword.isNotBlank()) {
                onAdd(keyword, selected)
                keyword = ""
            }
        }) { Text("Add") }
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = try {
    BigDecimal(this)
} catch (_: NumberFormatException) {
    null
}
