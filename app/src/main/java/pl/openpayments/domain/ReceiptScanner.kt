package pl.openpayments.domain

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import pl.openpayments.domain.DocumentDetector.Pt
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Rozpoznawanie paragonu na urządzeniu (ML Kit, offline — zdjęcie nie opuszcza telefonu).
 *
 * Kolejne kroki:
 *  1. wczytanie zdjęcia z obrotem z EXIF,
 *  2. wykrycie paragonu, przycięcie tła i wyprostowanie perspektywy,
 *  3. wykrycie obrotu tekstu (paragon sfotografowany bokiem lub do góry nogami),
 *  4. OCR kilku wariantów obrazu (kolor, wyrównane oświetlenie, oryginał) — długie paragony
 *     czytamy kawałkami, żeby litery nie były za małe,
 *  5. wybór wariantu, w którym suma pozycji najlepiej zgadza się z sumą paragonu.
 */
object ReceiptScanner {

    data class Outcome(val lines: List<String>, val result: ReceiptParser.Result)

    /**
     * @param alreadyCropped zdjęcie z systemowego skanera dokumentów (już przycięte i wyprostowane)
     * @param onStage opis bieżącego kroku do pokazania na ekranie
     * @param onPreview podgląd wyprostowanego paragonu
     */
    suspend fun scan(
        ctx: Context,
        file: File,
        alreadyCropped: Boolean = false,
        onStage: (String) -> Unit = {},
        onPreview: (Bitmap) -> Unit = {},
    ): Outcome = withContext(Dispatchers.Default) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            onStage("Szukam paragonu na zdjęciu…")
            var original = ReceiptImage.load(ctx, Uri.fromFile(file))
            val quad = if (alreadyCropped) null else runCatching { ReceiptImage.detect(original) }.getOrNull()
            if (quad != null) onStage("Prostuję perspektywę…")
            var base = quad?.let { runCatching { ReceiptImage.warp(original, it) }.getOrNull() } ?: ReceiptImage.normalize(original)
            val cropped = base !== original
            onPreview(base)

            onStage("Odczytuję tekst…")
            var plain = ocr(recognizer, base)
            // Tekst bokiem lub do góry nogami — obracamy i czytamy jeszcze raz.
            val turn = (OcrLayout.dominantAngle(plain) / 90.0).roundToInt() * 90
            val rotated = plain.size >= 3 && turn % 360 != 0
            if (rotated) {
                base = ReceiptImage.rotate(base, -turn)
                original = ReceiptImage.rotate(original, -turn)
                onPreview(base)
                plain = ocr(recognizer, base)
            }

            val candidates = mutableListOf<Candidate>()
            fun add(boxes: List<OcrLayout.Box>, source: Bitmap): Candidate {
                val lines = OcrLayout.rows(boxes)
                return Candidate(lines, ReceiptParser.parse(lines), source).also { candidates += it }
            }

            var best = add(plain, base)
            if (!good(best.result)) {
                onStage("Poprawiam kontrast i czytam jeszcze raz…")
                val enhanced = ReceiptImage.enhance(base)
                best = pick(best, add(ocrTiled(recognizer, enhanced), base))
                enhanced.recycle()
            }
            if (!good(best.result) && isLong(base)) {
                best = pick(best, add(ocrTiled(recognizer, base), base))
            }
            if (!good(best.result) && cropped) {
                onStage("Sprawdzam całe zdjęcie…")
                val full = ReceiptImage.normalize(original)
                best = pick(best, add(ocr(recognizer, full), original))
            }
            onStage("Sprawdzam sumę…")

            // Zapisujemy przycięty i wyprostowany paragon zamiast całego zdjęcia — o ile to on dał wynik.
            if (best.source !== original || rotated) {
                runCatching { ReceiptImage.saveJpeg(best.source, file) }
            }
            Outcome(best.lines, best.result)
        } finally {
            recognizer.close()
        }
    }

    private class Candidate(val lines: List<String>, val result: ReceiptParser.Result, val source: Bitmap)

    /** Wynik pewny: suma pozycji zgadza się z sumą paragonu. */
    private fun good(r: ReceiptParser.Result) = r.items.size >= 1 && ReceiptParser.isConsistent(r)

    private fun pick(a: Candidate, b: Candidate) = if (score(b.result) > score(a.result)) b else a

    fun score(r: ReceiptParser.Result): Double {
        var s = 0.0
        val total = r.total
        if (total != null) s += 10
        if (r.merchant != null) s += 3
        if (r.date != null) s += 3
        s += min(r.items.size, 80) * 0.5
        if (total != null && total > 0 && r.items.isNotEmpty()) {
            if (ReceiptParser.isConsistent(r)) s += 100
            else s += 50 * (1 - min(1.0, abs(total - r.itemsSum).toDouble() / total))
        }
        return s
    }

    private fun isLong(b: Bitmap) = b.height > b.width * 2.2

    private suspend fun ocr(recognizer: TextRecognizer, bmp: Bitmap, dy: Float = 0f): List<OcrLayout.Box> {
        val text = recognizer.process(InputImage.fromBitmap(bmp, 0)).await()
        return text.textBlocks.flatMap { it.lines }.mapNotNull { l ->
            val cp = l.cornerPoints
            val corners = if (cp != null && cp.size == 4) cp.map { Pt(it.x.toFloat(), it.y + dy) }
            else l.boundingBox?.let { r ->
                listOf(Pt(r.left.toFloat(), r.top + dy), Pt(r.right.toFloat(), r.top + dy), Pt(r.right.toFloat(), r.bottom + dy), Pt(r.left.toFloat(), r.bottom + dy))
            } ?: return@mapNotNull null
            OcrLayout.Box(l.text, corners)
        }
    }

    /**
     * Długi paragon czytamy w zachodzących na siebie kawałkach. Z każdego kawałka bierzemy tylko
     * linie, których środek leży w jego „rdzeniu” — dzięki temu nic się nie dubluje na łączeniach.
     */
    private suspend fun ocrTiled(recognizer: TextRecognizer, bmp: Bitmap): List<OcrLayout.Box> {
        if (!isLong(bmp)) return ocr(recognizer, bmp)
        val w = bmp.width; val h = bmp.height
        val tileH = (w * 1.5).roundToInt()
        val overlap = (w * 0.25).roundToInt()
        val out = mutableListOf<OcrLayout.Box>()
        var y = 0
        while (y < h) {
            val th = min(tileH, h - y)
            val last = y + th >= h
            val tile = Bitmap.createBitmap(bmp, 0, y, w, th)
            val boxes = ocr(recognizer, tile, y.toFloat())
            if (tile !== bmp) tile.recycle()
            val coreTop = if (y == 0) Float.NEGATIVE_INFINITY else y + overlap / 2f
            val coreBottom = if (last) Float.POSITIVE_INFINITY else y + th - overlap / 2f
            out += boxes.filter { b -> b.corners.map { it.y }.average().let { it >= coreTop && it < coreBottom } }
            if (last) break
            y += th - overlap
        }
        return out
    }
}
