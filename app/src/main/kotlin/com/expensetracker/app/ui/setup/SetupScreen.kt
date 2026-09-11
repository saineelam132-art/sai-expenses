package com.expensetracker.app.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.expensetracker.app.data.db.ContactEntity
import com.expensetracker.app.data.db.LedgerAccountEntity
import com.expensetracker.app.ui.AppViewModelFactory
import java.math.BigDecimal
import java.time.LocalDate

/**
 * One-time (but always editable) setup for the things no SMS can tell the app: your starting
 * cash-on-hand, existing loans (with their real terms), other assets you want on the balance
 * sheet, and the Friends contacts needed for lend/borrow auto-detection.
 */
@Composable
fun SetupScreen(factory: AppViewModelFactory) {
    val viewModel: SetupViewModel = viewModel(factory = factory)
    val cashBalance by viewModel.cashBalance.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val manualAssets by viewModel.manualAssets.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val ownNameVariants by viewModel.ownNameVariants.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("Your name") }
        item {
            Text(
                "Used to tell a Slice loan disbursement (money sent to yourself) apart from a Slice-funded " +
                    "payment to someone else.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item { OwnNameRow(ownNameVariants, onSave = viewModel::setOwnNameVariants) }

        item { HorizontalDivider() }
        item { SectionTitle("Cash on hand") }
        item { CashBalanceRow(cashBalance, onSave = viewModel::setCashBalance) }

        item { HorizontalDivider() }
        item { SectionTitle("Loans") }
        items(loans, key = { it.id }) { loan -> LoanRow(loan, onDelete = { viewModel.deleteLedgerAccount(loan.id) }) }
        item { AddLoanForm(onAdd = viewModel::addLoan) }

        item { HorizontalDivider() }
        item { SectionTitle("Other assets") }
        items(manualAssets, key = { it.id }) { asset -> ManualAssetRow(asset, onDelete = { viewModel.deleteLedgerAccount(asset.id) }) }
        item { AddManualAssetForm(onAdd = viewModel::addManualAsset) }

        item { HorizontalDivider() }
        item { SectionTitle("Friends") }
        item {
            Text(
                "People you lend to or borrow from — transactions to/from these get suggested as " +
                    "Lent/Borrowed instead of a regular expense.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        items(contacts, key = { it.id }) { contact -> ContactRow(contact, onDelete = { viewModel.deleteContact(contact.id) }) }
        item { AddContactForm(onAdd = viewModel::addContact) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun OwnNameRow(current: Set<String>, onSave: (Set<String>) -> Unit) {
    var text by remember(current) { mutableStateOf(current.joinToString(", ")) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Your name, comma-separated variants") },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = {
                val names = text.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                if (names.isNotEmpty()) onSave(names)
            }) { Text("Save") }
        }
    }
}

@Composable
private fun CashBalanceRow(current: BigDecimal, onSave: (BigDecimal) -> Unit) {
    var text by remember(current) { mutableStateOf(current.toPlainString()) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Current cash balance (₹)") },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { text.toBigDecimalOrNull()?.let(onSave) }) { Text("Save") }
        }
    }
}

@Composable
private fun LoanRow(loan: LedgerAccountEntity, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(loan.name, fontWeight = FontWeight.Bold)
                Text(
                    "Outstanding: ${loan.balance.toPlainString()} · Rate: ${loan.interestRatePercent ?: 0.0}%",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onDelete) { Text("Remove") }
        }
    }
}

@Composable
private fun ManualAssetRow(asset: LedgerAccountEntity, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${asset.name}: ${asset.balance.toPlainString()}")
            TextButton(onClick = onDelete) { Text("Remove") }
        }
    }
}

@Composable
private fun ContactRow(contact: ContactEntity, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(contact.name, fontWeight = FontWeight.Bold)
                Text(contact.knownIdentifiers, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onDelete) { Text("Remove") }
        }
    }
}

@Composable
private fun AddLoanForm(
    onAdd: (name: String, principal: BigDecimal, ratePercent: Double, disbursedDate: LocalDate, outstanding: BigDecimal, emi: BigDecimal?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var principal by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var disbursedDate by remember { mutableStateOf("") }
    var outstanding by remember { mutableStateOf("") }
    var emi by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Add a loan", fontWeight = FontWeight.Bold)
            OutlinedTextField(name, { name = it }, label = { Text("Lender / name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(principal, { principal = it }, label = { Text("Original principal (₹)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(rate, { rate = it }, label = { Text("Annual interest rate (%)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                disbursedDate,
                { disbursedDate = it },
                label = { Text("Disbursed date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                outstanding,
                { outstanding = it },
                label = { Text("Current outstanding balance (₹)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(emi, { emi = it }, label = { Text("EMI amount (₹, optional)") }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = {
                val principalValue = principal.toBigDecimalOrNull()
                val rateValue = rate.toDoubleOrNull()
                val dateValue = runCatching { LocalDate.parse(disbursedDate) }.getOrNull()
                val outstandingValue = outstanding.toBigDecimalOrNull()
                if (name.isNotBlank() && principalValue != null && rateValue != null && dateValue != null && outstandingValue != null) {
                    onAdd(name, principalValue, rateValue, dateValue, outstandingValue, emi.toBigDecimalOrNull())
                    name = ""; principal = ""; rate = ""; disbursedDate = ""; outstanding = ""; emi = ""
                }
            }) { Text("Add loan") }
        }
    }
}

@Composable
private fun AddManualAssetForm(onAdd: (name: String, value: BigDecimal) -> Unit) {
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Add an asset", fontWeight = FontWeight.Bold)
            OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Gold, FD, Vehicle)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value, { value = it }, label = { Text("Current value (₹)") }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = {
                val v = value.toBigDecimalOrNull()
                if (name.isNotBlank() && v != null) {
                    onAdd(name, v)
                    name = ""; value = ""
                }
            }) { Text("Add asset") }
        }
    }
}

@Composable
private fun AddContactForm(onAdd: (name: String, identifiers: List<String>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var identifiers by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Add a friend", fontWeight = FontWeight.Bold)
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                identifiers,
                { identifiers = it },
                label = { Text("Known UPI handles / names, comma-separated") },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onAdd(name, identifiers.split(",").map { it.trim() }.filter { it.isNotBlank() })
                    name = ""; identifiers = ""
                }
            }) { Text("Add friend") }
        }
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = try {
    if (isBlank()) null else BigDecimal(this)
} catch (_: NumberFormatException) {
    null
}
