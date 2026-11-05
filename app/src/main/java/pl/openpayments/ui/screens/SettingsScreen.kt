package pl.openpayments.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import pl.openpayments.BuildConfig
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import pl.openpayments.data.Category
import pl.openpayments.ui.MainViewModel
import pl.openpayments.ui.Nav
import pl.openpayments.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, nav: Nav, canLock: () -> Boolean) {
    val ctx = LocalContext.current
    val prefs = vm.repo.prefs
    val theme by vm.theme.collectAsState()
    val rules by vm.ruleCount.collectAsState()
    var alerts by remember { mutableStateOf(prefs.budgetAlerts) }
    var reminders by remember { mutableStateOf(prefs.subscriptionReminders) }
    var learn by remember { mutableStateOf(prefs.learnRules) }
    var lock by remember { mutableStateOf(prefs.appLock) }
    var confirmImport by remember { mutableStateOf<Uri?>(null) }
    val lockAvailable = remember { canLock() }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportBackup(it) }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmImport = uri }
    fun open(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Ustawienia") },
            navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") } })
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionCard {
                Text("Wygląd", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("system" to "Systemowy", "light" to "Jasny", "dark" to "Ciemny").forEachIndexed { i, (k, l) ->
                        SegmentedButton(theme == k, { prefs.setTheme(k) }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(l) }
                    }
                }
            }
            SectionCard {
                Text("Bezpieczeństwo", style = MaterialTheme.typography.titleMedium)
                SwitchRow(Icons.Filled.Fingerprint, "Blokada aplikacji",
                    if (lockAvailable) "Odcisk palca, twarz lub PIN telefonu" else "Ustaw blokadę ekranu w telefonie, aby włączyć",
                    lock, enabled = lockAvailable) { lock = it; prefs.appLock = it }
            }
            SectionCard {
                Text("Powiadomienia", style = MaterialTheme.typography.titleMedium)
                SwitchRow(Icons.Filled.NotificationsActive, "Ostrzeżenia budżetowe", "Zanim przekroczysz limit i po przekroczeniu", alerts) {
                    alerts = it; prefs.budgetAlerts = it
                }
                SwitchRow(Icons.Filled.Event, "Przypomnienia o płatnościach", "2 dni przed płatnością cykliczną", reminders) {
                    reminders = it; prefs.subscriptionReminders = it
                }
            }
            SectionCard {
                Text("Kategoryzacja", style = MaterialTheme.typography.titleMedium)
                SwitchRow(Icons.Filled.AutoAwesome, "Ucz się z moich poprawek", "Wyuczone reguły: $rules", learn) {
                    learn = it; prefs.learnRules = it
                }
                NavRow(Icons.Filled.Category, "Kategorie", "Dodawaj i edytuj kategorie") { nav.categories() }
                NavRow(Icons.Filled.RestartAlt, "Wyczyść wyuczone reguły", null) { vm.clearRules() }
            }
            SectionCard {
                Text("Dane", style = MaterialTheme.typography.titleMedium)
                NavRow(Icons.Filled.Backup, "Utwórz kopię zapasową", "Plik JSON z całą historią (bez zdjęć paragonów)") {
                    saveBackup.launch("openpayments-${java.time.LocalDate.now()}.json")
                }
                NavRow(Icons.Filled.SettingsBackupRestore, "Przywróć z kopii", "Zastępuje obecne dane") {
                    openBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                }
                NavRow(Icons.Filled.FileDownload, "Eksportuj do CSV", "Otwórz w Excelu lub Arkuszach Google") {
                    vm.exportCsv { uri ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(Intent.createChooser(send, "Eksport transakcji"))
                    }
                }
            }
            SectionCard {
                Text("Aktualizacje", style = MaterialTheme.typography.titleMedium)
                SwitchRow(Icons.Filled.SystemUpdate, "Sprawdzaj przy uruchomieniu", "Bez telemetrii", vm.autoUpdate,
                    enabled = BuildConfig.UPDATES_ENABLED) { vm.changeAutoUpdate(it) }
                NavRow(Icons.Filled.Refresh, if (vm.updateBusy) "Sprawdzanie…" else "Sprawdź teraz",
                    vm.release?.let { "Dostępna wersja ${it.version}" } ?: vm.updateStatus ?: "Zainstalowana ${BuildConfig.VERSION_NAME}") {
                    if (vm.release != null) vm.showUpdateDialog = true else vm.checkUpdates()
                }
            }
            SectionCard {
                Text("O aplikacji", style = MaterialTheme.typography.titleMedium)
                val official = vm.certificate.equals(BuildConfig.RELEASE_CERT_SHA256, true)
                InfoRow(Icons.Filled.Info, "Wersja", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                InfoRow(if (official) Icons.Filled.Verified else Icons.Filled.Warning,
                    if (official) "Podpis: oficjalne wydanie" else "Podpis: kompilacja lokalna",
                    vm.certificate?.chunked(2)?.take(8)?.joinToString(":")?.uppercase()?.let { "$it…" } ?: "nieznany")
                InfoRow(Icons.Filled.Shield, "Prywatność", "Dane tylko na tym urządzeniu, OCR offline, bez reklam")
                InfoRow(Icons.Filled.Gavel, "Licencja", "MIT")
                NavRow(Icons.Filled.Person, "Pomysł i wykonanie", "Adam Rędzikowski · redzikowski.com") { open("https://redzikowski.com") }
                NavRow(Icons.Filled.Email, "Kontakt", "kontakt@redzikowski.com") { open("mailto:kontakt@redzikowski.com") }
                NavRow(Icons.Filled.LocalCafe, "Postaw kawę", "Wesprzyj rozwój na buycoffee.to") { open("https://buycoffee.to/adamredzikowski") }
            }
        }
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Przywrócić kopię?") },
            text = { Text("Wszystkie obecne transakcje, budżety i kategorie zostaną zastąpione zawartością kopii.") },
            confirmButton = { TextButton(onClick = { vm.importBackup(uri); confirmImport = null }) { Text("Przywróć") } },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String) {
    ListItem(
        leadingContent = { Icon(icon, null) }, headlineContent = { Text(title) }, supportingContent = { Text(sub) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun SwitchRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, null) }, headlineContent = { Text(title) }, supportingContent = { Text(sub) },
        trailingContent = { Switch(value, onChange, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun NavRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = MaterialTheme.shapes.medium) {
        ListItem(
            leadingContent = { Icon(icon, null) }, headlineContent = { Text(title) },
            supportingContent = sub?.let { { Text(it) } },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(vm: MainViewModel, nav: Nav) {
    val cats by vm.categories.collectAsState()
    var editing by remember { mutableStateOf<Category?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Kategorie") },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") } })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Category(name = "", icon = "dots", color = categoryPalette.first()) }) {
                Icon(Icons.Filled.Add, "Dodaj kategorię")
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 96.dp)) {
            listOf(false to "Wydatki", true to "Przychody").forEach { (income, label) ->
                item { Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(8.dp, 16.dp, 8.dp, 4.dp)) }
                items(cats.filter { it.isIncome == income }, key = { it.id }) { c ->
                    Surface(onClick = { editing = c }, color = Color.Transparent, shape = MaterialTheme.shapes.medium) {
                        ListItem(leadingContent = { CategoryBadge(c) }, headlineContent = { Text(c.name) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent))
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
                    OutlinedTextField(name, { name = it }, label = { Text("Nazwa") }, singleLine = true)
                    if (c.id == 0L) Row {
                        FilterChip(!income, { income = false }, { Text("Wydatek") }); Spacer(Modifier.width(8.dp))
                        FilterChip(income, { income = true }, { Text("Przychód") })
                    }
                    Text("Ikona", style = MaterialTheme.typography.labelLarge)
                    categoryIcons.keys.chunked(5).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { k ->
                                FilledIconToggleButton(icon == k, { icon = k }) { Icon(iconFor(k), null) }
                            }
                        }
                    }
                    Text("Kolor", style = MaterialTheme.typography.labelLarge)
                    categoryPalette.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { col ->
                                FilledIconButton(onClick = { color = col },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(col)),
                                    modifier = Modifier.size(34.dp)) {
                                    if (col == color) Icon(Icons.Filled.Check, null, tint = Color.White)
                                }
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
