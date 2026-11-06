package pl.openpayments.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import pl.openpayments.data.ReceiptItem
import pl.openpayments.data.Transaction
import pl.openpayments.data.TxSource
import pl.openpayments.domain.*
import pl.openpayments.ui.MainViewModel
import pl.openpayments.ui.Nav
import pl.openpayments.ui.components.CategoryPicker
import pl.openpayments.ui.components.ConnectedToggleGroup
import pl.openpayments.ui.components.filledFieldColors
import pl.openpayments.ui.components.SectionCard
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

private class ItemRow(name: String, price: String, val qty: Double = 1.0, val raw: String? = null) {
    var name by mutableStateOf(name)
    var price by mutableStateOf(price)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionScreen(vm: MainViewModel, nav: Nav, id: Long, fromReceipt: Boolean, startIncome: Boolean) {
    val cats by vm.categories.collectAsState()
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf(id == 0L && !fromReceipt) }
    var original by remember { mutableStateOf<Transaction?>(null) }
    var isIncome by rememberSaveable { mutableStateOf(startIncome) }
    var amount by rememberSaveable { mutableStateOf("") }
    var merchant by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableLongStateOf(today()) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var userPickedCategory by rememberSaveable { mutableStateOf(false) }
    var image by rememberSaveable { mutableStateOf<String?>(null) }
    val items = remember { mutableStateListOf<ItemRow>() }
    var receiptConsistent by remember { mutableStateOf(true) }
    var showDate by remember { mutableStateOf(false) }
    var showImage by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        if (id != 0L) {
            val t = vm.repo.transaction(id) ?: return@LaunchedEffect nav.back().let { }
            original = t
            isIncome = t.isIncome; amount = moneyInput(t.amount); merchant = t.merchant; note = t.note
            date = t.date; categoryId = t.categoryId; userPickedCategory = true; image = t.receiptImage
            items.clear(); items += vm.repo.items(id).map { ItemRow(it.name, moneyInput(it.price), it.quantity, it.rawName) }
        } else if (fromReceipt) {
            vm.draft?.let { d ->
                merchant = d.merchant; d.date?.let { date = it }; d.total?.let { amount = moneyInput(it) }
                image = d.imagePath; receiptConsistent = d.consistent
                items.clear(); items += d.items.map { ItemRow(it.name, moneyInput(it.price), it.quantity, it.rawName) }
                categoryId = vm.repo.suggestCategory(d.merchant)
            }
        }
        loaded = true
    }

    // Automatyczna kategoryzacja w trakcie wpisywania sprzedawcy
    LaunchedEffect(merchant) {
        if (loaded && !userPickedCategory && merchant.length >= 3) {
            vm.repo.suggestCategory(merchant)?.let { categoryId = it }
        }
    }

    val visibleCats = cats.filter { it.isIncome == isIncome }
    val itemsSum = items.sumOf { parseMoney(it.price) ?: 0L }

    fun save() {
        val a = parseMoney(amount)
        when {
            a == null || a == 0L -> { error = "Podaj kwotę"; return }
            merchant.isBlank() -> { error = if (isIncome) "Podaj źródło przychodu" else "Podaj sprzedawcę"; return }
        }
        val t = Transaction(
            id = original?.id ?: 0, amount = a!!, isIncome = isIncome, merchant = merchant.trim(), note = note.trim(),
            categoryId = categoryId, date = date,
            source = original?.source ?: if (fromReceipt) TxSource.RECEIPT else TxSource.MANUAL,
            receiptImage = image, subscriptionId = original?.subscriptionId,
        )
        val ri = if (isIncome) emptyList() else items.filter { it.name.isNotBlank() }.map {
            ReceiptItem(name = it.name.trim(), quantity = it.qty, price = parseMoney(it.price) ?: 0, rawName = it.raw)
        }
        vm.saveTransaction(t, ri) {
            vm.draft = null
            vm.snack(if (original == null) "Dodano: ${t.merchant} · ${formatMoney(t.amount)}" else "Zapisano zmiany")
        }
        nav.back()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when { original != null -> "Edycja"; fromReceipt -> "Sprawdź paragon"; isIncome -> "Nowy przychód"; else -> "Nowy wydatek" }) },
                navigationIcon = {
                    FilledTonalIconButton(onClick = { nav.back() }, Modifier.padding(start = 4.dp)) { Icon(Icons.Filled.Close, "Zamknij") }
                },
                actions = {
                    if (original != null) FilledTonalIconButton(
                        onClick = { confirmDelete = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) { Icon(Icons.Filled.DeleteOutline, "Usuń") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = ::save, modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                        .padding(16.dp).height(64.dp),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Filled.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (fromReceipt) "Zatwierdź transakcję" else "Zapisz", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (original == null && !fromReceipt) {
                ConnectedToggleGroup(listOf(false to "Wydatek", true to "Przychód"), isIncome) {
                    if (it != isIncome) { isIncome = it; categoryId = null; userPickedCategory = false }
                }
            }

            // Duże pole kwoty
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
                    .background(if (isIncome) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer)
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Kwota", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
                androidx.compose.foundation.text.BasicTextField(
                    value = amount, onValueChange = { v -> amount = v.filter { it.isDigit() || it == ',' || it == '.' }.take(10); error = null },
                    textStyle = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.Bold, color = onContainer, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(onContainer),
                    visualTransformation = CurrencySuffix,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (amount.isEmpty()) Text("0,00 zł", style = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.Bold,
                                color = onContainer.copy(alpha = 0.35f)))
                            inner()
                        }
                    },
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }

            TextField(
                merchant, { merchant = it; error = null }, Modifier.fillMaxWidth(),
                label = { Text(if (isIncome) "Źródło (np. pracodawca)" else "Sprzedawca / sklep") },
                leadingIcon = { Icon(Icons.Filled.Storefront, null) }, singleLine = true, shape = MaterialTheme.shapes.large, colors = filledFieldColors(),
            )
            Surface(onClick = { showDate = true }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(formatDay(date), style = MaterialTheme.typography.bodyLarge)
                }
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Kategoria", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (!userPickedCategory && categoryId != null) {
                        Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text("dobrano automatycznie", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                CategoryPicker(visibleCats, categoryId) { categoryId = it; userPickedCategory = true }
            }

            TextField(
                note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Notatka (opcjonalnie)") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, null) }, shape = MaterialTheme.shapes.large, colors = filledFieldColors(),
            )

            if (image != null) {
                SectionCard {
                    Text("Zdjęcie paragonu", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    AsyncImage(
                        File(image.orEmpty()), "Paragon",
                        Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.medium).clickable { showImage = true },
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            if (!isIncome && (items.isNotEmpty() || image != null)) {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pozycje z paragonu", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { items += ItemRow("", "") }) { Icon(Icons.Filled.Add, null); Text("Dodaj") }
                    }
                    if (fromReceipt && !receiptConsistent && items.isNotEmpty()) {
                        Text("Suma pozycji nie zgadza się z sumą paragonu — sprawdź odczytane wartości.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.tertiaryContainer).padding(10.dp).fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                    }
                    if (items.any { it.raw != null }) {
                        Text("Skrócone nazwy z paragonu rozwinęliśmy automatycznie. Popraw nazwę, a zapamiętamy ją na kolejne zakupy.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp))
                    }
                    items.forEachIndexed { i, row ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                            val group = remember(row.name) { pl.openpayments.domain.ProductDictionary.groupOf(row.name) }
                            OutlinedTextField(row.name, { row.name = it }, Modifier.weight(1f), singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium, placeholder = { Text("Nazwa") },
                                shape = MaterialTheme.shapes.medium,
                                supportingText = {
                                    Text(listOfNotNull(group.label, row.raw?.takeIf { !it.equals(row.name, true) }?.let { "z paragonu: $it" })
                                        .joinToString(" · "), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                })
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(row.price, { v -> row.price = v.filter { it.isDigit() || it == ',' || it == '.' } },
                                Modifier.width(96.dp), singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), placeholder = { Text("0,00") })
                            IconButton(onClick = { items.removeAt(i) }) { Icon(Icons.Filled.Close, "Usuń pozycję") }
                        }
                    }
                    if (items.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Row {
                            Text("Suma pozycji", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text(formatMoney(itemsSum), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                        }
                        if (itemsSum != (parseMoney(amount) ?: 0L)) {
                            TextButton(onClick = { amount = moneyInput(itemsSum) }) { Text("Ustaw kwotę na sumę pozycji") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.toDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Anuluj") } },
        ) { DatePicker(state) }
    }

    if (showImage && image != null) {
        Dialog(onDismissRequest = { showImage = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).clickable { showImage = false }) {
                AsyncImage(File(image.orEmpty()), "Paragon", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Usunąć transakcję?") },
            text = { Text("Transakcja zostanie usunięta razem ze zdjęciem paragonu.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    original?.let { vm.deleteTransaction(it) }
                    scope.launch { nav.back() }
                }) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Anuluj") } },
        )
    }
}

/** Dokleja " zł" do wyświetlanej kwoty (kursor nie wchodzi w dopisek). */
private object CurrencySuffix : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        if (text.isEmpty()) return androidx.compose.ui.text.input.TransformedText(text, androidx.compose.ui.text.input.OffsetMapping.Identity)
        val out = androidx.compose.ui.text.AnnotatedString(text.text + " zł")
        val len = text.length
        return androidx.compose.ui.text.input.TransformedText(out, object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset
            override fun transformedToOriginal(offset: Int) = offset.coerceAtMost(len)
        })
    }
}
