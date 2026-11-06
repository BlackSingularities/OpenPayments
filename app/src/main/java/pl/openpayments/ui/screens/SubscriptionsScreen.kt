package pl.openpayments.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.openpayments.data.Category
import pl.openpayments.data.Cycle
import pl.openpayments.data.Subscription
import pl.openpayments.domain.*
import pl.openpayments.ui.MainViewModel
import pl.openpayments.ui.components.*
import java.time.Instant
import java.time.ZoneOffset

private val presets = listOf(
    "Netflix" to 6000L, "Spotify" to 2399L, "YouTube Premium" to 2599L, "Disney+" to 3799L,
    "Max" to 2999L, "iCloud+" to 499L, "Telefon" to 4500L, "Internet" to 6000L, "Siłownia" to 14900L,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(vm: MainViewModel) {
    val subs by vm.subscriptions.collectAsState()
    val cats by vm.categories.collectAsState()
    val catMap = remember(cats) { cats.associateBy { it.id } }
    var editing by remember { mutableStateOf<Subscription?>(null) }
    val subsCat = cats.firstOrNull { it.name == "Subskrypcje" }?.id

    val monthly = subs.filter { it.active }.sumOf {
        when (it.cycle) { Cycle.WEEKLY -> it.amount * 52 / 12; Cycle.MONTHLY -> it.amount; Cycle.YEARLY -> it.amount / 12 }
    }

    TabScaffold(
        info = "Subskrypcje i stałe rachunki",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Subscription(name = "", amount = 0, categoryId = subsCat, nextDate = today()) },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Dodaj") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 120.dp),
        ) {
            item {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Miesięcznie", style = MaterialTheme.typography.labelLarge)
                            Text(formatMoney(monthly), style = MaterialTheme.typography.displaySmall)
                            Text("Rocznie ok. ${formatMoney(monthly * 12, true)}", style = MaterialTheme.typography.bodyMedium)
                        }
                        val shape = MaterialShapes.SoftBurst.toShape()
                        Box(
                            Modifier.size(64.dp).clip(shape).background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Autorenew, null, Modifier.size(32.dp)) }
                    }
                }
            }
            item { GroupLabel("Szybkie dodawanie") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    items(presets) { (n, a) ->
                        SuggestionChip(
                            onClick = { editing = Subscription(name = n, amount = a, categoryId = subsCat, nextDate = today()) },
                            label = { Text(n) }, shape = CircleShape,
                        )
                    }
                }
            }
            if (subs.isEmpty()) item {
                EmptyState(Icons.Filled.Autorenew, "Brak płatności cyklicznych",
                    "Dodaj Netflixa, Spotify czy czynsz raz — OpenPayments sam zaksięguje je w dniu płatności i przypomni wcześniej.")
            } else {
                item { GroupLabel("Twoje płatności") }
                item {
                    SegmentedGroup(subs) { s, shapes ->
                        GroupRow(
                            shapes, s.name,
                            if (s.active) "${s.cycle.label} · następna ${formatShortDay(s.nextDate)}" else "wstrzymana",
                            leading = { CategoryBadge(catMap[s.categoryId], 40.dp) },
                            trailing = {
                                Text(formatMoney(s.amount), style = MaterialTheme.typography.titleSmall,
                                    color = if (s.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
                            },
                        ) { editing = s }
                    }
                }
            }
        }
    }

    editing?.let { s ->
        SubscriptionSheet(s, cats.filter { !it.isIncome }, { editing = null },
            { vm.saveSubscription(it); editing = null },
            if (s.id != 0L) ({ vm.deleteSubscription(s); editing = null }) else null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionSheet(
    s: Subscription, cats: List<Category>, onDismiss: () -> Unit, onSave: (Subscription) -> Unit, onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(s.name) }
    var amount by remember { mutableStateOf(moneyInput(s.amount)) }
    var cycle by remember { mutableStateOf(s.cycle) }
    var cat by remember { mutableStateOf(s.categoryId) }
    var next by remember { mutableLongStateOf(s.nextDate) }
    var autoBook by remember { mutableStateOf(s.autoBook) }
    var active by remember { mutableStateOf(s.active) }
    var pickDate by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (s.id == 0L) "Nowa płatność cykliczna" else "Edytuj płatność", style = MaterialTheme.typography.headlineSmall)
            TextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nazwa") }, singleLine = true, shape = MaterialTheme.shapes.large, colors = filledFieldColors())
            TextField(amount, { v -> amount = v.filter { it.isDigit() || it == ',' || it == '.' } }, Modifier.fillMaxWidth(),
                label = { Text("Kwota") }, suffix = { Text("zł") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.large, colors = filledFieldColors())
            ConnectedToggleGroup(Cycle.entries.map { it to it.label }, cycle) { cycle = it }
            FilledTonalButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth().height(56.dp), shapes = ButtonDefaults.shapes()) { Icon(Icons.Filled.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("Następna płatność: ${formatDay(next)}") }
            Text("Kategoria", style = MaterialTheme.typography.titleSmall)
            CategoryPicker(cats, cat) { cat = it }
            ListItem(colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent), headlineContent = { Text("Księguj automatycznie") },
                supportingContent = { Text("Dodaj wydatek w dniu płatności") },
                trailingContent = { Switch(autoBook, { autoBook = it }) })
            if (s.id != 0L) ListItem(colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent), headlineContent = { Text("Aktywna") }, trailingContent = { Switch(active, { active = it }) })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onDelete != null) FilledTonalButton(
                    onClick = onDelete, modifier = Modifier.weight(1f).height(56.dp), shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text("Usuń") }
                Button(
                    onClick = {
                        onSave(s.copy(name = name.trim(), amount = parseMoney(amount) ?: 0, cycle = cycle, categoryId = cat,
                            nextDate = next, autoBook = autoBook, active = active))
                    },
                    modifier = Modifier.weight(1f).height(56.dp), enabled = name.isNotBlank() && (parseMoney(amount) ?: 0) > 0,
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Zapisz") }
            }
        }
    }
    if (pickDate) {
        val st = rememberDatePickerState(next.toDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { pickDate = false }, confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let { next = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                pickDate = false
            }) { Text("OK") }
        }) { DatePicker(st) }
    }
}
