package pl.openpayments.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import pl.openpayments.BuildConfig
import pl.openpayments.data.Category
import pl.openpayments.ui.MainViewModel
import pl.openpayments.ui.Nav
import pl.openpayments.ui.components.*

/** Element listy ustawień: przełącznik albo akcja. */
private sealed interface SettingRow {
    val icon: ImageVector; val title: String; val sub: String?
    data class Toggle(override val icon: ImageVector, override val title: String, override val sub: String?, val value: Boolean,
                      val enabled: Boolean = true, val onChange: (Boolean) -> Unit) : SettingRow
    data class Action(override val icon: ImageVector, override val title: String, override val sub: String?,
                      val chevron: Boolean = true, val onClick: (() -> Unit)?) : SettingRow
}

@Composable
private fun RowsGroup(rows: List<SettingRow>) {
    SegmentedGroup(rows) { r, shapes ->
        when (r) {
            is SettingRow.Toggle -> GroupRow(
                shapes, r.title, r.sub, r.icon, enabled = r.enabled,
                trailing = { Switch(r.value, r.onChange, enabled = r.enabled) },
                onClick = { if (r.enabled) r.onChange(!r.value) },
            )
            is SettingRow.Action -> GroupRow(
                shapes, r.title, r.sub, r.icon,
                trailing = if (r.chevron && r.onClick != null) ({ Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }) else null,
                onClick = r.onClick,
            )
        }
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, nav: Nav, canLock: () -> Boolean) {
    val ctx = LocalContext.current
    val prefs = vm.repo.prefs
    val theme by vm.theme.collectAsState()
    val dynamic by vm.dynamicColor.collectAsState()
    val rules by vm.ruleCount.collectAsState()
    val aliases by vm.aliasCount.collectAsState()
    var alerts by remember { mutableStateOf(prefs.budgetAlerts) }
    var reminders by remember { mutableStateOf(prefs.subscriptionReminders) }
    var learn by remember { mutableStateOf(prefs.learnRules) }
    var lock by remember { mutableStateOf(prefs.appLock) }
    var confirmImport by remember { mutableStateOf<Uri?>(null) }
    val lockAvailable = remember { canLock() }
    val dynamicSupported = Build.VERSION.SDK_INT >= 31

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportBackup(it) }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmImport = uri }
    fun open(url: String) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }

    ExpressiveScaffold(title = "Ustawienia", onBack = { nav.back() }) { pad ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(top = pad.calculateTopPadding(), bottom = 32.dp).navigationBarsPadding(),
        ) {
            GroupLabel("Wygląd")
            ConnectedToggleGroup(
                listOf("system" to "Systemowy", "light" to "Jasny", "dark" to "Ciemny"), theme,
                Modifier.padding(horizontal = 16.dp),
            ) { prefs.setTheme(it) }
            Spacer(Modifier.height(12.dp))
            RowsGroup(listOf(
                SettingRow.Toggle(Icons.Filled.Palette, "Kolory z tapety",
                    if (dynamicSupported) "Material You — paleta dopasowana do tapety" else "Wymaga Androida 12 lub nowszego",
                    dynamic && dynamicSupported, enabled = dynamicSupported) { prefs.setDynamicColor(it) },
            ))

            GroupLabel("Bezpieczeństwo")
            RowsGroup(listOf(
                SettingRow.Toggle(Icons.Filled.Fingerprint, "Blokada aplikacji",
                    if (lockAvailable) "Odcisk palca, twarz lub PIN telefonu" else "Ustaw blokadę ekranu w telefonie, aby włączyć",
                    lock, enabled = lockAvailable) { lock = it; prefs.appLock = it },
            ))

            GroupLabel("Powiadomienia")
            RowsGroup(listOf(
                SettingRow.Toggle(Icons.Filled.NotificationsActive, "Ostrzeżenia budżetowe", "Zanim przekroczysz limit i po przekroczeniu", alerts) {
                    alerts = it; prefs.budgetAlerts = it
                },
                SettingRow.Toggle(Icons.Filled.Event, "Przypomnienia o płatnościach", "2 dni przed płatnością cykliczną", reminders) {
                    reminders = it; prefs.subscriptionReminders = it
                },
            ))

            GroupLabel("Kategoryzacja")
            RowsGroup(listOf(
                SettingRow.Toggle(Icons.Filled.AutoAwesome, "Ucz się z moich poprawek", "Wyuczone reguły: $rules", learn) {
                    learn = it; prefs.learnRules = it
                },
                SettingRow.Action(Icons.Filled.Category, "Kategorie", "Dodawaj i edytuj kategorie") { nav.categories() },
                SettingRow.Action(Icons.Filled.RestartAlt, "Wyczyść wyuczone reguły", null, chevron = false) { vm.clearRules() },
                SettingRow.Action(Icons.Filled.Spellcheck, "Wyuczone nazwy produktów: $aliases",
                    "Twoje poprawki skróconych nazw z paragonów. Dotknij, aby wyczyścić", chevron = false) { vm.clearAliases() },
            ))

            GroupLabel("Dane")
            RowsGroup(listOf(
                SettingRow.Action(Icons.Filled.Backup, "Utwórz kopię zapasową", "Plik JSON z całą historią (bez zdjęć paragonów)") {
                    saveBackup.launch("openpayments-${java.time.LocalDate.now()}.json")
                },
                SettingRow.Action(Icons.Filled.SettingsBackupRestore, "Przywróć z kopii", "Zastępuje obecne dane") {
                    openBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                },
                SettingRow.Action(Icons.Filled.FileDownload, "Eksportuj do CSV", "Otwórz w Excelu lub Arkuszach Google") {
                    vm.exportCsv { uri ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(Intent.createChooser(send, "Eksport transakcji"))
                    }
                },
            ))

            GroupLabel("Aktualizacje")
            RowsGroup(listOf(
                SettingRow.Toggle(Icons.Filled.SystemUpdate, "Sprawdzaj przy uruchomieniu", "Bez telemetrii", vm.autoUpdate,
                    enabled = BuildConfig.UPDATES_ENABLED) { vm.changeAutoUpdate(it) },
                SettingRow.Action(Icons.Filled.Refresh, if (vm.updateBusy) "Sprawdzanie…" else "Sprawdź teraz",
                    vm.release?.let { "Dostępna wersja ${it.version}" } ?: vm.updateStatus ?: "Zainstalowana ${BuildConfig.VERSION_NAME}",
                    chevron = false) {
                    if (vm.release != null) vm.showUpdateDialog = true else vm.checkUpdates()
                },
            ))
            if (vm.updateBusy) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp))

            GroupLabel("O aplikacji")
            val official = vm.certificate.equals(BuildConfig.RELEASE_CERT_SHA256, true)
            RowsGroup(listOf(
                SettingRow.Action(Icons.Filled.Info, "Wersja", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", onClick = null),
                SettingRow.Action(if (official) Icons.Filled.Verified else Icons.Filled.Warning,
                    if (official) "Podpis: oficjalne wydanie" else "Podpis: kompilacja lokalna",
                    vm.certificate?.chunked(2)?.take(8)?.joinToString(":")?.uppercase()?.let { "$it…" } ?: "nieznany", onClick = null),
                SettingRow.Action(Icons.Filled.Shield, "Prywatność", "Dane tylko na tym urządzeniu, OCR offline, bez reklam", onClick = null),
                SettingRow.Action(Icons.Filled.Gavel, "Licencja", "MIT", onClick = null),
                SettingRow.Action(Icons.Filled.Person, "Pomysł i wykonanie", "Adam Rędzikowski · redzikowski.com") { open("https://redzikowski.com") },
                SettingRow.Action(Icons.Filled.Email, "Kontakt", "kontakt@redzikowski.com") { open("mailto:kontakt@redzikowski.com") },
                SettingRow.Action(Icons.Filled.LocalCafe, "Postaw kawę", "Wesprzyj rozwój na buycoffee.to") { open("https://buycoffee.to/adamredzikowski") },
            ))
        }
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            icon = { Icon(Icons.Filled.SettingsBackupRestore, null) },
            title = { Text("Przywrócić kopię?") },
            text = { Text("Wszystkie obecne transakcje, budżety i kategorie zostaną zastąpione zawartością kopii.") },
            confirmButton = { TextButton(onClick = { vm.importBackup(uri); confirmImport = null }) { Text("Przywróć") } },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Anuluj") } },
        )
    }
}

