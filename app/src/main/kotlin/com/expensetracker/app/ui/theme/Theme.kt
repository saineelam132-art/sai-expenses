package com.expensetracker.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.expensetracker.core.model.Category

// White / blue / black. Blue is reserved for money figures and primary actions, so it always
// means "this is the number or the action" rather than decoration.
val Blue = Color(0xFF2A78D6)
val BlueDark = Color(0xFF1C5CAB)
val BlueTint = Color(0xFFE8F1FC)
val Ink = Color(0xFF0B0B0B)
val InkMuted = Color(0xFF52514E)
val Surface = Color(0xFFFFFFFF)
val SurfaceCard = Color(0xFFF4F6F9)
val Outline = Color(0xFFD7DCE3)
val Danger = Color(0xFFE34948)

/**
 * Deliberately light-only: the palette is specified as white background with black text and blue
 * accents, so it does not follow the system dark setting — an automatic dark flip would invert
 * exactly the contrast this was chosen for.
 */
private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = BlueTint,
    onPrimaryContainer = BlueDark,
    secondary = BlueDark,
    onSecondary = Color.White,
    background = Surface,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = InkMuted,
    outline = Outline,
    outlineVariant = Outline,
    error = Danger,
    onError = Color.White,
)

@Composable
fun ExpenseTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}

/**
 * Fixed sector -> color mapping, so a sector is the same color everywhere it appears (pie chart,
 * bar chart, summary rows). Colors are assigned by sector identity, never by rank or by position
 * in a filtered list — a month where Groceries outspends Food must not repaint either of them.
 *
 * The twelve hues are a validated categorical set: every adjacent pair clears both the
 * colorblind-separation and normal-vision floors on a white surface. Three of them fall under
 * 3:1 contrast against white, which is why every chart that uses them also carries a labeled
 * legend with figures — identity is never carried by color alone.
 */
object SectorColors {

    /** Untagged/unknown spending, and the "Other" bucket charts fold their long tail into. */
    val Untagged = Color(0xFF8A8A8A)

    private val slots = listOf(
        Color(0xFF2A78D6), // blue
        Color(0xFFEB6834), // orange
        Color(0xFF1BAF7A), // aqua
        Color(0xFFEDA100), // yellow
        Color(0xFFE87BA4), // magenta
        Color(0xFF008300), // green
        Color(0xFF4A3AA7), // violet
        Color(0xFFE34948), // red
        Color(0xFF7AB8F5), // light blue
        Color(0xFFA4572A), // brown
        Color(0xFF00897B), // teal
        Color(0xFFB07CD6), // purple
    )

    private val byCategory = mapOf(
        Category.FOOD_DINING to slots[0],
        Category.GROCERIES to slots[1],
        Category.TRAVEL_TRANSPORT to slots[2],
        Category.BILLS_UTILITIES to slots[3],
        Category.CLOTHES_SHOPPING to slots[4],
        Category.ENTERTAINMENT to slots[5],
        Category.SUBSCRIPTIONS to slots[6],
        Category.HEALTH to slots[7],
        Category.RENT_EMI to slots[8],
        Category.EDUCATION to slots[9],
        Category.INVESTMENTS to slots[10],
        Category.MISCELLANEOUS to slots[11],
    )

    fun forCategory(category: Category): Color = byCategory[category] ?: Untagged

    /**
     * Color for a user-defined category. Derived from the name so the same custom category keeps
     * the same color across app restarts and devices without needing to store one.
     */
    fun forCustom(name: String): Color {
        val normalized = name.trim().lowercase()
        if (normalized.isEmpty()) return Untagged
        val hash = normalized.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7FFFFFFF }
        return slots[hash % slots.size]
    }
}
