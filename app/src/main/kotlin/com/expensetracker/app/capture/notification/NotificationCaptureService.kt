package com.expensetracker.app.capture.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.capture.TransactionCaptureProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Captures payment notifications from UPI apps (GPay, PhonePe, Paytm, BHIM) that don't send SMS
 * at all. This is a system-wide "Notification Access" permission — while granted, Android hands
 * this service every notification posted on the device, not just from these apps. To keep that
 * as narrow as possible in practice:
 *  - [RELEVANT_PACKAGES] is checked first so we never even read the text of a notification from
 *    an app we don't care about (WhatsApp, games, etc.) — we bail before touching its extras.
 *  - For the apps we do read, the text is parsed in-memory and immediately discarded unless it
 *    matches a transaction pattern (see TransactionParser implementations) — non-matching bodies
 *    are never written to disk or logged.
 *  - Nothing here ever leaves the device: parsing, categorization, and storage are all local.
 *
 * Extend [RELEVANT_PACKAGES] via Settings (future work) if your UPI app isn't listed.
 */
class NotificationCaptureService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in RELEVANT_PACKAGES) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val body = bigText ?: text
        val message = listOfNotNull(title, body).joinToString(separator = " ").trim()
        if (message.isBlank()) return

        val appContext = applicationContext
        val app = ExpenseTrackerApp.from(appContext)
        app.applicationScope.launch(Dispatchers.IO) {
            TransactionCaptureProcessor.process(appContext, message, sbn.packageName)
        }
    }

    companion object {
        val RELEVANT_PACKAGES = setOf(
            "com.google.android.apps.nbu.paisa.user", // Google Pay
            "com.phonepe.app",
            "net.one97.paytm",
            "in.org.npci.upiapp", // BHIM
            "com.dreamplug.androidapp", // CRED
            "in.amazon.mShop.android.shopping", // Amazon Pay notifications ride on the Amazon app
        )
    }
}
