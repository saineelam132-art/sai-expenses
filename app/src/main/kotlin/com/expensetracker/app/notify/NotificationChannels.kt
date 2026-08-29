package com.expensetracker.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {
    const val TRANSACTIONS = "transactions"
    const val LARGE_TRANSACTIONS = "large_transactions"
    const val BUDGET_ALERTS = "budget_alerts"
    const val WEEKLY_SUMMARY = "weekly_summary"

    fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                TRANSACTIONS,
                "Transaction alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "A notification every time a transaction is captured" },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                LARGE_TRANSACTIONS,
                "Large transaction alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Prominent alert for transactions above your threshold" },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                BUDGET_ALERTS,
                "Budget alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Alerts when you cross 80% or 100% of a sector budget" },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                WEEKLY_SUMMARY,
                "Weekly summary",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Weekly total spend, top sectors, and balance trend" },
        )
    }
}
