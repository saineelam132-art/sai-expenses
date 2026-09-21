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
import androidx.compose.ui.Alignment
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
                onSave = { viewModel.updateLoan(loan.id, it) },
                onAddScheduleRow = viewModel::addScheduleRow,
                onDeleteScheduleRow = { viewModel.deleteScheduleRow(loan.id, it) },
            )
        }
        item { LoanForm(onSubmit = viewModel::addLoan) }

        item { HorizontalDivider() }
        item { SectionTitle("Other assets") }
        items(manualAssets, key = { it.id }) { asset ->
            ManualAssetRow(
                asset,
                onDelete = { viewModel.deleteLedgerAccount(asset.id) },
                onSave = { name, value -> viewModel.updateManualAsset(asset.id, name, value) },
            )
        }
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
        items(contacts, key = { it.id }) { contact ->
            ContactRow(
                contact,
                onDelete = { viewModel.deleteContact(contact.id) },
                onSave = { name, identifiers -> viewModel.updateContact(contact.id, name, identifiers) },
            )
        }
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
    onSave: (LoanDetails) -> Unit,
    onAddScheduleRow: (LoanScheduleEntity) -> Unit,
    onDeleteScheduleRow: (Int) -> Unit,
) {
    var showSchedule by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    if (editing) {
        LoanForm(
            onSubmit = {
                onSave(it)
                editing = false
            },
            initial = loan.toLoanDetails(),
            title = "Edit ${loan.name}",
            submitLabel = "Save changes",
            onCancel = { editing = false },
        )
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
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
                Column {
                    TextButton(onClick = { editing = true }) { Text("Edit") }
                    TextButton(onClick = onDelete) { Text("Remove") }
                }
            }
            TextButton(onClick = { showSchedule = !showSchedule }) {
                Text(if (showSchedule) "Hide schedule" else "Schedule (${schedule.size})")
            }
            if (showSchedule) {
                schedule.forEach { row ->
                    ScheduleRowLine(
                        row,
                        onDelete = { onDeleteScheduleRow(row.installmentNumber) },
                        onSave = onAddScheduleRow,
                    )
                }
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
private fun ScheduleRowLine(
    row: LoanScheduleEntity,
    onDelete: () -> Unit,
    onSave: (LoanScheduleEntity) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        ScheduleRowForm(
            loanId = row.loanId,
            installmentNumber = row.installmentNumber,
            initial = row,
            submitLabel = "Save installment",
            onSubmit = {
                // Keep the payment this row was already matched to; only the figures change.
                onSave(it.copy(matchedTransactionId = row.matchedTransactionId))
                editing = false
            },
            onCancel = { editing = false },
        )
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("#${row.installmentNumber}  ${row.dueDate}", style = MaterialTheme.typography.bodySmall)
            Text(
                "${row.totalAmount.toPlainString()} (P ${row.principalPortion.toPlainString()} / " +
                    "I ${row.interestPortion.toPlainString()})" + if (row.matchedTransactionId != null) " ✓ paid" else "",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = { editing = true }) { Text("Edit") }
        TextButton(onClick = onDelete) { Text("Remove") }
    }
}

/** Row-by-row entry of the agreement's Schedule-I table — deliberately plain: no OCR or PDF
 * parsing for v1, just the five numbers each row of the printed table already gives you. */
@Composable
private fun AddScheduleRowForm(loanId: String, nextInstallmentNumber: Int, onAdd: (LoanScheduleEntity) -> Unit) {
    ScheduleRowForm(
        loanId = loanId,
        installmentNumber = nextInstallmentNumber,
        initial = null,
        submitLabel = "Add installment",
        onSubmit = onAdd,
    )
}

/** Shared by adding a new installment and correcting an existing one. */
@Composable
private fun ScheduleRowForm(
    loanId: String,
    installmentNumber: Int,
    initial: LoanScheduleEntity?,
    submitLabel: String,
    onSubmit: (LoanScheduleEntity) -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    var dueDate by remember(initial) { mutableStateOf(initial?.dueDate?.toString().orEmpty()) }
    var principal by remember(initial) { mutableStateOf(initial?.principalPortion?.toPlainString().orEmpty()) }
    var interest by remember(initial) { mutableStateOf(initial?.interestPortion?.toPlainString().orEmpty()) }
    var total by remember(initial) { mutableStateOf(initial?.totalAmount?.toPlainString().orEmpty()) }
    var remaining by remember(initial) { mutableStateOf(initial?.remainingPrincipalAfter?.toPlainString().orEmpty()) }

    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (initial == null) "Add installment #$installmentNumber" else "Edit installment #$installmentNumber",
            fontWeight = FontWeight.Bold,
        )
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                val date = runCatching { LocalDate.parse(dueDate) }.getOrNull()
                val principalValue = principal.toBigDecimalOrNull()
                val interestValue = interest.toBigDecimalOrNull()
                val totalValue = total.toBigDecimalOrNull()
                val remainingValue = remaining.toBigDecimalOrNull()
                if (date != null && principalValue != null && interestValue != null &&
                    totalValue != null && remainingValue != null
                ) {
                    onSubmit(
                        LoanScheduleEntity(
                            loanId = loanId,
                            installmentNumber = installmentNumber,
                            dueDate = date,
                            principalPortion = principalValue,
                            interestPortion = interestValue,
                            totalAmount = totalValue,
                            remainingPrincipalAfter = remainingValue,
                        ),
                    )
                    if (initial == null) {
                        dueDate = ""; principal = ""; interest = ""; total = ""; remaining = ""
                    }
                }
            }) { Text(submitLabel) }
            if (onCancel != null) {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun ManualAssetRow(
    asset: LedgerAccountEntity,
    onDelete: () -> Unit,
    onSave: (name: String, value: BigDecimal) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var name by remember(asset) { mutableStateOf(asset.name) }
    var value by remember(asset) { mutableStateOf(asset.balance.toPlainString()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (editing) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value, { value = it }, label = { Text("Current value (₹)") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val parsed = value.toBigDecimalOrNull()
                        if (name.isNotBlank() && parsed != null) {
                            onSave(name, parsed)
                            editing = false
                        }
                    }) { Text("Save") }
                    TextButton(onClick = {
                        name = asset.name
                        value = asset.balance.toPlainString()
                        editing = false
                    }) { Text("Cancel") }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${asset.name}: ${asset.balance.toPlainString()}", modifier = Modifier.weight(1f))
                    TextButton(onClick = { editing = true }) { Text("Edit") }
                    TextButton(onClick = onDelete) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: ContactEntity,
    onDelete: () -> Unit,
    onSave: (name: String, identifiers: List<String>) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var name by remember(contact) { mutableStateOf(contact.name) }
    var identifiers by remember(contact) { mutableStateOf(contact.knownIdentifiers) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (editing) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    identifiers,
                    { identifiers = it },
                    label = { Text("Known UPI handles / names, comma-separated") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        if (name.isNotBlank()) {
                            onSave(name, identifiers.split(",").map { it.trim() }.filter { it.isNotBlank() })
                            editing = false
                        }
                    }) { Text("Save") }
                    TextButton(onClick = {
                        name = contact.name
                        identifiers = contact.knownIdentifiers
                        editing = false
                    }) { Text("Cancel") }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(contact.name, fontWeight = FontWeight.Bold)
                        Text(contact.knownIdentifiers, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { editing = true }) { Text("Edit") }
                    TextButton(onClick = onDelete) { Text("Remove") }
                }
            }
        }
    }
}

