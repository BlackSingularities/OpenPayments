package pl.openpayments.domain

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Rozpoznawanie tekstu na urządzeniu (ML Kit, offline — zdjęcie nie opuszcza telefonu).
 * ML Kit zwraca bloki kolumnami, więc nazwę towaru i cenę z tego samego wiersza
 * scalamy po współrzędnej Y, żeby odtworzyć wiersze paragonu.
 */
object ReceiptScanner {

    suspend fun recognize(ctx: Context, uri: Uri): List<String> {
        val image = InputImage.fromFilePath(ctx, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = recognizer.process(image).await()
            data class L(val text: String, val top: Int, val bottom: Int, val left: Int)
            val lines = text.textBlocks.flatMap { b -> b.lines }.mapNotNull { l ->
                val r = l.boundingBox ?: return@mapNotNull null
                L(l.text, r.top, r.bottom, r.left)
            }.sortedBy { it.top }

            val rows = mutableListOf<MutableList<L>>()
            for (l in lines) {
                val row = rows.lastOrNull()
                val ref = row?.first()
                if (ref != null) {
                    val h = (ref.bottom - ref.top).coerceAtLeast(1)
                    val centerA = (ref.top + ref.bottom) / 2
                    val centerB = (l.top + l.bottom) / 2
                    if (kotlin.math.abs(centerA - centerB) < h * 0.6) { row.add(l); continue }
                }
                rows += mutableListOf(l)
            }
            return rows.map { r -> r.sortedBy { it.left }.joinToString(" ") { it.text } }
        } finally {
            recognizer.close()
        }
    }
}
