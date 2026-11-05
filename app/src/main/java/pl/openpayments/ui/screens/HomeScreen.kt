package pl.openpayments.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.openpayments.domain.*
import pl.openpayments.ui.MainViewModel
import pl.openpayments.ui.Nav
import pl.openpayments.ui.components.*
import java.time.YearMonth
import kotlin.math.abs

@Composable
fun HomeScreen(vm: MainViewModel, nav: Nav) {
    val month by vm.month.collectAsState()
    val all by vm.transactions.collectAsState()
    val cats by vm.categories.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val subs by vm.subscriptions.collectAsState()
    val catMap = remember(cats) { cats.associateBy { it.id } }
    var fabOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(fabOpen) { fabOpen = false }

    val range = month.range()
    val monthTx = remember(all, month) { all.filter { it.date in range } }
    val expenses = monthTx.filter { !it.isIncome }
    val spent = expenses.sumOf { it.amount }
    val income = monthTx.filter { it.isIncome }.sumOf { it.amount }
    val prevSpent = remember(all, month) {
        val pr = month.minusMonths(1).range()
        val dayCap = if (month == YearMonth.now()) pr.first + (today() - range.first) else pr.last
        all.filter { !it.isIncome && it.date in pr.first..dayCap }.sumOf { it.amount }
    }
    val byCategory = remember(expenses) {
        expenses.groupBy { it.categoryId }.map { (id, l) -> id to l.sumOf { it.amount } }.sortedByDescending { it.second }
    }
    val totalBudget = budgets.firstOrNull { it.categoryId == null }
    val upcoming = subs.filter { it.active && it.nextDate in today()..today() + 7 }

    ExpressiveScaffold(
        title = monthName(month),
        subtitle = "OpenPayments",
        actions = {
            IconButton(onClick = { vm.shiftMonth(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Poprzedni miesiąc") }
            IconButton(onClick = { vm.shiftMonth(1) }, enabled = month < YearMonth.now()) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Następny miesiąc")
            }
            IconButton(onClick = nav::settings) { Icon(Icons.Filled.Settings, "Ustawienia") }
        },
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = fabOpen,
                button = {
                    ToggleFloatingActionButton(
                        checked = fabOpen, onCheckedChange = { fabOpen = it },
                        modifier = Modifier.semantics { contentDescription = if (fabOpen) "Zamknij menu" else "Dodaj" },
                    ) {
                        val icon = if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add
                        Icon(icon, null, with(ToggleFloatingActionButtonDefaults) { Modifier.animateIcon({ checkedProgress }) })
                    }
                },
            ) {
                FloatingActionButtonMenuItem(
                    onClick = { fabOpen = false; nav.scan() },
                    icon = { Icon(Icons.Filled.DocumentScanner, null) }, text = { Text("Skanuj paragon") },
                )
                FloatingActionButtonMenuItem(
                    onClick = { fabOpen = false; nav.edit() },
                    icon = { Icon(Icons.Filled.Edit, null) }, text = { Text("Wpisz wydatek") },
                )
                FloatingActionButtonMenuItem(
                    onClick = { fabOpen = false; nav.edit(income = true) },
                    icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Dodaj przychód") },
                )
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 120.dp),
        ) {
            item {
                HeroCard(spent, income, prevSpent, totalBudget?.limit, totalBudget?.alertPercent ?: 80, month)
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickAction(Icons.Filled.DocumentScanner, "Skanuj", primary = true, Modifier.weight(1f)) { nav.scan() }
                    QuickAction(Icons.Filled.Edit, "Wpisz", primary = false, Modifier.weight(1f)) { nav.edit() }
                    QuickAction(Icons.Filled.Savings, "Budżety", primary = false, Modifier.weight(1f)) { nav.tab("budgets") }
                }
            }
            if (byCategory.isNotEmpty()) item {
                SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("Na co idą pieniądze", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val slices = byCategory.map { (id, v) ->
                            Slice(catMap[id]?.name ?: "Bez kategorii", v, catMap[id]?.let { Color(it.color) } ?: Color.Gray)
                        }
                        Donut(slices, Modifier.size(136.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${expenses.size}", style = MaterialTheme.typography.headlineMedium)
                                Text("transakcji", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.width(20.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                            byCategory.take(5).forEach { (id, v) ->
                                val c = catMap[id]
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(c?.let { Color(it.color) } ?: Color.Gray))
                                    Spacer(Modifier.width(8.dp))
                                    Text(c?.name ?: "Bez kategorii", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                                    Text(formatMoney(v, true), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
            if (upcoming.isNotEmpty()) {
                item { GroupLabel("Nadchodzące płatności", action = "Wszystkie") { nav.tab("subscriptions") } }
                item {
                    SegmentedGroup(upcoming) { s, shapes ->
                        GroupRow(
                            shapes, s.name, formatDay(s.nextDate),
                            leading = { CategoryBadge(catMap[s.categoryId], 40.dp) },
                            trailing = { Text(formatMoney(s.amount), style = MaterialTheme.typography.titleSmall) },
                        )
                    }
                }
            }
            val catBudgets = budgets.filter { it.categoryId != null }
            if (catBudgets.isNotEmpty()) {
                item { GroupLabel("Budżety kategorii", action = "Wszystkie") { nav.tab("budgets") } }
                item {
                    SegmentedGroup(catBudgets.take(3)) { b, shapes ->
                        val s = expenses.filter { it.categoryId == b.categoryId }.sumOf { it.amount }
                        StaticSegmentedItem(
                            shapes,
                            leadingContent = { CategoryBadge(catMap[b.categoryId], 40.dp) },
                            supportingContent = {
                                Column {
                                    Text("${formatMoney(s, true)} z ${formatMoney(b.limit, true)}")
                                    Spacer(Modifier.height(8.dp))
                                    BudgetBar(s, b.limit, b.alertPercent)
                                }
                            },
                        ) { Text(b.name) }
                    }
                }
            }
            item { GroupLabel("Ostatnie transakcje", action = if (monthTx.isNotEmpty()) "Wszystkie" else null) { nav.tab("transactions") } }
            if (monthTx.isEmpty()) item {
                EmptyState(
                    Icons.AutoMirrored.Filled.ReceiptLong, "Pusto w tym miesiącu",
                    "Zeskanuj paragon albo wpisz pierwszy wydatek — kategorię dobierzemy sami.", "Skanuj paragon",
                ) { nav.scan() }
            } else item {
                SegmentedGroup(monthTx.take(6)) { t, shapes -> TransactionItem(t, catMap[t.categoryId], shapes) { nav.edit(t.id) } }
            }
        }
    }
}

@Composable
private fun HeroCard(spent: Long, income: Long, prevSpent: Long, limit: Long?, alert: Int, month: YearMonth) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge, color = scheme.primary, contentColor = scheme.onPrimary,
    ) {
        Column(Modifier.padding(24.dp)) {
            Text("Wydatki", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary.copy(alpha = .8f))
            Text(formatMoney(spent), style = MaterialTheme.typography.displayMedium)
            if (prevSpent > 0) {
                val diff = spent - prevSpent
                val pct = (diff * 100 / prevSpent).toInt()
                Spacer(Modifier.height(6.dp))
                Pill(
                    (if (diff > 0) "▲ $pct%" else "▼ ${-pct}%") + " vs ten sam okres miesiąc temu",
                    container = scheme.onPrimary.copy(alpha = .16f), content = scheme.onPrimary,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("Przychody", formatMoney(income, true), Modifier.weight(1f))
                MiniStat("Bilans", (if (income - spent >= 0) "+" else "−") + formatMoney(abs(income - spent), true), Modifier.weight(1f))
            }
            if (limit != null) {
                Spacer(Modifier.height(20.dp))
                val left = limit - spent
                Row {
                    Text("Budżet miesięczny", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text(
                        if (left >= 0) "zostało ${formatMoney(left, true)}" else "ponad o ${formatMoney(-left, true)}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.height(10.dp))
                BudgetBar(spent, limit, alert, onColor = true)
                if (month == YearMonth.now() && left > 0) {
                    val days = month.lengthOfMonth() - java.time.LocalDate.now().dayOfMonth + 1
                    Spacer(Modifier.height(8.dp))
                    Text("ok. ${formatMoney(left / days, true)} dziennie do końca miesiąca",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onPrimary.copy(alpha = .8f))
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .75f))
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = if (primary) ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) else ButtonDefaults.filledTonalButtonColors()
    Button(
        onClick = onClick, modifier = modifier.height(72.dp), shapes = ButtonDefaults.shapes(),
        colors = colors, contentPadding = PaddingValues(4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}
