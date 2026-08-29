package com.expensetracker.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** One labeled, colored value — used by both charts below. Callers pre-assign colors so a given
 * category (e.g. Food & Dining) keeps the same color across the bar chart, pie chart, and legend. */
data class ChartSlice(val label: String, val value: Double, val color: Color)

/**
 * Minimal hand-rolled charts (Canvas-based, no external charting library) so the dashboard has
 * zero extra dependencies to keep in sync with Compose/AGP versions.
 */
@Composable
fun SectorBarChart(slices: List<ChartSlice>, modifier: Modifier = Modifier) {
    if (slices.isEmpty()) {
        Text("No spending yet", style = MaterialTheme.typography.bodyMedium, modifier = modifier)
        return
    }
    val maxValue = slices.maxOf { it.value }.coerceAtLeast(1.0)
    Column(modifier = modifier.fillMaxWidth()) {
        slices.forEach { slice ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    slice.label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(20.dp)) {
                Canvas(modifier = Modifier.fillMaxWidth().height(20.dp)) {
                    val widthFraction = (slice.value / maxValue).toFloat().coerceIn(0f, 1f)
                    drawRect(color = slice.color, size = Size(size.width * widthFraction, size.height))
                }
            }
        }
    }
}

@Composable
fun SectorPieChart(slices: List<ChartSlice>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.value }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(140.dp).aspectRatio(1f)) {
            if (total <= 0.0) return@Canvas
            val diameter = min(size.width, size.height)
            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = (slice.value / total * 360.0).toFloat()
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                    size = Size(diameter, diameter),
                )
                startAngle += sweep
            }
        }
        Column(
            modifier = Modifier.padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            slices.forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).padding(end = 4.dp)) {
                        Canvas(modifier = Modifier.size(10.dp)) { drawRect(color = slice.color) }
                    }
                    Text(
                        "${slice.label} (${if (total > 0) (slice.value / total * 100).toInt() else 0}%)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}
