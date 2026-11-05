package pl.openpayments.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.openpayments.domain.*
import pl.openpayments.ui.MainViewModel
import pl.openpayments.ui.components.*
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: MainViewModel) {
    val month by vm.month.collectAsState()
    val all by vm.transactions.collectAsState()
    val cats by vm.categories.collectAsState()
    val catMap = remember(cats) { cats.associateBy { it.id } }
    val expenses = remember(all) { all.filter { !it.isIncome } }
    val products by vm.products.collectAsState()

    val sixMonths = remember(expenses, month) {
        (5 downTo 0).map { d ->
            val m = month.minusMonths(d.toLong()); val r = m.range()
            monthShort(m) to expenses.filter { it.date in r }.sumOf { it.amount }
        }
    }
    val r = month.range()
    val monthExp = expenses.filter { it.date in r }
    val total = monthExp.sumOf { it.amount }
    val byCat = monthExp.groupBy { it.categoryId }.map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second }
    val prevR = month.minusMonths(1).range()
    val prevByCat = expenses.filter { it.date in prevR }.groupBy { it.categoryId }.mapValues { (_, v) -> v.sumOf { it.amount } }
    val topMerchants = monthExp.groupBy { it.merchant.trim().lowercase() }
        .map { (_, v) -> v.first().merchant to (v.size to v.sumOf { it.amount }) }
        .sortedByDescending { it.second.second }.take(5)
    val daysElapsed = if (month == YearMonth.now()) LocalDate.now().dayOfMonth else month.lengthOfMonth()
    val avg6 = sixMonths.dropLast(1).map { it.second }.filter { it > 0 }.let { if (it.isEmpty()) 0 else it.sum() / it.size }

    fun cumulative(m: YearMonth, upTo: Int): List<Long> {
        val rr = m.range(); val list = expenses.filter { it.date in rr }
        var acc = 0L
        return (1..upTo).map { day -> acc += list.filter { it.date.toDate().dayOfMonth == day }.sumOf { it.amount }; acc }
    }

    ExpressiveScaffold(
        title = "Statystyki",
        subtitle = monthName(month),
        actions = {
            IconButton(onClick = { vm.shiftMonth(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Poprzedni miesiąc") }
            IconButton(onClick = { vm.shiftMonth(1) }, enabled = month < YearMonth.now()) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Następny miesiąc")
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (expenses.isEmpty()) {
                item { EmptyState(Icons.Filled.Insights, "Jeszcze nic tu nie ma", "Statystyki pojawią się po dodaniu pierwszych wydatków.") }
                return@LazyColumn
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Wydano", formatMoney(total, true), Modifier.weight(1f))
                    StatTile("Średnio dziennie", formatMoney(total / daysElapsed.coerceAtLeast(1), true), Modifier.weight(1f))
                    StatTile("Śr. 5 mies.", formatMoney(avg6, true), Modifier.weight(1f))
                }
            }
            item {
                SectionCard {
                    Text("Ostatnie 6 miesięcy", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    BarChart(sixMonths, Modifier.fillMaxWidth().height(170.dp))
                }
            }
            item {
                SectionCard {
                    Text("Tempo wydatków", style = MaterialTheme.typography.titleMedium)
                    Text("Skumulowane wydatki dzień po dniu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    val prevMonth = month.minusMonths(1)
                    CumulativeLine(
                        cumulative(month, daysElapsed), cumulative(prevMonth, prevMonth.lengthOfMonth()),
                        maxOf(month.lengthOfMonth(), prevMonth.lengthOfMonth()), Modifier.fillMaxWidth().height(140.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Legend(MaterialTheme.colorScheme.primary, monthName(month, false))
                        Spacer(Modifier.width(16.dp))
                        Legend(MaterialTheme.colorScheme.outline, monthName(month.minusMonths(1), false))
                    }
                }
            }
            if (byCat.isNotEmpty()) item {
                SectionCard {
                    Text("Kategorie", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    byCat.forEach { (id, v) ->
                        val c = catMap[id]
                        val prev = prevByCat[id] ?: 0
                        Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            CategoryBadge(c, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(c?.name ?: "Bez kategorii", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(formatMoney(v), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(5.dp))
                                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth(v.toFloat() / total.coerceAtLeast(1))
                                        .clip(CircleShape).background(c?.let { Color(it.color) } ?: Color.Gray))
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "${v * 100 / total.coerceAtLeast(1)}% wydatków" + if (prev > 0) {
                                        val d = (v - prev) * 100 / prev
                                        " · ${if (d >= 0) "+" else ""}$d% m/m"
                                    } else "",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            if (products.isNotEmpty()) item {
                SectionCard {
                    Text("Najdroższe produkty z paragonów", style = MaterialTheme.typography.titleMedium)
                    Text("Łącznie w tym miesiącu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    val maxP = products.maxOf { it.total }.coerceAtLeast(1)
                    products.forEach { p ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Row {
                                Text(p.name.replaceFirstChar { it.titlecase(PL) }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text("${p.count}× · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatMoney(p.total), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth(p.total.toFloat() / maxP).height(5.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)))
                        }
                    }
                }
            }
            if (topMerchants.isNotEmpty()) item {
                SectionCard {
                    Text("Gdzie najczęściej płacisz", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    topMerchants.forEachIndexed { i, (name, p) ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(28.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("${p.first} ${if (p.first == 1) "wizyta" else if (p.first in 2..4) "wizyty" else "wizyt"}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatMoney(p.second), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
    Spacer(Modifier.width(6.dp))
    Text(label, style = MaterialTheme.typography.labelMedium)
}
