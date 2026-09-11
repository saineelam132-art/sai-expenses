package com.expensetracker.app.worker

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.data.db.BudgetEntity
import com.expensetracker.app.data.db.TransactionEntity
import com.expensetracker.app.notify.NotificationChannels
import com.expensetracker.core.model.TransactionKind
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Checks the just-saved transaction's sector against its monthly budget and fires an 80%/100%
 * crossing alert at most once per threshold per month (tracked on [BudgetEntity] itself so this
 * survives process death).
 */
object BudgetAlertChecker {
    private val monthKeyFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    private val inr = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    suspend fun checkAfterTransaction(context: Context, transaction: TransactionEntity) {
        // Only real spending counts toward a sector budget — a Self-Transfer, Lent payment, or
        // Loan-Disbursed debit isn't spending in that sector (sumSpendForCategory already
        // excludes them; this gate just avoids a pointless recheck + possible confusing alert).
        if (transaction.kind != TransactionKind.EXPENSE) return

        val app = ExpenseTrackerApp.from(context)
        val budget = app.database.budgetDao().getByCategory(transaction.category) ?: return

        val now = LocalDateTime.now()
        val monthStart = LocalDate.of(now.year, now.month, 1).atStartOfDay()
        val monthKey = now.format(monthKeyFormatter)

        val spent = app.database.transactionDao().sumSpendForCategory(
            transaction.category,
            monthStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        val limit = budget.monthlyLimit.toDouble()
        if (limit <= 0.0) return
        val percent = (spent / limit) * 100.0

        val alreadyAlertedThisMonth = budget.lastAlertMonthKey == monthKey
        val alreadyAlertedPercent = if (alreadyAlertedThisMonth) budget.lastAlertPercentForMonth else 0

        val crossedThreshold = when {
            percent >= 100.0 && alreadyAlertedPercent < 100 -> 100
            percent >= 80.0 && alreadyAlertedPercent < 80 -> 80
            else -> null
        } ?: return

        notifyBudgetCrossing(context, transaction.category.displayName, crossedThreshold, spent, limit)

        app.database.budgetDao().upsert(
            budget.copy(lastAlertPercentForMonth = crossedThreshold, lastAlertMonthKey = monthKey),
        )
    }

    private fun notifyBudgetCrossing(context: Context, categoryName: String, percent: Int, spent: Double, limit: Double) {
        val title = if (percent >= 100) "$categoryName budget exceeded" else "$categoryName budget at 80%"
        val body = "You've spent ${inr.format(spent)} of your ${inr.format(limit)} monthly $categoryName budget."

        val notification = NotificationCompat.Builder(context, NotificationChannels.BUDGET_ALERTS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) {
            try {
                manager.notify(("budget-$categoryName").hashCode(), notification)
            } catch (_: SecurityException) {
                // Notification permission not granted; budget state above is still updated.
            }
        }
    }
}
