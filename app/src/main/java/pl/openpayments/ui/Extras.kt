package pl.openpayments.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.openpayments.domain.parseMoney

/** Pierwsze uruchomienie: krótkie przedstawienie i opcjonalny budżet miesięczny. */
@Composable
fun OnboardingScreen(vm: MainViewModel) {
    var budget by remember { mutableStateOf("") }
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Box(
                Modifier.size(72.dp).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimary) }
            Spacer(Modifier.height(24.dp))
            Text("Budżet domowy,\nogarnięty.", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                "Zobacz, na co naprawdę idą pieniądze — bez przepisywania paragonów do arkusza.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Feature(Icons.Filled.DocumentScanner, "Skan paragonu", "Sklep, data, suma i lista produktów odczytane ze zdjęcia.")
            Feature(Icons.Filled.AutoAwesome, "Sama kategoryzuje", "Poprawiasz raz — aplikacja zapamiętuje na przyszłość.")
            Feature(Icons.Filled.NotificationsActive, "Pilnuje budżetu", "Ostrzeżenie, zanim przekroczysz limit, nie po fakcie.")
            Feature(Icons.Filled.Lock, "Prywatnie", "Dane zostają na telefonie. Bez kont i bez reklam.")
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                budget, { v -> budget = v.filter { it.isDigit() || it == ',' || it == '.' } }, Modifier.fillMaxWidth(),
                label = { Text("Ile chcesz wydawać miesięcznie? (opcjonalnie)") }, suffix = { Text("zł") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = MaterialTheme.shapes.medium,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { vm.finishOnboarding(parseMoney(budget)) },
                modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.large,
            ) { Text("Zaczynamy", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(16.dp))
            Text(
                "Adam Rędzikowski · redzikowski.com", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LockScreen(unlock: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("OpenPayments jest zablokowane", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = unlock) { Icon(Icons.Filled.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("Odblokuj") }
        }
    }
}

/** Okno nowej wersji — bez listy zmian i linków, jak w pozostałych aplikacjach. */
@Composable
fun UpdateDialog(vm: MainViewModel) {
    val r = vm.release ?: return
    AlertDialog(
        onDismissRequest = { if (!vm.updateBusy) vm.showUpdateDialog = false },
        icon = { Icon(Icons.Filled.SystemUpdate, null) },
        title = { Text("Dostępna wersja ${r.version}") },
        text = {
            Column {
                Text("Nowa wersja OpenPayments jest gotowa do instalacji. Twoje dane zostaną zachowane.")
                if (vm.updateBusy) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator({ vm.updateProgress }, Modifier.fillMaxWidth())
                }
                vm.updateStatus?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = vm::installUpdate, enabled = !vm.updateBusy) { Text("Zainstaluj") } },
        dismissButton = { TextButton(onClick = { vm.showUpdateDialog = false }, enabled = !vm.updateBusy) { Text("Później") } },
    )
}
