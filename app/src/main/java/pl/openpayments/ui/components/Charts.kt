package pl.openpayments.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class Slice(val label: String, val value: Long, val color: Color)

/** Wykres pierścieniowy z sumą w środku. */
@Composable
fun Donut(slices: List<Slice>, modifier: Modifier = Modifier, center: @Composable () -> Unit = {}) {
    val progress = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) { progress.animateTo(1f, tween(800)) }
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.13f
            val inset = stroke / 2
            val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
            val topLeft = Offset((size.width - arcSize.width) / 2, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            var start = -90f
            val gap = if (slices.size > 1) 2.5f else 0f
            slices.forEach { s ->
                val sweep = 360f * s.value / total * progress.value
                if (sweep > gap) drawArc(s.color, start + gap / 2, sweep - gap, false, topLeft, arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        center()
    }
}

/** Wykres słupkowy z podpisami; ostatni (bieżący) słupek wyróżniony. */
@Composable
fun BarChart(values: List<Pair<String, Long>>, modifier: Modifier = Modifier, highlightLast: Boolean = true) {
    val progress = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { progress.animateTo(1f, tween(700)) }
    val max = (values.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val primary = MaterialTheme.colorScheme.primary
    val muted = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.primary, 0.3f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val bw = slot * 0.56f
            for (i in 1..3) {
                val y = size.height * i / 4
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
            }
            values.forEachIndexed { i, (_, v) ->
                val h = size.height * v / max * progress.value
                val c = if (highlightLast && i == values.lastIndex) primary else muted
                drawRoundRect(
                    c, Offset(slot * i + (slot - bw) / 2, size.height - h), Size(bw, h),
                    CornerRadius(bw * 0.25f, bw * 0.25f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            values.forEachIndexed { i, (l, _) ->
                Text(l, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = if (i == values.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (i == values.lastIndex) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

/** Skumulowane wydatki dnia po dniu (bieżący miesiąc vs poprzedni). */
@Composable
fun CumulativeLine(current: List<Long>, previous: List<Long>, daysInMonth: Int, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val prevColor = MaterialTheme.colorScheme.outline
    val max = maxOf(current.lastOrNull() ?: 0, previous.lastOrNull() ?: 0).coerceAtLeast(1)
    Canvas(modifier) {
        fun path(values: List<Long>, color: Color, width: Float) {
            if (values.size < 2) return
            val p = androidx.compose.ui.graphics.Path()
            values.forEachIndexed { i, v ->
                val x = size.width * i / (daysInMonth - 1).coerceAtLeast(1)
                val y = size.height - size.height * v / max
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, color, style = Stroke(width, cap = StrokeCap.Round))
        }
        path(previous, prevColor, 2.dp.toPx())
        path(current, primary, 3.dp.toPx())
    }
}
