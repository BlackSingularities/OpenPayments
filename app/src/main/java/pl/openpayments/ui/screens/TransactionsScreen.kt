package pl.openpayments.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import pl.openpayments.domain.formatDay
import pl.openpayments.domain.formatMoney
import pl.openpayments.ui.MainViewModel
import pl.openpayments.ui.Nav
import pl.openpayments.ui.components.EmptyState
import pl.openpayments.ui.components.TabScaffold
import pl.openpayments.ui.components.TransactionItem

private enum class Kind(val label: String) { ALL("Wszystkie"), OUT("Wydatki"), IN("Przychody") }

@Composable
fun TransactionsScreen(vm: MainViewModel, nav: Nav) {
    val all by vm.transactions.collectAsState()
    val cats by vm.categories.collectAsState()
    val catMap = remember(cats) { cats.associateBy { it.id } }
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(Kind.ALL) }
    var catFilter by rememberSaveable { mutableStateOf<Long?>(null) }

    val filtered = remember(all, query, kind, catFilter) {
        all.filter { t ->
            (query.isBlank() || t.merchant.contains(query, true) || t.note.contains(query, true)) &&
                (kind == Kind.ALL || (kind == Kind.IN) == t.isIncome) &&
                (catFilter == null || t.categoryId == catFilter)
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.date } }
    val totalOut = filtered.filter { !it.isIncome }.sumOf { it.amount }

    TabScaffold(
        info = if (filtered.isEmpty()) "Brak pozycji" else "${filtered.size} pozycji · wydatki ${formatMoney(totalOut, true)}",
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 32.dp)) {
            item {
                TextField(
                    query, { query = it },
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp),
                    placeholder = { Text("Szukaj sprzedawcy lub notatki") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Wyczyść") } },
                    singleLine = true, shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
            }
            item {
                LazyRow(contentPadding = PaddingValues(16.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Kind.entries) { k -> FilterChip(kind == k, { kind = k }, { Text(k.label) }, shape = CircleShape) }
                    item { VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp)) }
                    items(cats.filter { kind == Kind.ALL || it.isIncome == (kind == Kind.IN) }) { c ->
                        FilterChip(catFilter == c.id, { catFilter = if (catFilter == c.id) null else c.id }, { Text(c.name) }, shape = CircleShape)
                    }
                }
            }
            if (filtered.isEmpty()) item {
                EmptyState(Icons.Filled.SearchOff, "Brak transakcji", "Zmień filtry albo dodaj pierwszą transakcję z ekranu Start.")
            }
            grouped.forEach { (day, list) ->
                stickyHeader(key = "h$day") {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                            Text(formatDay(day), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            val out = list.filter { !it.isIncome }.sumOf { it.amount }
                            if (out > 0) Text("−" + formatMoney(out), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                    val state = rememberSwipeToDismissBoxState()
                    LaunchedEffect(state.currentValue) {
                        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) vm.deleteTransaction(t)
                    }
                    val shapes = ListItemDefaults.segmentedShapes(i, list.size)
                    SwipeToDismissBox(
                        state = state,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp).animateItem(),
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                Modifier.fillMaxSize().clip(shapes.shape).background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) { Icon(Icons.Filled.DeleteOutline, "Usuń", tint = MaterialTheme.colorScheme.onErrorContainer) }
                        },
                    ) { TransactionItem(t, catMap[t.categoryId], shapes) { nav.edit(t.id) } }
                }
            }
        }
    }
}
