package com.expensetracker.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Small key-value app settings — everything else lives in Room. */
class SettingsRepository(private val context: Context) {

    val largeTransactionThreshold: Flow<BigDecimal> =
        context.dataStore.data.map { prefs ->
            BigDecimal.valueOf(prefs[LARGE_TXN_THRESHOLD] ?: DEFAULT_LARGE_TXN_THRESHOLD)
        }

    suspend fun setLargeTransactionThreshold(value: BigDecimal) {
        context.dataStore.edit { it[LARGE_TXN_THRESHOLD] = value.toDouble() }
    }

    val encryptedBackupEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[ENCRYPTED_BACKUP_ENABLED] ?: false }

    suspend fun setEncryptedBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[ENCRYPTED_BACKUP_ENABLED] = enabled }
    }

    val weeklySummaryEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[WEEKLY_SUMMARY_ENABLED] ?: true }

    suspend fun setWeeklySummaryEnabled(enabled: Boolean) {
        context.dataStore.edit { it[WEEKLY_SUMMARY_ENABLED] = enabled }
    }

    /** Name variants that identify "this is me" as a payee — used to tell a Slice loan
     * disbursement (sent to myself) apart from a Slice-funded payment to a merchant. */
    val ownNameVariants: Flow<Set<String>> =
        context.dataStore.data.map { it[OWN_NAME_VARIANTS] ?: DEFAULT_OWN_NAME_VARIANTS }

    suspend fun setOwnNameVariants(names: Set<String>) {
        context.dataStore.edit { it[OWN_NAME_VARIANTS] = names }
    }

    companion object {
        const val DEFAULT_LARGE_TXN_THRESHOLD = 5000.0
        val DEFAULT_OWN_NAME_VARIANTS = setOf("Neelam Sai Ram Ganesh", "Sai Ram Ganesh")
        private val LARGE_TXN_THRESHOLD = doublePreferencesKey("large_transaction_threshold")
        private val ENCRYPTED_BACKUP_ENABLED = booleanPreferencesKey("encrypted_backup_enabled")
        private val WEEKLY_SUMMARY_ENABLED = booleanPreferencesKey("weekly_summary_enabled")
        private val OWN_NAME_VARIANTS = stringSetPreferencesKey("own_name_variants")
    }
}
