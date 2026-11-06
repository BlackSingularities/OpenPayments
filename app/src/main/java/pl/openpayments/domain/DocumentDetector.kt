package pl.openpayments.domain

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Wykrywanie paragonu na zdjęciu i przygotowanie obrazu do OCR. Czysty Kotlin (bez Androida),
 * żeby dało się to testować na JVM.
 *
 * Paragon to jasny, prawie szary (mało nasycony) czworokąt na zwykle ciemniejszym lub
 * kolorowym tle. Szukamy go tak:
 *  1. ocena „papierowości” piksela: jasność minus nasycenie (zielone napisy na podkładce odpadają),
 *  2. próg Otsu, potem domknięcie (zalewa ciemny druk na papierze) i otwarcie (usuwa cienkie
 *     jasne napisy tła),
 *  3. największa spójna plama blisko środka, z wypełnionymi dziurami,
 *  4. otoczka wypukła → uproszczenie → czworokąt o największym polu wpisany w otoczkę,
 *  5. kontrola jakości: plama musi wypełniać czworokąt, inaczej nie przycinamy wcale.
 */
object DocumentDetector {

    data class Pt(val x: Float, val y: Float) {
        operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
        fun dist(o: Pt) = hypot((x - o.x).toDouble(), (y - o.y).toDouble())
    }

    /** Narożniki w kolejności: lewy górny, prawy górny, prawy dolny, lewy dolny. */
    data class Quad(val tl: Pt, val tr: Pt, val br: Pt, val bl: Pt) {
        val points: List<Pt> get() = listOf(tl, tr, br, bl)
        val area: Double get() = polygonArea(points)
        /** Szerokość i wysokość prostokąta wyjściowego (dłuższe z przeciwległych boków). */
        val outWidth: Double get() = max(tl.dist(tr), bl.dist(br))
        val outHeight: Double get() = max(tl.dist(bl), tr.dist(br))
        fun scale(sx: Float, sy: Float) = Quad(
            Pt(tl.x * sx, tl.y * sy), Pt(tr.x * sx, tr.y * sy), Pt(br.x * sx, br.y * sy), Pt(bl.x * sx, bl.y * sy),
        )
    }

    /**
     * @param argb piksele ARGB pomniejszonego zdjęcia (najlepiej ~500 px dłuższego boku)
     * @return czworokąt paragonu we współrzędnych tej mapy albo null, gdy nie ma czego przycinać
     *         (paragon wypełnia kadr, tło jest równie jasne, wynik niepewny).
     */
    fun detect(argb: IntArray, w: Int, h: Int): Quad? {
        val n = w * h
        if (w < 16 || h < 16 || argb.size < n) return null

        val score = IntArray(n)
        for (i in 0 until n) {
            val c = argb[i]
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            val lum = (r * 299 + g * 587 + b * 114) / 1000
            val sat = max(r, max(g, b)) - min(r, min(g, b))
            score[i] = (lum - sat).coerceIn(0, 255)
        }
        val (t, separation) = otsu(score)
        if (separation < 28) return null // brak wyraźnej różnicy papier/tło

        var mask = BooleanArray(n) { score[it] > t }
        val rad = max(1, min(w, h) / 120)
        mask = erode(dilate(mask, w, h, rad), w, h, rad)            // domknięcie: druk na papierze
        mask = dilate(erode(mask, w, h, rad + 1), w, h, rad + 1)    // otwarcie: cienkie napisy tła

        val comp = bestComponent(mask, w, h) ?: return null
        fillHoles(comp, w, h)
        val area = comp.count { it }
        if (area < n * 0.03 || area > n * 0.96) return null

        val pts = ArrayList<Pt>(h * 2)
        for (y in 0 until h) {
            var lo = -1; var hi = -1
            val row = y * w
            for (x in 0 until w) if (comp[row + x]) { if (lo < 0) lo = x; hi = x }
            if (lo >= 0) { pts += Pt(lo.toFloat(), y.toFloat()); pts += Pt(hi + 1f, y.toFloat()); pts += Pt(lo.toFloat(), y + 1f); pts += Pt(hi + 1f, y + 1f) }
        }
        val hull = convexHull(pts)
        if (hull.size < 4) return null
        val simple = simplify(hull, 18)
        val quadPts = maxAreaQuad(simple) ?: return null
        val quad = order(quadPts)

        // Kontrola: plama ma wypełniać czworokąt, a czworokąt ma mieć sensowny kształt.
        val qa = quad.area
        if (qa <= 0 || area / qa < 0.82 || qa / polygonArea(hull) < 0.85) return null
        if (min(quad.outWidth, quad.outHeight) < min(w, h) * 0.08) return null
        if (!isConvex(quad.points)) return null
        // Paragon praktycznie na cały kadr — nie ma sensu ciąć.
        if (qa > n * 0.93) return null

        return expand(quad, 0.012f, w, h)
    }

