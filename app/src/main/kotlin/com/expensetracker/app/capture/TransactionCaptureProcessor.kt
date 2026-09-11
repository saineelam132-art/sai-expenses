package com.expensetracker.app.capture

import android.content.Context
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.data.db.AccountEntity
import com.expensetracker.app.data.db.RecurringBillEntity
import com.expensetracker.app.data.db.RecurringBillStatus
import com.expensetracker.app.diagnostics.CrashLog
import com.expensetracker.app.notify.TransactionNotifier
import com.expensetracker.app.worker.BudgetAlertChecker
import com.expensetracker.core.model.MandateAction
import com.expensetracker.core.model.NonTransactionEvent
import com.expensetracker.core.parser.NonTransactionClassifier
import com.expensetracker.core.parser.ParserRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Single entry point both [com.expensetracker.app.capture.sms.SmsReceiver] and
 * [com.expensetracker.app.capture.notification.NotificationCaptureService] funnel into.
 * [NonTransactionClassifier] runs first — OTPs, broker balance-disclosure pings, UPI AutoPay
 * mandate lifecycle messages, and declined-payment notices are never transactions at all and
 * must never reach the transaction parsers or land in the review list. Everything else goes
 * through: parse -> categorize -> infer kind -> persist -> post to the ledger -> notify -> check
 * budgets. Keeping this in one place means SMS and UPI-app-notification transactions are
 * guaranteed to be handled identically.
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
            val nonTransactionEvent = NonTransactionClassifier.classify(message, sender)
            if (nonTransactionEvent != null) {
                handleNonTransactionEvent(context, nonTransactionEvent)
                return
            }

            val parsed = registry.parse(message, sender) ?: return // not a transaction message at all
            val app = ExpenseTrackerApp.from(context)
            val threshold = app.settingsRepository.largeTransactionThreshold.first()
            val saved = app.transactionRepository.recordTransaction(context, parsed, threshold)
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

    private suspend fun handleNonTransactionEvent(context: Context, event: NonTransactionEvent) {
        val app = ExpenseTrackerApp.from(context)
        when (event) {
            is NonTransactionEvent.Discard -> Unit

            is NonTransactionEvent.BalanceOnly -> {
                val accountHint = event.accountHint ?: return // nothing to attribute the balance to
                app.database.accountDao().upsert(
                    AccountEntity(
                        id = "${event.sourceLabel}-$accountHint",
                        bankLabel = event.sourceLabel,
                        lastFourDigits = accountHint,
                        latestBalance = event.balance,
                        lastUpdated = System.currentTimeMillis(),
                    ),
                )
            }

            is NonTransactionEvent.MandateEvent -> {
                val merchantKey = event.merchant ?: "unknown"
                val status = when (event.action) {
                    MandateAction.CREATED -> RecurringBillStatus.ACTIVE
                    MandateAction.PAUSED -> RecurringBillStatus.PAUSED
                    MandateAction.REVOKED -> RecurringBillStatus.REVOKED
                }
                app.database.recurringBillDao().upsert(
                    RecurringBillEntity(
                        id = "${event.sourceLabel}-$merchantKey",
                        merchant = event.merchant ?: "Unknown",
                        expectedAmount = event.amount,
                        sourceLabel = event.sourceLabel,
                        status = status,
                        validFrom = event.validFrom,
                        validTo = event.validTo,
                        referenceId = event.referenceId,
                    ),
                )
            }
        }
    }
}
