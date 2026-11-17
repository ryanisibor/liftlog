package app.liftlog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/** One point of a chart: a value and the label for its position along the bottom. */
data class ChartPoint(val label: String, val value: Double)

/**
 * A line across evenly spaced points, with three labelled gridlines and the first and last
 * position labels. The newest point is emphasised. Give it a height.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    formatValue: (Double) -> String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()

    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        if (points.isEmpty()) return@Canvas
        val low = points.minOf { it.value }
        val high = points.maxOf { it.value }
        // Pad the range so the line never touches the edges; a flat line sits in the middle.
        val pad = if (high > low) (high - low) * 0.15 else maxOf(high * 0.1, 1.0)
        val bottomValue = low - pad
        val topValue = high + pad

        val gridValues = listOf(topValue - pad, (low + high) / 2, bottomValue + pad).distinct()
        val gridLabels = gridValues.map { measurer.measure(formatValue(it), labelStyle) }
        val leftGutter = gridLabels.maxOf { it.size.width } + 8.dp.toPx()
        val bottomGutter = measurer.measure("0", labelStyle).size.height + 6.dp.toPx()
        val plotWidth = size.width - leftGutter - 8.dp.toPx()
        val plotHeight = size.height - bottomGutter - 6.dp.toPx()
        val plotTop = 6.dp.toPx()

        fun x(index: Int) = leftGutter + if (points.size == 1) plotWidth / 2 else plotWidth * index / (points.size - 1)
        fun y(value: Double) = plotTop + plotHeight * ((topValue - value) / (topValue - bottomValue)).toFloat()

        gridValues.forEachIndexed { index, value ->
            val lineY = y(value)
            drawLine(gridColor, Offset(leftGutter, lineY), Offset(size.width, lineY), strokeWidth = 1.dp.toPx())
            drawText(gridLabels[index], topLeft = Offset(0f, lineY - gridLabels[index].size.height / 2))
        }

        if (points.size > 1) {
            val path = Path()
            points.forEachIndexed { index, point ->
                if (index == 0) path.moveTo(x(index), y(point.value)) else path.lineTo(x(index), y(point.value))
            }
            drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        points.forEachIndexed { index, point ->
            val isLast = index == points.lastIndex
            drawCircle(lineColor, radius = (if (isLast) 5.5f else 3f).dp.toPx(), center = Offset(x(index), y(point.value)))
        }

        val labelY = size.height - bottomGutter + 6.dp.toPx()
        val first = measurer.measure(points.first().label, labelStyle)
        drawText(first, topLeft = Offset(leftGutter, labelY))
        if (points.size > 1) {
            val last = measurer.measure(points.last().label, labelStyle)
            drawText(last, topLeft = Offset(size.width - last.size.width, labelY))
        }
    }
}

/** Vertical bars with each count above its bar and its label below. The last bar is emphasised. */
@Composable
fun BarChart(
    points: List<ChartPoint>,
    description: String,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val currentColor = MaterialTheme.colorScheme.primary
    val labelStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle: TextStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val measurer = rememberTextMeasurer()

    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        if (points.isEmpty()) return@Canvas
        val highest = maxOf(points.maxOf { it.value }, 1.0)
        val labelHeight = measurer.measure("0", labelStyle).size.height + 6.dp.toPx()
        val valueHeight = measurer.measure("0", valueStyle).size.height + 4.dp.toPx()
        val plotHeight = size.height - labelHeight - valueHeight
        val slot = size.width / points.size
        val barWidth = slot * 0.62f

        points.forEachIndexed { index, point ->
            val centre = slot * index + slot / 2
            // A zero week still gets a sliver, so the row of weeks reads as continuous.
            val barHeight = maxOf(plotHeight * (point.value / highest).toFloat(), 3.dp.toPx())
            val top = valueHeight + plotHeight - barHeight
            drawRoundRect(
                color = if (index == points.lastIndex) currentColor else barColor,
                topLeft = Offset(centre - barWidth / 2, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            if (point.value > 0) {
                val value = measurer.measure(point.value.toInt().toString(), valueStyle)
                drawText(value, topLeft = Offset(centre - value.size.width / 2, top - valueHeight))
            }
            val label = measurer.measure(point.label, labelStyle)
            drawText(label, topLeft = Offset(centre - label.size.width / 2, size.height - labelHeight + 6.dp.toPx()))
        }
    }
}
