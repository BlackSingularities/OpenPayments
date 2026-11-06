package pl.openpayments.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import pl.openpayments.data.ReceiptItem
import pl.openpayments.domain.ReceiptParser
import pl.openpayments.domain.ReceiptScanner
import pl.openpayments.ui.MainViewModel
import pl.openpayments.ui.Nav
import pl.openpayments.ui.ReceiptDraft
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(vm: MainViewModel, nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var processing by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingFile by remember { mutableStateOf<File?>(null) }

    fun process(file: File) {
        processing = file; error = null
        scope.launch {
            runCatching {
                val lines = ReceiptScanner.recognize(ctx, Uri.fromFile(file))
                val r = ReceiptParser.parse(lines)
                if (r.total == null && r.items.isEmpty()) error("nothing")
                vm.draft = ReceiptDraft(
                    merchant = r.merchant ?: "", date = r.date, total = r.total,
                    items = vm.repo.resolveItems(r.items),
                    imagePath = file.absolutePath, consistent = ReceiptParser.isConsistent(r), rawLines = lines,
                )
            }.onSuccess { nav.replaceWithEdit() }
                .onFailure {
                    processing = null
                    error = "Nie udało się odczytać paragonu. Zrób zdjęcie z bliska, przy dobrym świetle, na płaskiej powierzchni."
                    vm.draft = ReceiptDraft("", null, null, emptyList(), file.absolutePath, true, emptyList())
                }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingFile
        if (ok && f != null) process(f) else f?.delete()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { process(vm.repo.copyToReceipts(uri)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skanuj paragon") },
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.fillMaxWidth().weight(1f).clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                val f = processing
                if (f != null) {
                    AsyncImage(f, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.6f)
                    ScanLine()
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ContainedLoadingIndicator(Modifier.size(96.dp))
                        Spacer(Modifier.height(12.dp))
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                            Text("Odczytuję sklep, datę i pozycje…", Modifier.padding(12.dp))
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                        val cookie = MaterialShapes.Cookie12Sided.toShape()
                        Box(
                            Modifier.size(112.dp).clip(cookie).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.DocumentScanner, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                        Spacer(Modifier.height(16.dp))
                        Text("Zrób zdjęcie paragonu", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "OpenPayments odczyta sklep, datę, sumę i listę kupionych rzeczy. Rozpoznawanie działa na telefonie — zdjęcie nigdzie nie jest wysyłane.",
                            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(20.dp))
                        Tip(Icons.Filled.WbSunny, "Dobre, równomierne światło")
                        Tip(Icons.Filled.CropFree, "Cały paragon w kadrze, prosto")
                        Tip(Icons.Filled.Straighten, "Długi paragon? Wygładź go na stole")
                    }
                }
            }
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                TextButton(onClick = { nav.replaceWithEdit() }) { Text("Uzupełnij ręcznie z tym zdjęciem") }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val (f, uri) = vm.repo.newReceiptFile()
                    pendingFile = f; camera.launch(uri)
                },
                enabled = processing == null,
                modifier = Modifier.fillMaxWidth().height(64.dp), shapes = ButtonDefaults.shapes(),
            ) { Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text("Zrób zdjęcie") }
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(
                onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = processing == null,
                modifier = Modifier.fillMaxWidth().height(64.dp), shapes = ButtonDefaults.shapes(),
            ) { Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Wybierz z galerii") }
        }
    }
}

@Composable
private fun Tip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ScanLine() {
    val t = rememberInfiniteTransition(label = "scan")
    val y by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse), label = "y")
    val c = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize()) {
        val yy = size.height * y
        drawLine(c, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 4.dp.toPx())
    }
}
