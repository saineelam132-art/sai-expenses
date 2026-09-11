package com.expensetracker.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A person (not a merchant/bill) you lend to or borrow from — see
 * [com.expensetracker.core.model.TransactionKind.requiresContact]. [knownIdentifiers] is a
 * comma-separated list of normalized merchant/VPA strings (same normalization as
 * [com.expensetracker.core.categorize.CategoryEngine.normalize]) used to auto-match an incoming
 * transaction's payee to this contact.
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val knownIdentifiers: String = "",
)