@Composable
fun CategoriesScreen(vm: MainViewModel, nav: Nav) {
    val cats by vm.categories.collectAsState()
    var editing by remember { mutableStateOf<Category?>(null) }
    ExpressiveScaffold(
        title = "Kategorie", onBack = { nav.back() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Category(name = "", icon = "dots", color = categoryPalette.first()) },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Nowa") },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 120.dp)) {
            listOf(false to "Wydatki", true to "Przychody").forEach { (income, label) ->
                val list = cats.filter { it.isIncome == income }
                item { GroupLabel(label) }
                item {
                    SegmentedGroup(list) { c, shapes ->
                        GroupRow(shapes, c.name, leading = { CategoryBadge(c, 40.dp) }) { editing = c }
                    }
                }
            }
        }
    }
    editing?.let { c ->
        var name by remember(c) { mutableStateOf(c.name) }
        var icon by remember(c) { mutableStateOf(c.icon) }
        var color by remember(c) { mutableLongStateOf(c.color) }
        var income by remember(c) { mutableStateOf(c.isIncome) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (c.id == 0L) "Nowa kategoria" else "Edytuj kategorię") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryBadge(c.copy(icon = icon, color = color), 56.dp)
                    TextField(name, { name = it }, label = { Text("Nazwa") }, singleLine = true,
                        shape = MaterialTheme.shapes.large, colors = filledFieldColors())
                    if (c.id == 0L) ConnectedToggleGroup(listOf(false to "Wydatek", true to "Przychód"), income) { income = it }
                    Text("Ikona", style = MaterialTheme.typography.labelLarge)
                    categoryIcons.keys.chunked(5).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { k -> FilledIconToggleButton(icon == k, { icon = k }) { Icon(iconFor(k), null) } }
                        }
                    }
                    Text("Kolor", style = MaterialTheme.typography.labelLarge)
                    categoryPalette.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { col ->
                                FilledIconButton(
                                    onClick = { color = col },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(col)),
                                    modifier = Modifier.size(34.dp),
                                ) { if (col == color) Icon(Icons.Filled.Check, null, tint = Color.White) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vm.saveCategory(c.copy(name = name.trim(), icon = icon, color = color, isIncome = income)); editing = null
                }) { Text("Zapisz") }
            },
            dismissButton = {
                Row {
                    if (c.id != 0L) TextButton(onClick = { vm.deleteCategory(c); editing = null }) {
                        Text("Usuń", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { editing = null }) { Text("Anuluj") }
                }
            },
        )
    }
}
