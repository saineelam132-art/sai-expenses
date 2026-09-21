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
import com.expensetracker.app.data.db.LoanScheduleEntity
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
        item {
            Text(
                "Enter a loan exactly as its agreement states. If you have the document, add the " +
                    "full installment schedule too — repayments are matched against those rows, " +
                    "and the principal/interest split is taken from them rather than calculated.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        items(loans, key = { it.id }) { loan ->
            LoanRow(
                loan,
                schedule = viewModel.scheduleFor(loan.id).collectAsState().value,
                onDelete = { viewModel.deleteLedgerAccount(loan.id) },
                onAddScheduleRow = viewModel::addScheduleRow,
            )
        }
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
private fun LoanRow(
    loan: LedgerAccountEntity,
    schedule: List<LoanScheduleEntity>,
    onDelete: () -> Unit,
    onAddScheduleRow: (LoanScheduleEntity) -> Unit,
) {
    var showSchedule by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(loan.name, fontWeight = FontWeight.Bold)
                    Text(
                        "Outstanding: ${loan.balance.toPlainString()} · Rate: ${loan.interestRatePercent ?: 0.0}%" +
                            (loan.aprPercent?.let { " · APR: $it%" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    loan.loanAccountNumber?.let {
                        Text("A/c $it", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        if (schedule.isEmpty()) {
                            "No schedule entered — repayments will need manual matching"
                        } else {
                            "${schedule.count { it.matchedTransactionId != null }} of ${schedule.size} installments paid"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onDelete) { Text("Remove") }
            }
            TextButton(onClick = { showSchedule = !showSchedule }) {
                Text(if (showSchedule) "Hide schedule" else "Schedule (${schedule.size})")
            }
            if (showSchedule) {
                schedule.forEach { row -> ScheduleRowLine(row) }
                AddScheduleRowForm(
                    loanId = loan.id,
                    nextInstallmentNumber = (schedule.maxOfOrNull { it.installmentNumber } ?: 0) + 1,
                    onAdd = onAddScheduleRow,
                )
            }
        }
    }
}

@Composable
private fun ScheduleRowLine(row: LoanScheduleEntity) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("#${row.installmentNumber}  ${row.dueDate}", style = MaterialTheme.typography.bodySmall)
        Text(
            "${row.totalAmount.toPlainString()} (P ${row.principalPortion.toPlainString()} / " +
                "I ${row.interestPortion.toPlainString()})" + if (row.matchedTransactionId != null) " ✓" else "",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Row-by-row entry of the agreement's Schedule-I table — deliberately plain: no OCR or PDF
 * parsing for v1, just the five numbers each row of the printed table already gives you. */
@Composable
private fun AddScheduleRowForm(loanId: String, nextInstallmentNumber: Int, onAdd: (LoanScheduleEntity) -> Unit) {
    var dueDate by remember { mutableStateOf("") }
    var principal by remember { mutableStateOf("") }
    var interest by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    var remaining by remember { mutableStateOf("") }

    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Add installment #$nextInstallmentNumber", fontWeight = FontWeight.Bold)
        OutlinedTextField(dueDate, { dueDate = it }, label = { Text("Due date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(principal, { principal = it }, label = { Text("Principal") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(interest, { interest = it }, label = { Text("Interest") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(total, { total = it }, label = { Text("Installment amount") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            remaining,
            { remaining = it },
            label = { Text("Remaining principal after") },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = {
            val date = runCatching { LocalDate.parse(dueDate) }.getOrNull()
            val principalValue = principal.toBigDecimalOrNull()
            val interestValue = interest.toBigDecimalOrNull()
            val totalValue = total.toBigDecimalOrNull()
            val remainingValue = remaining.toBigDecimalOrNull()
            if (date != null && principalValue != null && interestValue != null &&
                totalValue != null && remainingValue != null
            ) {
                onAdd(
                    LoanScheduleEntity(
                        loanId = loanId,
                        installmentNumber = nextInstallmentNumber,
                        dueDate = date,
                        principalPortion = principalValue,
                        interestPortion = interestValue,
                        totalAmount = totalValue,
                        remainingPrincipalAfter = remainingValue,
                    ),
                )
                dueDate = ""; principal = ""; interest = ""; total = ""; remaining = ""
            }
        }) { Text("Add installment") }
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

/**
 * Only the lender name and current outstanding balance are required — a loan you have no
 * paperwork for is still worth tracking as a liability. Everything else mirrors a real loan
 * agreement and can be filled in when the document is to hand.
 */
@Composable
private fun AddLoanForm(onAdd: (LoanDetails) -> Unit) {
    var name by remember { mutableStateOf("") }
    var outstanding by remember { mutableStateOf("") }
    var disbursedDate by remember { mutableStateOf("") }
    var principal by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var apr by remember { mutableStateOf("") }
    var emi by remember { mutableStateOf("") }
    var loanAccountNumber by remember { mutableStateOf("") }
    var sanctioned by remember { mutableStateOf("") }
    var tenure by remember { mutableStateOf("") }
    var processingFee by remember { mutableStateOf("") }
    var insurance by remember { mutableStateOf("") }
    var penalTerms by remember { mutableStateOf("") }
    var foreclosureTerms by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Add a loan", fontWeight = FontWeight.Bold)
            OutlinedTextField(name, { name = it }, label = { Text("Lender / name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                outstanding,
                { outstanding = it },
                label = { Text("Current outstanding balance (₹)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                disbursedDate,
                { disbursedDate = it },
                label = { Text("Disbursed date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                loanAccountNumber,
                { loanAccountNumber = it },
                label = { Text("Loan account number") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(sanctioned, { sanctioned = it }, label = { Text("Sanctioned amount (₹)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(principal, { principal = it }, label = { Text("Original principal (₹)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tenure, { tenure = it }, label = { Text("Tenure (months)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(rate, { rate = it }, label = { Text("Interest rate (%)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(apr, { apr = it }, label = { Text("APR (%) — includes fees") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(emi, { emi = it }, label = { Text("EMI amount (₹, optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                processingFee,
                { processingFee = it },
                label = { Text("Processing fee (₹, one-time)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                insurance,
                { insurance = it },
                label = { Text("Insurance / other charge (₹, one-time)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(penalTerms, { penalTerms = it }, label = { Text("Penal charge terms") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                foreclosureTerms,
                { foreclosureTerms = it },
                label = { Text("Foreclosure charge terms") },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = {
                val outstandingValue = outstanding.toBigDecimalOrNull()
                if (name.isNotBlank() && outstandingValue != null) {
                    onAdd(
                        LoanDetails(
                            name = name,
                            outstandingBalance = outstandingValue,
                            disbursedDate = runCatching { LocalDate.parse(disbursedDate) }.getOrNull(),
                            principal = principal.toBigDecimalOrNull(),
                            interestRatePercent = rate.toDoubleOrNull(),
                            emiAmount = emi.toBigDecimalOrNull(),
                            loanAccountNumber = loanAccountNumber.takeIf { it.isNotBlank() },
                            sanctionedAmount = sanctioned.toBigDecimalOrNull(),
                            tenureMonths = tenure.toIntOrNull(),
                            aprPercent = apr.toDoubleOrNull(),
                            processingFee = processingFee.toBigDecimalOrNull(),
                            insuranceCharge = insurance.toBigDecimalOrNull(),
                            penalChargeTerms = penalTerms.takeIf { it.isNotBlank() },
                            foreclosureChargeTerms = foreclosureTerms.takeIf { it.isNotBlank() },
                        ),
                    )
                    name = ""; outstanding = ""; disbursedDate = ""; principal = ""; rate = ""; apr = ""
                    emi = ""; loanAccountNumber = ""; sanctioned = ""; tenure = ""
                    processingFee = ""; insurance = ""; penalTerms = ""; foreclosureTerms = ""
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
