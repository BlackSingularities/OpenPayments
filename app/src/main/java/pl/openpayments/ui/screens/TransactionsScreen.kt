package pl.openpayments.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.openpayments.domain.formatDay
import pl.openpayments.domain.formatMoney
import pl.openpayments.ui.MainViewModel
import pl.openpayments.ui.Nav
import pl.openpayments.ui.components.EmptyState
import pl.openpayments.ui.components.TransactionRow

private enum class Kind(val label: String) { ALL("Wszystkie"), OUT("Wydatki"), IN("Przychody") }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("Transakcje", style = MaterialTheme.typography.headlineSmall) }) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Szukaj sprzedawcy lub notatki") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true,
                    shape = MaterialTheme.shapes.large,
                )
            }
            item {
                LazyRow(contentPadding = PaddingValues(16.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Kind.entries) { k -> FilterChip(kind == k, { kind = k }, { Text(k.label) }) }
                    item { VerticalDivider(Modifier.height(32.dp)) }
                    items(cats.filter { kind == Kind.ALL || it.isIncome == (kind == Kind.IN) }) { c ->
                        FilterChip(catFilter == c.id, { catFilter = if (catFilter == c.id) null else c.id }, { Text(c.name) })
                    }
                }
            }
            if (filtered.isEmpty()) item {
                EmptyState(Icons.Filled.SearchOff, "Brak transakcji", "Zmień filtry albo dodaj pierwszą transakcję z ekranu Start.")
            }
            grouped.forEach { (day, list) ->
                stickyHeader(key = "h$day") {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Text(formatDay(day), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val out = list.filter { !it.isIncome }.sumOf { it.amount }
                            if (out > 0) Text("−" + formatMoney(out), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(list, key = { it.id }) { t ->
                    val state = rememberSwipeToDismissBoxState()
                    LaunchedEffect(state.currentValue) {
                        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) vm.deleteTransaction(t)
                    }
                    SwipeToDismissBox(
                        state = state, modifier = Modifier.padding(horizontal = 12.dp).animateItem(),
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) { Icon(Icons.Filled.DeleteOutline, "Usuń", tint = MaterialTheme.colorScheme.onErrorContainer) }
                        },
                    ) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            TransactionRow(t, catMap[t.categoryId]) { nav.edit(t.id) }
                        }
                    }
                }
            }
        }
    }
}
