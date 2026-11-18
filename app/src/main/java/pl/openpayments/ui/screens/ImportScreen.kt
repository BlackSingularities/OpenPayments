package pl.openpayments.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.openpayments.data.Category
import pl.openpayments.data.TxSource
import pl.openpayments.domain.bank.ImportCandidate
import pl.openpayments.domain.bank.Role
import pl.openpayments.domain.bank.SignMode
import pl.openpayments.domain.bank.SkipReason
import pl.openpayments.domain.formatMoney
import pl.openpayments.domain.formatShortDay
import pl.openpayments.domain.toDate
import pl.openpayments.ui.BankImportController
import pl.openpayments.ui.MainViewModel
import pl.openpayments.ui.Nav
import pl.openpayments.ui.components.*
import pl.openpayments.ui.plural
import pl.openpayments.ui.theme.Amber
import pl.openpayments.ui.theme.Positive
import java.time.format.DateTimeFormatter

private val csvTypes = arrayOf(
    "text/csv", "text/comma-separated-values", "text/tab-separated-values", "application/csv",
    "application/vnd.ms-excel", "text/plain", "application/octet-stream",
)

@Composable
fun ImportScreen(vm: MainViewModel, nav: Nav) {
    val imp = vm.bankImport
    val cats by vm.categories.collectAsState()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { imp.load(it) } }
    val saveTemplate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.saveCsvTemplate(it) }
    }
    val pending = imp.pendingUri
    LaunchedEffect(pending) { if (pending != null) { imp.pendingUri = null; imp.load(pending) } }
    fun leave() { imp.clear(); nav.back() }
    BackHandler { leave() }
    var editing by remember { mutableStateOf<ImportCandidate?>(null) }

    ExpressiveScaffold(
        title = "Import z banku",
        subtitle = imp.fileName,
        onBack = ::leave,
        actions = {
            if (imp.hasPreview) IconButton(onClick = { pick.launch(csvTypes) }) { Icon(Icons.Filled.FolderOpen, "Wybierz inny plik") }
        },
        bottomBar = { if (imp.hasPreview) CommitBar(imp) { imp.commit { nav.back() } } },
    ) { pad ->
        when {
            imp.busy && !imp.hasPreview -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ContainedLoadingIndicator(Modifier.size(96.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Rozpoznaję układ pliku…", style = MaterialTheme.typography.titleMedium)
                }
            }
            !imp.hasPreview -> Intro(pad, imp.error, onPick = { pick.launch(csvTypes) }, onTemplate = { saveTemplate.launch("openpayments-wzor.csv") })
            else -> Preview(pad, imp, cats) { editing = it }
        }
    }

    editing?.let { c -> CandidateDialog(imp, c, cats) { editing = null } }
}

// ——— Ekran startowy ———

@Composable
private fun Intro(pad: PaddingValues, error: String?, onPick: () -> Unit, onTemplate: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(top = pad.calculateTopPadding(), bottom = 32.dp).navigationBarsPadding(),
    ) {
        SectionCard(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Box(
                Modifier.size(64.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.AccountBalance, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimary) }
            Spacer(Modifier.height(16.dp))
            Text("Cała historia konta w kilka sekund", style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(8.dp))
            Text(
                "Wybierz plik CSV pobrany z bankowości internetowej. Aplikacja sama rozpozna bank, kolumny, " +
                    "format dat i kwot, uporządkuje nazwy sprzedawców, dobierze kategorie i pominie to, co już masz.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onPick, modifier = Modifier.fillMaxWidth().height(56.dp), shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Filled.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Wybierz plik CSV", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            SectionCard(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        GroupLabel("Jak pobrać plik z banku")
        SegmentedGroup(listOf(
            Icons.AutoMirrored.Filled.Login to "Zaloguj się do bankowości internetowej lub aplikacji banku",
            Icons.Filled.History to "Otwórz historię rachunku i wybierz okres",
            Icons.Filled.Download to "Pobierz zestawienie jako CSV (czasem: „Eksport”, „Zapisz jako”)",
            Icons.AutoMirrored.Filled.OpenInNew to "Wybierz plik tutaj albo otwórz go w menedżerze plików przez OpenPayments",
        )) { (icon, text), shapes -> GroupRow(shapes, text, icon = icon) }

        GroupLabel("Rozpoznawane automatycznie")
        FlowRow(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("mBank", "PKO BP", "ING", "Pekao", "Millennium", "Santander", "Alior", "Credit Agricole", "BNP Paribas",
                "Citi Handlowy", "VeloBank", "Nest Bank", "Revolut", "Wise", "eksport OpenPayments", "i inne").forEach { Pill(it) }
        }

        GroupLabel("Własna tabela")
        SectionCard(Modifier.padding(horizontal = 16.dp)) {
            Text(
                "Dane z Excela albo z banku spoza listy? Wystarczą trzy kolumny w dowolnej kolejności: " +
                    "data, kwota (minus oznacza wydatek) i opis. Mogą dojść kontrahent, kategoria " +
                    "i typ („wydatek”/„przychód”) albo osobne kolumny wydatków i wpływów. Nagłówki po polsku lub angielsku, " +
                    "separator dowolny, a jeśli coś się nie dopasuje — poprawisz to w podglądzie.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(
                    "Data;Kwota;Opis;Kategoria\n2026-11-03;-45,20;Biedronka;Zakupy spożywcze\n2026-11-10;5400,00;Wypłata;Wynagrodzenie",
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                    fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, softWrap = false,
                )
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onTemplate, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Filled.TableChart, null); Spacer(Modifier.width(8.dp)); Text("Zapisz wzór tabeli")
            }
        }

        GroupLabel("Prywatność")
        SegmentedGroup(listOf(Unit)) { _, shapes ->
            GroupRow(shapes, "Plik zostaje na telefonie", "Odczyt i rozpoznawanie odbywają się bez internetu. Nic nie jest wysyłane.", Icons.Filled.Shield)
        }
    }
}

