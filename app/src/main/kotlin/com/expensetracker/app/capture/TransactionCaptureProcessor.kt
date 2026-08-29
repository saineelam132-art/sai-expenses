package com.expensetracker.app.capture

import android.content.Context
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.notify.TransactionNotifier
import com.expensetracker.app.worker.BudgetAlertChecker
import com.expensetracker.core.parser.ParserRegistry
import kotlinx.coroutines.flow.first

/**
 * Single entry point both [com.expensetracker.app.capture.sms.SmsReceiver] and
 * [com.expensetracker.app.capture.notification.NotificationCaptureService] funnel into:
 * parse -> categorize -> persist -> notify -> check budgets. Keeping this in one place means
 * SMS and UPI-app-notification transactions are guaranteed to be handled identically.
 */
object TransactionCaptureProcessor {
    private val registry = ParserRegistry()

    suspend fun process(context: Context, message: String, sender: String?) {
        val parsed = registry.parse(message, sender) ?: return // not a transaction message at all
        val app = ExpenseTrackerApp.from(context)
        val threshold = app.settingsRepository.largeTransactionThreshold.first()
        val saved = app.transactionRepository.recordTransaction(parsed, threshold)
        TransactionNotifier.notify(context, saved)
        BudgetAlertChecker.checkAfterTransaction(context, saved)
    }
}
