package pl.openpayments.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.openpayments.data.Budget
import pl.openpayments.domain.*
import pl.openpayments.ui.MainViewModel
import pl.openpayments.ui.components.*
import pl.openpayments.ui.theme.Danger
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(vm: MainViewModel) {
    val budgets by vm.budgets.collectAsState()
    val cats by vm.categories.collectAsState()
    val all by vm.transactions.collectAsState()
    val catMap = remember(cats) { cats.associateBy { it.id } }
    var editing by remember { mutableStateOf<Budget?>(null) }

    val month = YearMonth.now()
    val range = month.range()
    val expenses = remember(all) { all.filter { !it.isIncome && it.date in range } }
    val daysLeft = month.lengthOfMonth() - LocalDate.now().dayOfMonth + 1

    Scaffold(
        topBar = { TopAppBar(title = { Text("Budżety", style = MaterialTheme.typography.headlineSmall) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Budget(name = "", limit = 0, categoryId = null) },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Nowy budżet") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("${monthName(month)} · zostało $daysLeft dni", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (budgets.isEmpty()) item {
                EmptyState(Icons.Filled.Savings, "Ustaw pierwszy budżet",
                    "Wpisz kwotę na miesiąc — całościowo albo dla wybranej kategorii. Ostrzeżemy Cię, zanim przekroczysz limit.",
                    "Utwórz budżet") { editing = Budget(name = "Miesięczny", limit = 0, categoryId = null) }
            }
            items(budgets, key = { it.id }) { b ->
                val spent = expenses.filter { b.categoryId == null || it.categoryId == b.categoryId }.sumOf { it.amount }
                val left = b.limit - spent
                Surface(onClick = { editing = b }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryBadge(catMap[b.categoryId] ?: if (b.categoryId == null) null else null, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(b.name, style = MaterialTheme.typography.titleMedium)
                                Text(catMap[b.categoryId]?.name ?: "Wszystkie wydatki", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val pct = if (b.limit > 0) (spent * 100 / b.limit).toInt() else 0
                            Pill("$pct%",
                                color = if (pct >= 100) Danger.copy(alpha = .15f) else MaterialTheme.colorScheme.secondaryContainer,
                                textColor = if (pct >= 100) Danger else MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(Modifier.height(14.dp))
                        BudgetBar(spent, b.limit, b.alertPercent)
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Text("Wydano ${formatMoney(spent)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text("z ${formatMoney(b.limit)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (left >= 0) "Możesz wydawać ok. ${formatMoney(left / daysLeft.coerceAtLeast(1), true)} dziennie"
                            else "Przekroczono o ${formatMoney(-left)}",
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                            color = if (left >= 0) MaterialTheme.colorScheme.primary else Danger,
                        )
                    }
                }
            }
        }
    }

    editing?.let { b ->
        BudgetSheet(b, cats.filter { !it.isIncome }, onDismiss = { editing = null },
            onSave = { vm.saveBudget(it); editing = null },
            onDelete = if (b.id != 0L) ({ vm.deleteBudget(b); editing = null }) else null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetSheet(
    b: Budget, cats: List<pl.openpayments.data.Category>, onDismiss: () -> Unit,
    onSave: (Budget) -> Unit, onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(b.name) }
    var limit by remember { mutableStateOf(moneyInput(b.limit)) }
    var cat by remember { mutableStateOf(b.categoryId) }
    var alert by remember { mutableFloatStateOf(b.alertPercent.toFloat()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (b.id == 0L) "Nowy budżet" else "Edytuj budżet", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nazwa") }, singleLine = true)
            OutlinedTextField(limit, { v -> limit = v.filter { it.isDigit() || it == ',' || it == '.' } }, Modifier.fillMaxWidth(),
                label = { Text("Limit miesięczny") }, suffix = { Text("zł") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Text("Zakres", style = MaterialTheme.typography.titleSmall)
            FilterChip(cat == null, { cat = null }, { Text("Wszystkie wydatki") })
            CategoryPicker(cats, cat) { cat = it; if (name.isBlank()) name = cats.first { c -> c.id == it }.name }
            Text("Ostrzeż przy ${alert.toInt()}% wykorzystania", style = MaterialTheme.typography.titleSmall)
            Slider(alert, { alert = it }, valueRange = 50f..95f, steps = 8)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onDelete != null) OutlinedButton(onClick = onDelete, Modifier.weight(1f)) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        val l = parseMoney(limit) ?: 0
                        if (l > 0) onSave(b.copy(name = name.ifBlank { "Budżet" }, limit = l, categoryId = cat, alertPercent = alert.toInt()))
                    },
                    Modifier.weight(1f), enabled = (parseMoney(limit) ?: 0) > 0,
                ) { Text("Zapisz") }
            }
        }
    }
}
