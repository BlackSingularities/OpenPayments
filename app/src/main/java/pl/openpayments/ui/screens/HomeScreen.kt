package pl.openpayments.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.openpayments.domain.*
import pl.openpayments.ui.MainViewModel
import pl.openpayments.ui.Nav
import pl.openpayments.ui.components.*
import pl.openpayments.ui.theme.Danger
import pl.openpayments.ui.theme.Positive
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, nav: Nav) {
    val month by vm.month.collectAsState()
    val all by vm.transactions.collectAsState()
    val cats by vm.categories.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val subs by vm.subscriptions.collectAsState()
    val catMap = remember(cats) { cats.associateBy { it.id } }
    var fabOpen by remember { mutableStateOf(false) }

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
    val byCategory = remember(expenses, catMap) {
        expenses.groupBy { it.categoryId }.map { (id, l) -> id to l.sumOf { it.amount } }.sortedByDescending { it.second }
    }
    val totalBudget = budgets.firstOrNull { it.categoryId == null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("OpenPayments", style = MaterialTheme.typography.headlineSmall) },
                actions = { IconButton(onClick = nav::settings) { Icon(Icons.Filled.Settings, "Ustawienia") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedVisibility(fabOpen, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExtendedFloatingActionButton(
                            onClick = { fabOpen = false; nav.edit(income = true) },
                            icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Przychód") },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        )
                        ExtendedFloatingActionButton(
                            onClick = { fabOpen = false; nav.edit() },
                            icon = { Icon(Icons.Filled.Edit, null) }, text = { Text("Wpisz wydatek") },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        )
                        ExtendedFloatingActionButton(
                            onClick = { fabOpen = false; nav.scan() },
                            icon = { Icon(Icons.Filled.DocumentScanner, null) }, text = { Text("Skanuj paragon") },
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        )
                    }
                }
                FloatingActionButton(onClick = { fabOpen = !fabOpen }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(if (fabOpen) Icons.Filled.Close else Icons.Filled.Add, "Dodaj")
                }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { MonthSwitcher(month, vm::shiftMonth) }
            item {
                // Karta główna: wydatki miesiąca
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primary) {
                    Column(Modifier.fillMaxWidth().padding(22.dp)) {
                        Text("Wydatki · ${monthName(month)}", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f),
                            style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(formatMoney(spent), style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onPrimary)
                        if (prevSpent > 0) {
                            val diff = spent - prevSpent
                            val pct = (diff * 100 / prevSpent).toInt()
                            Text(
                                (if (diff > 0) "▲ $pct% " else "▼ ${-pct}% ") + "względem tego samego okresu poprzedniego miesiąca",
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .85f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MiniStat("Przychody", formatMoney(income, true), Modifier.weight(1f))
                            MiniStat("Bilans", (if (income - spent >= 0) "+" else "−") + formatMoney(kotlin.math.abs(income - spent), true), Modifier.weight(1f))
                        }
                        if (totalBudget != null) {
                            Spacer(Modifier.height(16.dp))
                            val left = totalBudget.limit - spent
                            Row {
                                Text("Budżet miesięczny", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                Text(if (left >= 0) "zostało ${formatMoney(left, true)}" else "ponad o ${formatMoney(-left, true)}",
                                    color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                            }
                            Spacer(Modifier.height(8.dp))
                            BudgetBar(spent, totalBudget.limit, totalBudget.alertPercent)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(Icons.Filled.DocumentScanner, "Skanuj", Modifier.weight(1f)) { nav.scan() }
                    QuickAction(Icons.Filled.Edit, "Wpisz", Modifier.weight(1f)) { nav.edit() }
                    QuickAction(Icons.Filled.Savings, "Budżety", Modifier.weight(1f)) { nav.tab("budgets") }
                }
            }
            if (byCategory.isNotEmpty()) item {
                SectionCard {
                    Text("Na co idą pieniądze", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val slices = byCategory.map { (id, v) ->
                            Slice(catMap[id]?.name ?: "Bez kategorii", v, catMap[id]?.let { Color(it.color) } ?: Color.Gray)
                        }
                        Donut(slices, Modifier.size(132.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${expenses.size}", style = MaterialTheme.typography.headlineSmall)
                                Text("transakcji", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            byCategory.take(5).forEach { (id, v) ->
                                val c = catMap[id]
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(c?.let { Color(it.color) } ?: Color.Gray))
                                    Spacer(Modifier.width(8.dp))
                                    Text(c?.name ?: "Bez kategorii", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                                    Text(formatMoney(v, true), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
            val upcoming = subs.filter { it.active && it.nextDate in today()..today() + 7 }
            if (upcoming.isNotEmpty()) item {
                SectionCard {
                    Text("Nadchodzące płatności", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    upcoming.forEach { s ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            CategoryBadge(catMap[s.categoryId], 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(formatDay(s.nextDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatMoney(s.amount), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            val catBudgets = budgets.filter { it.categoryId != null }
            if (catBudgets.isNotEmpty()) item {
                SectionCard {
                    SectionHeader("Budżety kategorii", "Wszystkie") { nav.tab("budgets") }
                    catBudgets.take(3).forEach { b ->
                        val s = expenses.filter { it.categoryId == b.categoryId }.sumOf { it.amount }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(b.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("${formatMoney(s, true)} / ${formatMoney(b.limit, true)}", style = MaterialTheme.typography.bodySmall,
                                color = if (s > b.limit) Danger else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(6.dp))
                        BudgetBar(s, b.limit, b.alertPercent, 8.dp)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
            item {
                SectionCard {
                    SectionHeader("Ostatnie transakcje", if (monthTx.isNotEmpty()) "Wszystkie" else null) { nav.tab("transactions") }
                    if (monthTx.isEmpty()) {
                        EmptyState(Icons.AutoMirrored.Filled.ReceiptLong, "Pusto w tym miesiącu",
                            "Zeskanuj paragon albo wpisz pierwszy wydatek — kategorię dobierzemy sami.",
                            "Skanuj paragon") { nav.scan() }
                    } else monthTx.take(6).forEach { t ->
                        TransactionRow(t, catMap[t.categoryId]) { nav.edit(t.id) }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthSwitcher(month: YearMonth, shift: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { shift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Poprzedni miesiąc") }
        Text(monthName(month), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { shift(1) }, enabled = month < YearMonth.now()) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Następny miesiąc")
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(Color.White.copy(alpha = 0.13f)).padding(12.dp)
    ) {
        Text(label, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .75f), style = MaterialTheme.typography.labelMedium)
        Text(value, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Suppress("unused") private val keepPositive = Positive
