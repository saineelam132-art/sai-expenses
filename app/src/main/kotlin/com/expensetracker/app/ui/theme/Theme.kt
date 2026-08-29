package com.expensetracker.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Green40 = Color(0xFF1B5E20)
val Green80 = Color(0xFFA5D6A7)
val Red40 = Color(0xFFB3261E)

private val LightColors = lightColorScheme(primary = Green40, secondary = Green80, error = Red40)
private val DarkColors = darkColorScheme(primary = Green80, secondary = Green40, error = Color(0xFFF2B8B5))

/** A small fixed palette used by the sector bar/pie charts so a category keeps the same color everywhere. */
val SectorChartColors = listOf(
    Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF558B2F), Color(0xFF9E9D24),
    Color(0xFFF9A825), Color(0xFFEF6C00), Color(0xFFD84315), Color(0xFFAD1457),
    Color(0xFF6A1B9A), Color(0xFF4527A0), Color(0xFF283593), Color(0xFF00695C),
)

@Composable
fun ExpenseTrackerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
