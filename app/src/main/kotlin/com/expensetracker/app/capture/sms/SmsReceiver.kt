package com.expensetracker.app.capture.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.capture.TransactionCaptureProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires the instant a bank/UPI SMS arrives. Multi-part SMS (long messages split by the carrier)
 * are reassembled by [Telephony.Sms.Intents.getMessagesFromIntent] before we see them.
 *
 * Uses goAsync() + a background coroutine because Room writes and categorization would otherwise
 * risk exceeding the ~10s window a manifest-registered BroadcastReceiver gets on the main thread.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress
        val body = messages.joinToString(separator = "") { it.messageBody ?: "" }
        if (body.isBlank()) return

        val appContext = context.applicationContext
        val app = ExpenseTrackerApp.from(appContext)
        val pendingResult = goAsync()

        app.applicationScope.launch(Dispatchers.IO) {
            try {
                TransactionCaptureProcessor.process(appContext, body, sender)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
