package com.expensetracker.app.worker

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.expensetracker.app.ExpenseTrackerApp
import com.expensetracker.app.notify.NotificationChannels
import com.expensetracker.core.model.TransactionType
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Weekly "total spent, top 3 sectors, balance trend" notification. */
class WeeklySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = ExpenseTrackerApp.from(applicationContext)
        if (!app.settingsRepository.weeklySummaryEnabled.first()) return Result.success()

        val now = LocalDateTime.now()
        val weekAgo = now.minusDays(7)
        val nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val weekAgoMillis = weekAgo.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val transactions = app.database.transactionDao().getInRange(weekAgoMillis, nowMillis)
        val debits = transactions.filter { it.type == TransactionType.DEBIT && it.amount != null }

        val totalSpent = debits.sumOf { it.amount!!.toDouble() }
        val topSectors = debits.groupBy { it.category }
            .mapValues { (_, txns) -> txns.sumOf { it.amount!!.toDouble() } }
            .entries.sortedByDescending { it.value }
            .take(3)

        val accounts = app.database.accountDao().observeAll().first()
        var weekAgoBalanceTotal = 0.0
        var currentBalanceTotal = 0.0
        var haveFullTrend = accounts.isNotEmpty()
        for (account in accounts) {
            currentBalanceTotal += account.latestBalance?.toDouble() ?: run { haveFullTrend = false; 0.0 }
            val earliest = app.database.transactionDao().getEarliestBalanceSince(account.id, weekAgoMillis)
            if (earliest == null) {
                haveFullTrend = false
            } else {
                weekAgoBalanceTotal += earliest.toDouble()
            }
        }

        postSummary(app.applicationContext, totalSpent, topSectors.map { it.key.displayName to it.value }) { inr ->
            if (haveFullTrend) {
                val delta = currentBalanceTotal - weekAgoBalanceTotal
                val direction = if (delta >= 0) "up" else "down"
                "Balance trend: $direction ${inr.format(kotlin.math.abs(delta))} vs last week."
            } else {
                null
            }
        }

        return Result.success()
    }

    private fun postSummary(
        context: Context,
        totalSpent: Double,
        topSectors: List<Pair<String, Double>>,
        trendLine: (NumberFormat) -> String?,
    ) {
        val inr = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        val sectorsText = if (topSectors.isEmpty()) {
            "No spending this week."
        } else {
            topSectors.joinToString("; ") { (name, amount) -> "$name ${inr.format(amount)}" }
        }
        val trend = trendLine(inr)

        val body = buildString {
            append("Spent ${inr.format(totalSpent)} this week. Top sectors: $sectorsText.")
            if (trend != null) append(" $trend")
        }

        val notification = NotificationCompat.Builder(context, NotificationChannels.WEEKLY_SUMMARY)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("Your weekly spending summary")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) {
            try {
                manager.notify(WEEKLY_SUMMARY_NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
                // Notification permission not granted; nothing else to do here.
            }
        }
    }

    companion object {
        private const val WEEKLY_SUMMARY_NOTIFICATION_ID = 9001
        private const val WORK_NAME = "weekly_summary"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(Duration.ofDays(7))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
