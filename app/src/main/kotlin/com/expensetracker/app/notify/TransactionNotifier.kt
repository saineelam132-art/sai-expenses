package com.expensetracker.app.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.expensetracker.app.data.db.TransactionEntity
import com.expensetracker.app.ui.MainActivity
import com.expensetracker.core.model.TransactionType
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Fires the "instant notification after every transaction" requirement. Regular transactions
 * use a normal-priority channel; anything at/above the user's large-transaction threshold uses a
 * high-priority channel and deep-links into a category-confirmation screen instead of just the
 * dashboard.
 */
object TransactionNotifier {
    private val inr: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    fun notify(context: Context, transaction: TransactionEntity) {
        val amountText = transaction.amount?.let { formatAmount(it) } ?: "an amount we couldn't read"
        val merchantText = transaction.merchant ?: "an unknown payee"
        val balanceText = transaction.availableBalance?.let { " Balance: ${formatAmount(it)}." } ?: ""

        val title: String
        val body: String
        val channel: String

        if (transaction.needsReview) {
            title = "Transaction needs review"
            body = "$amountText ${verb(transaction.type)} — couldn't confidently parse or categorize this one. Tap to review."
            channel = NotificationChannels.TRANSACTIONS
        } else if (transaction.isLargeTransaction) {
            title = "Large transaction: $amountText"
            body = "$amountText ${verb(transaction.type)} at $merchantText — categorized under " +
                "${transaction.category.displayName}.$balanceText Tap to confirm the category."
            channel = NotificationChannels.LARGE_TRANSACTIONS
        } else {
            title = "$amountText ${verb(transaction.type)}"
            body = "At $merchantText — categorized under ${transaction.category.displayName}.$balanceText"
            channel = NotificationChannels.TRANSACTIONS
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            transaction.id.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_TRANSACTION_ID, transaction.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_notify_chat) // replace with a branded icon asset
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (transaction.isLargeTransaction) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT,
            )
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) {
            try {
                manager.notify(transaction.id.toInt(), notification)
            } catch (_: SecurityException) {
                // POST_NOTIFICATIONS not granted on API 33+; the transaction is still saved,
                // it just won't surface a notification until the user grants the permission.
            }
        }
    }

    private fun verb(type: TransactionType): String = when (type) {
        TransactionType.DEBIT -> "spent"
        TransactionType.CREDIT -> "received"
        TransactionType.UNKNOWN -> "recorded"
    }

    private fun formatAmount(amount: BigDecimal): String = inr.format(amount)
}
