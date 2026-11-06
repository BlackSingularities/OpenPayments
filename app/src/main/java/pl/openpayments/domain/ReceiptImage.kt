package pl.openpayments.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Operacje na bitmapach: wczytanie z obrotem z EXIF, wykrycie i wyprostowanie paragonu, poprawa obrazu. */
object ReceiptImage {

    private const val MAX_INPUT_PIXELS = 16_000_000
    private const val MAX_OUTPUT_PIXELS = 8_000_000
    /** Szerokość paragonu po wyprostowaniu: ~40 znaków w wierszu → ok. 30–40 px na znak, w sam raz dla ML Kit. */
    private const val TARGET_WIDTH = 1300
    private const val MAX_WIDTH = 1700

    fun load(ctx: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth.toLong() * bounds.outHeight / (sample.toLong() * sample) > MAX_INPUT_PIXELS) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("Nie można odczytać zdjęcia")
        val rotation = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { s ->
                when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
                    else -> 0
                }
            }
        }.getOrNull() ?: 0
        return rotate(bmp, rotation)
    }

    fun rotate(bmp: Bitmap, degrees: Int): Bitmap {
        val d = ((degrees % 360) + 360) % 360
        if (d == 0) return bmp
        val m = Matrix().apply { postRotate(d.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    /** Szuka paragonu na pomniejszonej kopii; zwraca czworokąt we współrzędnych pełnej bitmapy. */
    fun detect(bmp: Bitmap): DocumentDetector.Quad? {
        val scale = 520f / max(bmp.width, bmp.height)
        val sw = max(16, (bmp.width * scale).roundToInt())
        val sh = max(16, (bmp.height * scale).roundToInt())
        val small = Bitmap.createScaledBitmap(bmp, sw, sh, true)
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)
        if (small !== bmp) small.recycle()
        val q = DocumentDetector.detect(px, sw, sh) ?: return null
        return q.scale(bmp.width / sw.toFloat(), bmp.height / sh.toFloat())
    }

    /** Wycina czworokąt i prostuje perspektywę do prostokąta o szerokości dobranej pod OCR. */
    fun warp(bmp: Bitmap, q: DocumentDetector.Quad): Bitmap? {
        val w0 = q.outWidth; val h0 = q.outHeight
        if (w0 < 40 || h0 < 40) return null
        var s = when {
            w0 < TARGET_WIDTH -> TARGET_WIDTH / w0
            w0 > MAX_WIDTH -> MAX_WIDTH / w0
            else -> 1.0
        }
        if (w0 * h0 * s * s > MAX_OUTPUT_PIXELS) s = sqrt(MAX_OUTPUT_PIXELS / (w0 * h0))
        val w = max(1, (w0 * s).roundToInt()); val h = max(1, (h0 * s).roundToInt())
        val src = floatArrayOf(q.tl.x, q.tl.y, q.tr.x, q.tr.y, q.br.x, q.br.y, q.bl.x, q.bl.y)
        val dst = floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat())
        val m = Matrix()
        if (!m.setPolyToPoly(src, 0, dst, 0, 4)) return null
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(bmp, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG))
        }
        return out
    }

    /** Skaluje do szerokości odpowiedniej dla OCR (np. wynik skanera dokumentów albo zdjęcie bez wykrytych krawędzi). */
    fun normalize(bmp: Bitmap): Bitmap {
        val w0 = bmp.width.toDouble(); val h0 = bmp.height.toDouble()
        var s = when {
            w0 < TARGET_WIDTH * 0.8 && h0 > w0 -> TARGET_WIDTH / w0
            w0 > MAX_WIDTH * 1.4 && h0 > w0 -> MAX_WIDTH / w0
            else -> 1.0
        }
        if (w0 * h0 * s * s > MAX_OUTPUT_PIXELS) s = min(s, sqrt(MAX_OUTPUT_PIXELS / (w0 * h0)))
        if (s in 0.95..1.05) return bmp
        return Bitmap.createScaledBitmap(bmp, max(1, (w0 * s).roundToInt()), max(1, (h0 * s).roundToInt()), true)
    }

    /** Szara kopia z wyrównanym oświetleniem i podbitym kontrastem. */
    fun enhance(bmp: Bitmap): Bitmap {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        DocumentDetector.flattenIllumination(px, w, h)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    fun saveJpeg(bmp: Bitmap, file: File) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
    }
}
