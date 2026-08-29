package com.expensetracker.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.expensetracker.core.model.Category
import java.math.BigDecimal

/** One budget per sector per month is not tracked separately — a budget is a standing monthly limit. */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: Category,
    val monthlyLimit: BigDecimal,
    /** Last threshold (80/100) we've already notified about this month, to avoid repeat alerts. */
    val lastAlertPercentForMonth: Int = 0,
    val lastAlertMonthKey: String = "",
)