// ——— Podgląd ———

@Composable
private fun Preview(pad: PaddingValues, imp: BankImportController, cats: List<Category>, onEdit: (ImportCandidate) -> Unit) {
    val a = imp.analysis ?: return
    val r = imp.result
    val catMap = remember(cats) { cats.associateBy { it.id } }
    val list = imp.candidates
    val counts = remember(list) { list.groupingBy { it.reason }.eachCount() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp)) {
        item { Summary(imp) }

        item {
            GroupLabel(
                "Kolumny w pliku",
                action = if (a.roles != imp.autoRoles) "Przywróć automatyczne" else null,
            ) { imp.resetRoles() }
        }
        item {
            val cols = (0 until a.table.columns).filter { c ->
                a.table.header?.getOrNull(c)?.isNotBlank() == true || a.table.rows.any { it[c].isNotBlank() }
            }
            SegmentedGroup(cols) { c, shapes -> ColumnRow(imp, c, shapes) }
        }

        item { GroupLabel("Opcje") }
        item {
            data class Opt(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val sub: String, val on: Boolean, val set: (Boolean) -> Unit)
            val opts = buildList {
                counts[SkipReason.DUPLICATE]?.let { n ->
                    add(Opt(Icons.Filled.ContentCopy, "Pomijaj duplikaty", "${plural(n, "pozycja jest", "pozycje są", "pozycji jest")} już w aplikacji — z paragonu, wpisane ręcznie lub z wcześniejszego importu",
                        SkipReason.DUPLICATE in imp.skip) { imp.changeSkip(SkipReason.DUPLICATE, it) })
                }
                counts[SkipReason.TRANSFER]?.let { n ->
                    add(Opt(Icons.Filled.SyncAlt, "Pomijaj przelewy własne", "${plural(n, "operacja", "operacje", "operacji")}: między swoimi kontami, oszczędności, wymiana walut",
                        SkipReason.TRANSFER in imp.skip) { imp.changeSkip(SkipReason.TRANSFER, it) })
                }
                counts[SkipReason.PENDING]?.let { n ->
                    add(Opt(Icons.Filled.HourglassEmpty, "Pomijaj oczekujące i odrzucone", "${plural(n, "operacja", "operacje", "operacji")} bez ostatecznego rozliczenia",
                        SkipReason.PENDING in imp.skip) { imp.changeSkip(SkipReason.PENDING, it) })
                }
                add(Opt(Icons.Filled.SwapVert, "Odwróć znaki kwot", "Gdy wydatki pokazują się jako wpływy", imp.invert) { imp.changeInvert(it) })
            }
            SegmentedGroup(opts) { o, shapes ->
                GroupRow(shapes, o.title, o.sub, o.icon, trailing = { Switch(o.on, o.set) }, onClick = { o.set(!o.on) })
            }
        }

        val allOn = list.isNotEmpty() && list.all(imp::isSelected)
        item {
            GroupLabel(
                "Operacje · ${list.size}",
                action = if (list.isEmpty()) null else if (allOn) "Odznacz wszystkie" else "Zaznacz wszystkie",
            ) { imp.selectAll(!allOn) }
        }
        if (list.isEmpty() && r != null) item {
            EmptyState(Icons.Filled.TableRows, "Brak operacji do pokazania",
                "Wskaż powyżej kolumnę z datą i kwotą (albo kolumny wydatków i wpływów).")
        }
        itemsIndexed(list, key = { _, c -> c.id }) { i, c ->
            CandidateRow(imp, c, catMap[imp.categoryOf(c)], ListItemDefaults.segmentedShapes(i, list.size)) { onEdit(c) }
        }
    }
}

