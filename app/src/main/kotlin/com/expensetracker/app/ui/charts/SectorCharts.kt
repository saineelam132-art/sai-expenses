package com.expensetracker.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** One labeled, colored value. Callers assign the color from the fixed per-sector map, so a
 * sector looks the same in the donut, the bars and the legend. */
data class ChartSlice(val label: String, val value: Double, val color: Color)

/**
 * Folds everything past [keep] into a single "Other" slice. Charts stay readable and never show
 * more colors at once than the palette validates for, while the underlying per-sector colors
 * stay fixed — the fold is about how many slices are drawn, not about recoloring the survivors.
 */
fun List<ChartSlice>.foldTail(keep: Int, otherColor: Color): List<ChartSlice> {
    if (size <= keep) return this
    val head = take(keep)
    val tail = drop(keep)
    return head + ChartSlice("Other", tail.sumOf { it.value }, otherColor)
}

/**
 * Donut rather than a filled pie: the hole carries the month's total, so the headline figure and
 * its breakdown read as one object. Slices are separated by a surface-colored gap so adjacent
 * segments stay distinguishable even for a viewer who can't tell their hues apart.
 */
@Composable
fun SectorDonutChart(
    slices: List<ChartSlice>,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier,
) {
    val total = slices.sumOf { it.value }
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val gapColor = MaterialTheme.colorScheme.surface
            val emptyRingColor = MaterialTheme.colorScheme.outline
            Canvas(modifier = Modifier.size(200.dp)) {
                val diameter = min(size.width, size.height)
                val stroke = diameter * 0.22f
                val inset = stroke / 2f
                val arcSize = Size(diameter - stroke, diameter - stroke)
                val topLeft = Offset(inset, inset)

                if (total <= 0.0) {
                    // Before any spending the card keeps its shape as a plain ring, rather than
                    // collapsing and shifting everything below it once data arrives.
                    drawArc(
                        color = emptyRingColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                    return@Canvas
                }

                var startAngle = -90f
                slices.forEach { slice ->
                    val sweep = (slice.value / total * 360.0).toFloat()
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                    // 2px surface gap between neighbouring segments.
                    if (slices.size > 1) {
                        drawArc(
                            color = gapColor,
                            startAngle = startAngle,
                            sweepAngle = 1.2f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke),
                        )
                    }
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    centerValue,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        // The legend is not optional: three of the sector hues sit under 3:1 contrast on white,
        // so the figures beside each swatch are what actually carry identity.
        slices.forEach { slice ->
            val pct = if (total > 0) (slice.value / total * 100) else 0.0
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(slice.color))
                Spacer(Modifier.width(8.dp))
                Text(slice.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    "${"%.0f".format(pct)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Horizontal bars, each anchored to a common baseline and rounded at the data end only. */
@Composable
fun SectorBarChart(slices: List<ChartSlice>, valueLabel: (Double) -> String, modifier: Modifier = Modifier) {
    if (slices.isEmpty()) return
    val maxValue = slices.maxOf { it.value }.coerceAtLeast(0.01)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        slices.forEach { slice ->
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(slice.color))
                    Spacer(Modifier.width(8.dp))
                    Text(slice.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        valueLabel(slice.value),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((slice.value / maxValue).toFloat().coerceIn(0.02f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(slice.color),
                    )
                }
            }
        }
    }
}