    // ---------- przygotowanie obrazu do OCR ----------

    /**
     * Wyrównuje oświetlenie (cienie, zagięcia, pożółkły papier termiczny) i podbija kontrast.
     * Tło szacujemy maksimum w blokach (ciemny druk znika) + wygładzenie, a potem dzielimy przez nie
     * każdy piksel. Działa w miejscu: [argb] dostaje odcienie szarości.
     */
    fun flattenIllumination(argb: IntArray, w: Int, h: Int) {
        val n = w * h
        for (i in 0 until n) {
            val c = argb[i]
            argb[i] = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        }
        val block = max(4, w / 36)
        val sw = ceil(w / block.toDouble()).toInt()
        val sh = ceil(h / block.toDouble()).toInt()
        var bg = IntArray(sw * sh)
        for (y in 0 until h) {
            val by = (y / block) * sw
            val row = y * w
            for (x in 0 until w) {
                val v = argb[row + x]
                val k = by + x / block
                if (v > bg[k]) bg[k] = v
            }
        }
        bg = maxFilter(bg, sw, sh, 2)
        bg = boxBlur(bg, sw, sh, 2)

        val hist = IntArray(256)
        for (y in 0 until h) {
            val fy = ((y + 0.5f) / block - 0.5f).coerceIn(0f, (sh - 1).toFloat())
            val y0 = fy.toInt(); val y1 = min(y0 + 1, sh - 1); val ty = fy - y0
            val row = y * w
            for (x in 0 until w) {
                val fx = ((x + 0.5f) / block - 0.5f).coerceIn(0f, (sw - 1).toFloat())
                val x0 = fx.toInt(); val x1 = min(x0 + 1, sw - 1); val tx = fx - x0
                val top = bg[y0 * sw + x0] * (1 - tx) + bg[y0 * sw + x1] * tx
                val bot = bg[y1 * sw + x0] * (1 - tx) + bg[y1 * sw + x1] * tx
                val b = max(24f, top * (1 - ty) + bot * ty)
                val v = (argb[row + x] * 255f / b).toInt().coerceIn(0, 255)
                argb[row + x] = v
                hist[v]++
            }
        }
        // Rozciągnięcie: najciemniejszy 1% → czarny, papier (≈ 96%) → biały.
        val lo = percentile(hist, n, 0.01)
        val hi = max(lo + 40, min(250, percentile(hist, n, 0.60)))
        val lut = IntArray(256) { v -> (((v - lo) * 255f) / (hi - lo)).toInt().coerceIn(0, 255) }
        for (i in 0 until n) {
            val g = lut[argb[i]]
            argb[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
    }

    // ---------- geometria ----------

    fun polygonArea(p: List<Pt>): Double {
        var s = 0.0
        for (i in p.indices) {
            val a = p[i]; val b = p[(i + 1) % p.size]
            s += a.x.toDouble() * b.y - b.x.toDouble() * a.y
        }
        return abs(s) / 2
    }

    /** Otoczka wypukła (monotone chain). */
    fun convexHull(points: List<Pt>): List<Pt> {
        val p = points.distinct().sortedWith(compareBy<Pt>({ it.x }, { it.y }))
        if (p.size < 3) return p
        fun cross(o: Pt, a: Pt, b: Pt) = (a.x - o.x).toDouble() * (b.y - o.y) - (a.y - o.y).toDouble() * (b.x - o.x)
        val lower = ArrayList<Pt>()
        for (pt in p) { while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), pt) <= 0) lower.removeAt(lower.lastIndex); lower += pt }
        val upper = ArrayList<Pt>()
        for (pt in p.asReversed()) { while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), pt) <= 0) upper.removeAt(upper.lastIndex); upper += pt }
        lower.removeAt(lower.lastIndex); upper.removeAt(upper.lastIndex)
        return lower + upper
    }

    /** Upraszcza wielokąt (Visvalingam): usuwa wierzchołki, które najmniej zmieniają pole. */
    private fun simplify(poly: List<Pt>, maxPoints: Int): List<Pt> {
        val p = poly.toMutableList()
        fun tri(a: Pt, b: Pt, c: Pt) = abs((b.x - a.x).toDouble() * (c.y - a.y) - (c.x - a.x).toDouble() * (b.y - a.y)) / 2
        while (p.size > maxPoints) {
            var best = 0; var bestA = Double.MAX_VALUE
            for (i in p.indices) {
                val a = tri(p[(i - 1 + p.size) % p.size], p[i], p[(i + 1) % p.size])
                if (a < bestA) { bestA = a; best = i }
            }
            p.removeAt(best)
        }
        return p
    }

    /** Czworokąt o największym polu z wierzchołków wielokąta wypukłego (brute force, n ≤ ~20). */
    private fun maxAreaQuad(p: List<Pt>): List<Pt>? {
        if (p.size < 4) return null
        var best: List<Pt>? = null; var bestA = -1.0
        for (i in 0 until p.size) for (j in i + 1 until p.size) for (k in j + 1 until p.size) for (l in k + 1 until p.size) {
            val q = listOf(p[i], p[j], p[k], p[l])
            val a = polygonArea(q)
            if (a > bestA) { bestA = a; best = q }
        }
        return best
    }

    /** Ustala kolejność narożników: zgodnie z ruchem wskazówek, zaczynając od lewego górnego. */
    fun order(p: List<Pt>): Quad {
        val cx = p.sumOf { it.x.toDouble() } / p.size
        val cy = p.sumOf { it.y.toDouble() } / p.size
        val sorted = p.sortedBy { atan2(it.y - cy, it.x - cx) }
        val start = sorted.indices.minBy { sorted[it].x + sorted[it].y }
        val r = List(4) { sorted[(start + it) % 4] }
        return Quad(r[0], r[1], r[2], r[3])
    }

    private fun isConvex(p: List<Pt>): Boolean {
        var sign = 0
        for (i in p.indices) {
            val a = p[i]; val b = p[(i + 1) % p.size]; val c = p[(i + 2) % p.size]
            val cr = (b.x - a.x).toDouble() * (c.y - b.y) - (b.y - a.y).toDouble() * (c.x - b.x)
            val s = if (cr > 0) 1 else if (cr < 0) -1 else 0
            if (s != 0) { if (sign == 0) sign = s else if (s != sign) return false }
        }
        return sign != 0
    }

    /** Lekko poszerza czworokąt od środka, żeby nie uciąć znaków przy krawędzi papieru. */
    private fun expand(q: Quad, f: Float, w: Int, h: Int): Quad {
        val cx = q.points.map { it.x }.average().toFloat()
        val cy = q.points.map { it.y }.average().toFloat()
        fun e(p: Pt) = Pt((cx + (p.x - cx) * (1 + f)).coerceIn(0f, w.toFloat()), (cy + (p.y - cy) * (1 + f)).coerceIn(0f, h.toFloat()))
        return Quad(e(q.tl), e(q.tr), e(q.br), e(q.bl))
    }

    // ---------- operacje na maskach i mapach ----------

    /** Próg Otsu i odległość między średnimi klas (miara kontrastu). */
    private fun otsu(v: IntArray): Pair<Int, Int> {
        val hist = IntArray(256)
        for (x in v) hist[x]++
        val total = v.size.toDouble()
        var sum = 0.0
        for (i in 0..255) sum += i.toDouble() * hist[i]
        var sumB = 0.0; var wB = 0.0; var best = 0.0; var t = 127; var sep = 0
        for (i in 0..255) {
            wB += hist[i]
            if (wB == 0.0) continue
            val wF = total - wB
            if (wF == 0.0) break
            sumB += i.toDouble() * hist[i]
            val mB = sumB / wB; val mF = (sum - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; t = i; sep = (mF - mB).toInt() }
        }
        return t to sep
    }

    private fun percentile(hist: IntArray, n: Int, p: Double): Int {
        val target = (n * p).toLong()
        var acc = 0L
        for (i in 0..255) { acc += hist[i]; if (acc > target) return i }
        return 255
    }

    /** Liczy zapalone piksele w oknie [x−r, x+r] w poziomie i pionie (separowalnie). */
    private fun window(mask: BooleanArray, w: Int, h: Int, r: Int, all: Boolean): BooleanArray {
        val tmp = BooleanArray(w * h)
        val pre = IntArray(max(w, h) + 1)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) pre[x + 1] = pre[x] + if (mask[row + x]) 1 else 0
            for (x in 0 until w) {
                val a = max(0, x - r); val b = min(w, x + r + 1)
                val c = pre[b] - pre[a]
                tmp[row + x] = if (all) c == b - a else c > 0
            }
        }
        val out = BooleanArray(w * h)
        for (x in 0 until w) {
            for (y in 0 until h) pre[y + 1] = pre[y] + if (tmp[y * w + x]) 1 else 0
            for (y in 0 until h) {
                val a = max(0, y - r); val b = min(h, y + r + 1)
                val c = pre[b] - pre[a]
                out[y * w + x] = if (all) c == b - a else c > 0
            }
        }
        return out
    }

    private fun erode(m: BooleanArray, w: Int, h: Int, r: Int) = window(m, w, h, r, all = true)
    private fun dilate(m: BooleanArray, w: Int, h: Int, r: Int) = window(m, w, h, r, all = false)

    /** Największa spójna plama, z premią za położenie blisko środka kadru. */
    private fun bestComponent(mask: BooleanArray, w: Int, h: Int): BooleanArray? {
        val label = IntArray(w * h)
        val queue = IntArray(w * h)
        var next = 0; var bestLabel = 0; var bestScore = 0.0
        for (s in mask.indices) {
            if (!mask[s] || label[s] != 0) continue
            next++
            var head = 0; var tail = 0
            queue[tail++] = s; label[s] = next
            var sx = 0L; var sy = 0L
            while (head < tail) {
                val p = queue[head++]
                val x = p % w; val y = p / w
                sx += x; sy += y
                if (x > 0 && mask[p - 1] && label[p - 1] == 0) { label[p - 1] = next; queue[tail++] = p - 1 }
                if (x < w - 1 && mask[p + 1] && label[p + 1] == 0) { label[p + 1] = next; queue[tail++] = p + 1 }
                if (y > 0 && mask[p - w] && label[p - w] == 0) { label[p - w] = next; queue[tail++] = p - w }
                if (y < h - 1 && mask[p + w] && label[p + w] == 0) { label[p + w] = next; queue[tail++] = p + w }
            }
            val dx = (sx.toDouble() / tail) / w - 0.5
            val dy = (sy.toDouble() / tail) / h - 0.5
            val score = tail * (1.0 - 0.6 * hypot(dx, dy))
            if (score > bestScore) { bestScore = score; bestLabel = next }
        }
        if (bestLabel == 0) return null
        return BooleanArray(w * h) { label[it] == bestLabel }
    }

    /** Wypełnia dziury w plamie: wszystko, czego nie da się osiągnąć od krawędzi kadru. */
    private fun fillHoles(comp: BooleanArray, w: Int, h: Int) {
        val outside = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var tail = 0
        fun seed(p: Int) { if (!comp[p] && !outside[p]) { outside[p] = true; queue[tail++] = p } }
        for (x in 0 until w) { seed(x); seed((h - 1) * w + x) }
        for (y in 0 until h) { seed(y * w); seed(y * w + w - 1) }
        var head = 0
        while (head < tail) {
            val p = queue[head++]
            val x = p % w; val y = p / w
            if (x > 0) seed(p - 1)
            if (x < w - 1) seed(p + 1)
            if (y > 0) seed(p - w)
            if (y < h - 1) seed(p + w)
        }
        for (i in comp.indices) if (!outside[i]) comp[i] = true
    }

    private fun maxFilter(v: IntArray, w: Int, h: Int, r: Int): IntArray {
        val tmp = IntArray(w * h); val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0
            for (k in max(0, x - r)..min(w - 1, x + r)) m = max(m, v[y * w + k])
            tmp[y * w + x] = m
        }
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0
            for (k in max(0, y - r)..min(h - 1, y + r)) m = max(m, tmp[k * w + x])
            out[y * w + x] = m
        }
        return out
    }

    private fun boxBlur(v: IntArray, w: Int, h: Int, r: Int): IntArray {
        val tmp = IntArray(w * h); val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0; var c = 0
            for (k in max(0, x - r)..min(w - 1, x + r)) { s += v[y * w + k]; c++ }
            tmp[y * w + x] = s / c
        }
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0; var c = 0
            for (k in max(0, y - r)..min(h - 1, y + r)) { s += tmp[k * w + x]; c++ }
            out[y * w + x] = s / c
        }
        return out
    }
}
