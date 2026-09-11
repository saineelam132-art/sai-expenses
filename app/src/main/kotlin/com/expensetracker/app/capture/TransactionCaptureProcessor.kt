package com.expensetracker.app.capture

import android.content.Context
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.diagnostics.CrashLog
import com.expensetracker.app.notify.TransactionNotifier
import com.expensetracker.app.worker.BudgetAlertChecker
import com.expensetracker.core.parser.ParserRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Single entry point both [com.expensetracker.app.capture.sms.SmsReceiver] and
 * [com.expensetracker.app.capture.notification.NotificationCaptureService] funnel into:
 * parse -> categorize -> persist -> notify -> check budgets. Keeping this in one place means
 * SMS and UPI-app-notification transactions are guaranteed to be handled identically.
 *
 * The whole body is wrapped defensively: this runs unattended, possibly hundreds of times a day,
 * triggered by content this app doesn't control (another app's SMS/notification text). One
 * malformed or unexpected message must never crash the process — it gets logged via [CrashLog]
 * and the rest of the app keeps working, rather than one bad SMS taking the whole app down.
 */
object TransactionCaptureProcessor {
    private val registry = ParserRegistry()

    suspend fun process(context: Context, message: String, sender: String?) {
        try {
            val parsed = registry.parse(message, sender) ?: return // not a transaction message at all
            val app = ExpenseTrackerApp.from(context)
            val threshold = app.settingsRepository.largeTransactionThreshold.first()
            val saved = app.transactionRepository.recordTransaction(parsed, threshold)
            TransactionNotifier.notify(context, saved)
            BudgetAlertChecker.checkAfterTransaction(context, saved)
        } catch (c: CancellationException) {
            throw c // structured concurrency: cancellation must propagate, never be swallowed
        } catch (t: Throwable) {
            CrashLog.record(
                context,
                "TransactionCaptureProcessor",
                IllegalStateException("Failed to process message from sender=$sender: $message", t),
            )
        }
    }
}