/**
 * Serves both adding and correcting a loan: pass [initial] to pre-fill it from what's stored.
 * Only the lender name and current outstanding balance are required — a loan you have no
 * paperwork for is still worth tracking as a liability. Everything else mirrors a real loan
 * agreement and can be filled in when the document is to hand.
 */
@Composable
private fun LoanForm(
    onSubmit: (LoanDetails) -> Unit,
    initial: LoanDetails? = null,
    title: String = "Add a loan",
    submitLabel: String = "Add loan",
    onCancel: (() -> Unit)? = null,
) {
    fun BigDecimal?.text() = this?.toPlainString().orEmpty()

    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var outstanding by remember(initial) { mutableStateOf(initial?.outstandingBalance.text()) }
    var disbursedDate by remember(initial) { mutableStateOf(initial?.disbursedDate?.toString().orEmpty()) }
    var principal by remember(initial) { mutableStateOf(initial?.principal.text()) }
    var rate by remember(initial) { mutableStateOf(initial?.interestRatePercent?.toString().orEmpty()) }
    var apr by remember(initial) { mutableStateOf(initial?.aprPercent?.toString().orEmpty()) }
    var emi by remember(initial) { mutableStateOf(initial?.emiAmount.text()) }
    var loanAccountNumber by remember(initial) { mutableStateOf(initial?.loanAccountNumber.orEmpty()) }
    var sanctioned by remember(initial) { mutableStateOf(initial?.sanctionedAmount.text()) }
    var tenure by remember(initial) { mutableStateOf(initial?.tenureMonths?.toString().orEmpty()) }
    var processingFee by remember(initial) { mutableStateOf(initial?.processingFee.text()) }
    var insurance by remember(initial) { mutableStateOf(initial?.insuranceCharge.text()) }
    var penalTerms by remember(initial) { mutableStateOf(initial?.penalChargeTerms.orEmpty()) }
    var foreclosureTerms by remember(initial) { mutableStateOf(initial?.foreclosureChargeTerms.orEmpty()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val outstandingValue = outstanding.toBigDecimalOrNull()
                    if (name.isNotBlank() && outstandingValue != null) {
                        onSubmit(
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
                        // Only the add form resets; an edit form is dismissed by its caller, and
                        // clearing it would blank the fields for a moment on the way out.
                        if (initial == null) {
                            name = ""; outstanding = ""; disbursedDate = ""; principal = ""; rate = ""; apr = ""
                            emi = ""; loanAccountNumber = ""; sanctioned = ""; tenure = ""
                            processingFee = ""; insurance = ""; penalTerms = ""; foreclosureTerms = ""
                        }
                    }
                }) { Text(submitLabel) }
                if (onCancel != null) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }
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
