package pl.openpayments.domain

import pl.openpayments.domain.DocumentDetector.Pt
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Odtwarza wiersze paragonu z linii zwróconych przez OCR.
 *
 * ML Kit zwraca nazwę towaru i cenę jako osobne linie (szeroka przerwa między kolumnami).
 * Żeby je skleić, liczymy dominujący kąt tekstu, „prostujemy” środki linii o ten kąt i dopiero
 * wtedy grupujemy je po wysokości. Dzięki temu lekko przekrzywione zdjęcie nie miesza
 * nazwy z jednego wiersza z ceną z sąsiedniego.
 */
object OcrLayout {

    /** Linia OCR z narożnikami: lewy górny, prawy górny, prawy dolny, lewy dolny. */
    data class Box(val text: String, val corners: List<Pt>) {
        val width: Double get() = (corners[0].dist(corners[1]) + corners[3].dist(corners[2])) / 2
        val height: Double get() = (corners[0].dist(corners[3]) + corners[1].dist(corners[2])) / 2
        /** Kąt górnej krawędzi w stopniach (0 = poziomo, 90 = tekst obrócony w prawo). */
        val angle: Double get() = Math.toDegrees(atan2((corners[1].y - corners[0].y).toDouble(), (corners[1].x - corners[0].x).toDouble()))
    }

    /** Dominujący kąt tekstu w stopniach (mediana, z przewagą długich linii). */
    fun dominantAngle(lines: List<Box>): Double {
        if (lines.isEmpty()) return 0.0
        val long = lines.filter { it.width > it.height * 2.5 && it.text.length >= 4 }
        val src = long.ifEmpty { lines }
        // Mediana kątów „na okręgu”: liczymy względem pierwszego, żeby ±180° się nie zerowały.
        val ref = src.first().angle
        val rel = src.map { normalize(it.angle - ref) }.sorted()
        return normalize(ref + rel[rel.size / 2])
    }

    private fun normalize(a: Double): Double {
        var x = a
        while (x > 180) x -= 360
        while (x <= -180) x += 360
        return x
    }

    fun rows(lines: List<Box>): List<String> {
        if (lines.isEmpty()) return emptyList()
        val theta = Math.toRadians(dominantAngle(lines).let { if (abs(it) > 45) 0.0 else it })
        val c = cos(theta); val s = sin(theta)

        data class L(val text: String, val y: Double, val left: Double, val right: Double, val h: Double)
        val ls = lines.filter { it.text.isNotBlank() }.map { b ->
            val xs = b.corners.map { it.x * c + it.y * s }
            val ys = b.corners.map { -it.x * s + it.y * c }
            L(b.text.trim(), ys.average(), xs.min(), xs.max(), max(1.0, b.height))
        }.sortedBy { it.y }
        if (ls.isEmpty()) return emptyList()
        val hs = ls.map { it.h }.sorted()
        val medianH = hs[hs.size / 2]

        class Row { val items = mutableListOf<L>(); var sumY = 0.0; val meanY get() = sumY / items.size }
        val rows = mutableListOf<Row>()
        for (l in ls) {
            var best: Row? = null; var bestD = Double.MAX_VALUE
            for (i in rows.indices.reversed()) {
                val r = rows[i]
                if (r.meanY < l.y - medianH * 2) break
                val d = abs(r.meanY - l.y)
                if (d > medianH * 0.55) continue
                // Dwie linie zachodzące na siebie w poziomie nie mogą być w tym samym wierszu.
                val overlaps = r.items.any { o ->
                    val ov = min(o.right, l.right) - max(o.left, l.left)
                    ov > 0.3 * min(o.right - o.left, l.right - l.left)
                }
                if (!overlaps && d < bestD) { bestD = d; best = r }
            }
            val row = best ?: Row().also { rows += it }
            row.items += l; row.sumY += l.y
        }
        return rows.sortedBy { it.meanY }.map { r -> r.items.sortedBy { it.left }.joinToString(" ") { it.text } }
    }
}