private val periodFmt = DateTimeFormatter.ofPattern("d MMM yyyy", pl.openpayments.domain.PL)

@Composable
private fun Summary(imp: BankImportController) {
    val a = imp.analysis ?: return
    val r = imp.result
    val list = imp.candidates
    SectionCard(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.AccountBalance, null, tint = MaterialTheme.colorScheme.onSecondary) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(a.bank ?: "Własny układ tabeli", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    if (imp.remembered) "Układ kolumn zapamiętany z poprzedniego importu" else "Kolumny rozpoznane automatycznie",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (imp.busy) LoadingIndicator(Modifier.size(36.dp))
        }
        if (list.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            val from = list.minOf { it.date }.toDate(); val to = list.maxOf { it.date }.toDate()
            Text(
                "${plural(list.size, "operacja", "operacje", "operacji")} · ${from.format(periodFmt)} – ${to.format(periodFmt)}",
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.height(6.dp))
        val delim = when (a.table.delimiter) { ';' -> "średnik"; ',' -> "przecinek"; '\t' -> "tabulator"; '|' -> "kreska"; else -> "„${a.table.delimiter}”" }
        Text(
            listOfNotNull(
                "Kodowanie ${a.table.charset}", "separator: $delim", "daty ${a.dateOrder.label}",
                r?.let { "wydatki i wpływy ${it.signMode.label}" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        val warnings = buildList {
            if (Role.DATE !in a.roles) add("Nie znaleziono kolumny z datą — wskaż ją poniżej.")
            if (a.roles.none { it == Role.AMOUNT || it == Role.DEBIT || it == Role.CREDIT }) add("Nie znaleziono kolumny z kwotą — wskaż ją poniżej.")
            if (r?.signMode == SignMode.ALL_EXPENSES && list.isNotEmpty())
                add("Plik nie rozróżnia wydatków i wpływów, więc wszystko potraktowano jako wydatki. Wskaż kolumnę typu operacji albo popraw pojedyncze pozycje.")
            if ((r?.foreignCurrency ?: 0) > 0) add("${plural(r!!.foreignCurrency, "operacja jest", "operacje są", "operacji jest")} w obcej walucie — kwoty bez przeliczenia na złote.")
            if ((r?.ignoredLines ?: 0) > 0) add("Pominięto ${plural(r!!.ignoredLines, "wiersz", "wiersze", "wierszy")} bez daty lub kwoty (podsumowania, blokady środków).")
        }
        warnings.forEach { w ->
            Spacer(Modifier.height(10.dp))
            Row {
                Icon(Icons.Filled.Info, null, Modifier.size(18.dp).padding(top = 2.dp), tint = Amber)
                Spacer(Modifier.width(8.dp))
                Text(w, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun ColumnRow(imp: BankImportController, c: Int, shapes: ListItemShapes) {
    val a = imp.analysis ?: return
    val role = a.roles[c]
    var open by remember { mutableStateOf(false) }
    val title = a.table.header?.getOrNull(c)?.trimStart('#')?.takeIf { it.isNotBlank() } ?: "Kolumna ${c + 1}"
    val samples = a.table.rows.asSequence().map { it[c] }.filter { it.isNotBlank() }.distinct().take(2).toList()
    val changed = imp.autoRoles.getOrNull(c) != role
    SegmentedListItem(
        onClick = { open = true }, shapes = shapes, colors = segmentedColors(),
        supportingContent = {
            Text(samples.joinToString("  ·  ").ifEmpty { "pusta" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Box {
                val colors = if (role == Role.IGNORE) AssistChipDefaults.assistChipColors()
                else AssistChipDefaults.assistChipColors(
                    containerColor = if (changed) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    labelColor = if (changed) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                )
                AssistChip(
                    onClick = { open = true }, label = { Text(role.label, maxLines = 1) }, colors = colors,
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) },
                    border = if (role == Role.IGNORE) AssistChipDefaults.assistChipBorder(true) else null,
                )
                DropdownMenu(open, { open = false }) {
                    Role.entries.forEach { r ->
                        DropdownMenuItem(
                            text = { Text(r.label) },
                            leadingIcon = { if (r == role) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp)) },
                            onClick = { open = false; if (r != role) imp.setRole(c, r) },
                        )
                    }
                }
            }
        },
    ) { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp)) }
}

private fun reasonText(c: ImportCandidate): String? = when (c.reason) {
    SkipReason.DUPLICATE -> c.duplicate?.let { d ->
        when (d.source) {
            TxSource.RECEIPT -> "Jest już z paragonu: ${d.merchant}, ${formatShortDay(d.date)}"
            TxSource.IMPORT -> "Zaimportowana wcześniej"
            TxSource.RECURRING -> "Zaksięgowana jako płatność cykliczna: ${d.merchant}"
            TxSource.MANUAL -> "Wpisana ręcznie: ${d.merchant}, ${formatShortDay(d.date)}"
        }
    } ?: SkipReason.DUPLICATE.label
    null -> null
    else -> c.reason.label
}

@Composable
private fun CandidateRow(imp: BankImportController, c: ImportCandidate, cat: Category?, shapes: ListItemShapes, onClick: () -> Unit) {
    val on = imp.isSelected(c)
    val reason = reasonText(c)
    Box(Modifier.padding(horizontal = 16.dp, vertical = 1.dp).alpha(if (on) 1f else 0.6f)) {
        SegmentedListItem(
            onClick = onClick, shapes = shapes, colors = segmentedColors(),
            leadingContent = { CategoryBadge(cat, 40.dp) },
            supportingContent = {
                Column {
                    Text("${formatShortDay(c.date)} · ${cat?.name ?: "Bez kategorii"}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (reason != null) Text(reason, color = MaterialTheme.colorScheme.tertiary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (c.isIncome) "+" else "−") + formatMoney(c.amount), style = MaterialTheme.typography.titleSmall,
                        color = if (c.isIncome) Positive else MaterialTheme.colorScheme.onSurface,
                    )
                    Checkbox(on, { imp.setSelected(c, it) })
                }
            },
        ) { Text(imp.nameOf(c), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun CommitBar(imp: BankImportController, onCommit: () -> Unit) {
    val chosen = imp.selected
    val out = chosen.filter { !it.isIncome }.sumOf { it.amount }
    val inc = chosen.filter { it.isIncome }.sumOf { it.amount }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                "Wydatki ${formatMoney(out)} · wpływy ${formatMoney(inc)}",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onCommit, enabled = chosen.isNotEmpty() && !imp.busy,
                modifier = Modifier.fillMaxWidth().height(56.dp), shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Filled.Download, null); Spacer(Modifier.width(8.dp))
                Text(if (chosen.isEmpty()) "Zaznacz operacje do importu" else "Importuj ${plural(chosen.size, "operację", "operacje", "operacji")}",
                    style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun CandidateDialog(imp: BankImportController, c: ImportCandidate, cats: List<Category>, onDismiss: () -> Unit) {
    var name by remember(c.id) { mutableStateOf(imp.nameOf(c)) }
    val visible = cats.filter { it.isIncome == c.isIncome }
    AlertDialog(
        onDismissRequest = { imp.setName(c, name); onDismiss() },
        title = { Text((if (c.isIncome) "+" else "−") + formatMoney(c.amount)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatShortDay(c.date) + " " + c.date.toDate().year, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextField(name, { name = it }, label = { Text("Nazwa") }, singleLine = true,
                    shape = MaterialTheme.shapes.large, colors = filledFieldColors(), modifier = Modifier.fillMaxWidth())
                val original = (listOf(c.row.counterparty, c.row.type) + c.row.descriptions).filter { it.isNotBlank() }.distinct()
                if (original.isNotEmpty()) Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("W wyciągu (wiersz ${c.row.line})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(original.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                    }
                }
                reasonText(c)?.let { Pill(it, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Importuj tę operację", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Switch(imp.isSelected(c), { imp.setSelected(c, it) })
                }
                Text("Kategoria", style = MaterialTheme.typography.labelLarge)
                CategoryPicker(visible, imp.categoryOf(c)) { imp.setCategory(c, it) }
            }
        },
        confirmButton = { TextButton(onClick = { imp.setName(c, name); onDismiss() }) { Text("Gotowe") } },
    )
}
