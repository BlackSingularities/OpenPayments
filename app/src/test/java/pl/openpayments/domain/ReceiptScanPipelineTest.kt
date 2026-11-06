package pl.openpayments.domain

import org.junit.Assert.*
import org.junit.Test
import pl.openpayments.domain.DocumentDetector.Pt
import kotlin.math.cos
import kotlin.math.sin

/** Kadrowanie zdjęcia, składanie wierszy z OCR i paragon z Biedronki w układzie z prawdziwego zdjęcia. */
class ReceiptScanPipelineTest {

    // ---------- wykrywanie paragonu ----------

    private fun insideQuad(x: Double, y: Double, q: List<Pt>): Boolean {
        var sign = 0
        for (i in q.indices) {
            val a = q[i]; val b = q[(i + 1) % q.size]
            val cr = (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x)
            val s = if (cr > 0) 1 else if (cr < 0) -1 else 0
            if (s != 0) { if (sign == 0) sign = s else if (s != sign) return false }
        }
        return true
    }

    @Test fun detectsTiltedReceiptOnDarkClutteredBackground() {
        val w = 400; val h = 300
        val truth = listOf(Pt(150f, 30f), Pt(260f, 50f), Pt(230f, 280f), Pt(120f, 260f))
        val px = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            when {
                insideQuad(x + 0.5, y + 0.5, truth) ->
                    // papier z ciemnymi wierszami druku co 9 px
                    if (y % 9 < 2 && x % 40 < 30) 0xFF303030.toInt() else 0xFFEDEDE8.toInt()
                // podkładka: ciemne tło z zielonymi i cienkimi białymi napisami
                y % 23 < 2 && x < 110 -> 0xFFF0F0F0.toInt()
                x % 31 < 3 && y > 200 -> 0xFF2EAD4B.toInt()
                else -> 0xFF22252A.toInt()
            }
        }
        val q = DocumentDetector.detect(px, w, h)
        assertNotNull("paragon powinien zostać wykryty", q)
        q!!
        val found = listOf(q.tl, q.tr, q.br, q.bl)
        for ((a, b) in truth.zip(found)) assertTrue("narożnik $b daleko od $a", a.dist(b) < 12.0)
    }

    @Test fun noCropWhenNothingStandsOut() {
        val px = IntArray(200 * 150) { 0xFF808080.toInt() }
        assertNull(DocumentDetector.detect(px, 200, 150))
    }

    @Test fun ordersCornersClockwiseFromTopLeft() {
        val q = DocumentDetector.order(listOf(Pt(10f, 100f), Pt(100f, 100f), Pt(10f, 0f), Pt(100f, 0f)))
        assertEquals(Pt(10f, 0f), q.tl); assertEquals(Pt(100f, 0f), q.tr)
        assertEquals(Pt(100f, 100f), q.br); assertEquals(Pt(10f, 100f), q.bl)
    }

    @Test fun flattensShadowSoTextStaysDarkAndPaperWhite() {
        val w = 240; val h = 120
        // papier z cieniem rosnącym w prawo (jasność 230 → 110) i ciemnym tekstem
        val px = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val paper = 230 - x / 2
            val v = if (y in 50..56 && x % 12 < 8) paper / 4 else paper
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        DocumentDetector.flattenIllumination(px, w, h)
        fun g(x: Int, y: Int) = px[y * w + x] and 0xFF
        assertTrue("papier w cieniu ma być jasny", g(230, 10) > 200)
        assertTrue("papier w świetle ma być jasny", g(10, 10) > 200)
        assertTrue("tekst w cieniu ma być ciemny", g(229, 53) < 90)
    }

    // ---------- składanie wierszy ----------

    private fun box(text: String, x: Float, y: Float, w: Float, h: Float, deg: Double): OcrLayout.Box {
        val t = Math.toRadians(deg); val c = cos(t).toFloat(); val s = sin(t).toFloat()
        fun r(px: Float, py: Float) = Pt(px * c - py * s, px * s + py * c)
        return OcrLayout.Box(text, listOf(r(x, y), r(x + w, y), r(x + w, y + h), r(x, y + h)))
    }

    @Test fun joinsNameAndPriceOnSkewedPhoto() {
        // Przy 5° cena po prawej „spada” o prawie cały wiersz — bez prostowania trafiłaby do złej nazwy.
        val deg = 5.0
        val boxes = listOf(
            box("Mleko UHT", 10f, 100f, 190f, 24f, deg), box("3,99 C", 400f, 100f, 80f, 24f, deg),
            box("Chleb żytni", 10f, 140f, 190f, 24f, deg), box("4,50 C", 400f, 140f, 80f, 24f, deg),
            box("Masło extra", 10f, 180f, 190f, 24f, deg), box("7,99 C", 400f, 180f, 80f, 24f, deg),
        ).shuffled(java.util.Random(7))
        assertEquals(5.0, OcrLayout.dominantAngle(boxes), 0.5)
        assertEquals(listOf("Mleko UHT 3,99 C", "Chleb żytni 4,50 C", "Masło extra 7,99 C"), OcrLayout.rows(boxes))
    }

    @Test fun detectsSidewaysText() {
        val boxes = listOf(box("Mleko UHT", 10f, 100f, 190f, 24f, 90.0), box("Chleb żytni", 10f, 140f, 190f, 24f, 90.0))
        assertEquals(90.0, OcrLayout.dominantAngle(boxes), 1.0)
    }

    // ---------- paragon z Biedronki ----------

    private val biedronka = listOf(
        "Biedronko",
        "Sklep 4121 ul. Łaska 23/25, 95-200 Pabianice",
        "Jeronimo Martins Polska S.A.",
        "NIP 779-10-11-327",
        "2026-10-23 16:41 578686",
        "PARAGON FISKALNY",
        "Mleko UHT 1,5 1L C 4 x2,29 9,16C",
        "Rabat -1,20",
        "7,96C",
        "Jaja W Wyb M10szt C 2 x13,99 27,98C",
        "Rabat",
        "- 9,32",
        "18,66C",
        "NaPepsStrauCre0,33L A 1 x2,99 2,99A",
        "MullMilchCzek 400g C",
        "3 x3,95 11,85C",
        "Rabat -2,97",
        "8,88C",
        "PrażynkiMix110-120g C 1 x6,79 6,79C",
        "BułecŚniad400g C 2 x10,49 20,98C",
        "Rabat -5,49 15,49C",
        "Karkówk B K WAC kg C 1,320 x13,45 17,75C",
        "SPRZEDAŻ OPODATKOWANA A 2,99",
        "PTU A 23,00 % 0,56",
        "SPRZEDAŻ OPODATKOWANA C 75,53",
        "SUMA PTU 4,16",
        "SUMA PLN 78,52",
        "Karta płatnicza 78,52",
    )

    @Test fun parsesBiedronkaWithDiscountedPricesOnSeparateLines() {
        val r = ReceiptParser.parse(biedronka)
        assertEquals("Biedronka", r.merchant)
        assertEquals(7852L, r.total)
        assertEquals(listOf(796L, 1866L, 299L, 888L, 679L, 1549L, 1775L), r.items.map { it.price })
        assertEquals("MullMilchCzek 400g", r.items[3].rawName)
        assertEquals(1.32, r.items[6].quantity, 0.0001)
        assertTrue(ReceiptParser.isConsistent(r))
    }

    @Test fun recognizesStoreDespiteOcrTypo() {
        val r = ReceiptParser.parse(listOf("Biedronko", "PARAGON FISKALNY", "Chleb 4,50 C", "SUMA PLN 4,50"))
        assertEquals("Biedronka", r.merchant)
    }

    @Test fun totalOnNextLineAndTaxFallback() {
        assertEquals(450L, ReceiptParser.parse(listOf("PARAGON FISKALNY", "Chleb 4,50 C", "SUMA PLN", "4,50")).total)
        val r = ReceiptParser.parse(listOf(
            "PARAGON FISKALNY", "Chleb 4,50 C", "Woda 2,00 A",
            "SPRZEDAZ OPODATKOWANA A 2,00", "SPRZEDAZ OPODATKOWANA C 4,50",
        ))
        assertEquals(650L, r.total)
        assertTrue(ReceiptParser.isConsistent(r))
    }

    @Test fun quantityWithoutNameIsNotAnItem() {
        val r = ReceiptParser.parse(listOf("SER GOUDA", "1 szt * 7,49 = 7,49 C", "SUMA PLN 7,49"))
        assertEquals("SER GOUDA", r.items.single().rawName)
    }

    // ---------- nazwy pisane łącznie ----------

    @Test fun expandsCamelCaseNames() {
        val milk = ProductDictionary.resolve("MullMilchCzek 400g")
        assertEquals("Müller Milch czekolada 400 g", milk.name)
        assertEquals(ProductGroup.DAIRY, milk.group)

        val pepsi = ProductDictionary.resolve("NaPepsiCreamSo0,33L")
        assertTrue(pepsi.name, pepsi.name.startsWith("Napój Pepsi"))
        assertTrue(pepsi.name, pepsi.name.endsWith("0,33 l"))
        assertEquals(ProductGroup.DRINKS, pepsi.group)

        assertEquals("Jaja z wolnego wybiegu M 10 szt", ProductDictionary.resolve("Jaja W Wyb M10szt").name)
        val rolls = ProductDictionary.resolve("BułecŚniad400g")
        assertEquals("Bułeczki śniadaniowe 400 g", rolls.name)
        assertEquals(ProductGroup.BREAD, rolls.group)
    }

    @Test fun splitCamelKeepsAllCapsAndMultipacks() {
        assertEquals("SER ZOLT", ProductDictionary.splitCamel("SER ZOLT"))
        assertEquals("Serek Hom Danio 4x150g", ProductDictionary.splitCamel("SerekHomDanio4x150g"))
        // Przypadkowa wielka litera z OCR („MulL”) nie rozbija słowa na jednoliterowe kawałki.
        assertEquals("MulL Milch", ProductDictionary.splitCamel("MulLMilch"))
    }
}
