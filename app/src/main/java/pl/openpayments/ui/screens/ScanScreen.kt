package pl.openpayments.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch
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
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var stage by remember { mutableStateOf("Odczytuję sklep, datę i pozycje…") }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingFile by remember { mutableStateOf<File?>(null) }

    fun process(file: File, cropped: Boolean) {
        processing = file; preview = null; error = null
        scope.launch {
            runCatching {
                val out = ReceiptScanner.scan(
                    ctx, file, alreadyCropped = cropped,
                    onStage = { s -> scope.launch { stage = s } },
                    onPreview = { b -> val img = b.asImageBitmap(); scope.launch { preview = img } },
                )
                val r = out.result
                if (r.total == null && r.items.isEmpty()) error("nothing")
                vm.draft = ReceiptDraft(
                    merchant = r.merchant ?: "", date = r.date, total = r.total,
                    items = vm.repo.resolveItems(r.items),
                    imagePath = file.absolutePath, consistent = ReceiptParser.isConsistent(r), rawLines = out.lines,
                )
            }.onSuccess { nav.replaceWithEdit() }
                .onFailure {
                    processing = null; preview = null
                    error = "Nie udało się odczytać paragonu. Spróbuj „Skanuj paragon” — połóż go płasko na ciemnym tle, przy dobrym świetle."
                    vm.draft = ReceiptDraft("", null, null, emptyList(), file.absolutePath, true, emptyList())
                }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingFile
        if (ok && f != null) process(f, cropped = false) else f?.delete()
    }
    fun launchCamera() {
        val (f, uri) = vm.repo.newReceiptFile()
        pendingFile = f; camera.launch(uri)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { process(vm.repo.copyToReceipts(uri), cropped = false) }
    }
    // Skaner dokumentów z Google Play services: sam znajduje krawędzie paragonu, pozwala poprawić
    // narożniki i oddaje wyprostowane zdjęcie. Działa na urządzeniu, bez wysyłania obrazu.
    val docScanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = GmsDocumentScanningResult.fromActivityResultIntent(res.data)?.pages?.firstOrNull()?.imageUri
            if (uri != null) scope.launch { process(vm.repo.copyToReceipts(uri), cropped = true) }
        }
    }
    fun launchScanner() {
        val activity = ctx.findActivity()
        if (activity == null) { launchCamera(); return }
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER)
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { sender -> docScanner.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { launchCamera() } // brak Google Play services — zwykły aparat + nasze kadrowanie
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
                    val p = preview
                    if (p != null) Image(p, null, Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit, alpha = 0.75f)
                    else AsyncImage(f, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.6f)
                    ScanLine()
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ContainedLoadingIndicator(Modifier.size(96.dp))
                        Spacer(Modifier.height(12.dp))
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                            Text(stage, Modifier.padding(12.dp))
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
                        Tip(Icons.Filled.CropFree, "Krawędzie i perspektywę prostujemy sami")
                        Tip(Icons.Filled.WbSunny, "Dobre światło, paragon na ciemnym tle")
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
                onClick = { launchScanner() },
                enabled = processing == null,
                modifier = Modifier.fillMaxWidth().height(64.dp), shapes = ButtonDefaults.shapes(),
            ) { Icon(Icons.Filled.DocumentScanner, null); Spacer(Modifier.width(8.dp)); Text("Skanuj paragon") }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = { launchCamera() },
                    enabled = processing == null,
                    modifier = Modifier.weight(1f).height(56.dp), shapes = ButtonDefaults.shapes(),
                ) { Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text("Aparat") }
                FilledTonalButton(
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = processing == null,
                    modifier = Modifier.weight(1f).height(56.dp), shapes = ButtonDefaults.shapes(),
                ) { Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Galeria") }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
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
