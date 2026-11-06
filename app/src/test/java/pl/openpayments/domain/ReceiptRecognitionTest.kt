package pl.openpayments.domain

import org.junit.Assert.*
import org.junit.Test

class ReceiptRecognitionTest {

    private fun r(raw: String) = ProductDictionary.resolve(raw)

    @Test fun expandsAbbreviationsAndRestoresDiacritics() {
        assertEquals("Ser żółty gouda plastry 150 g", r("SER ZOLT.GOUDA PLAS.150G").name)
        assertEquals(ProductGroup.DAIRY, r("SER ZOLT.GOUDA PLAS.150G").group)
        assertEquals("Kiełbasa wędzona", r("KIELB.WEDZONA").name.let { it.substringBefore(" ") + " " + it.substringAfter(" ") })
        assertEquals(ProductGroup.MEAT, r("KIELB. SLASKA").group)
        assertEquals("Śmietana 18% 400 g", r("SMIET. 18% 400G").name)
        assertEquals("Bułka kajzerka", r("BUL.KAJZ.").name)
        assertEquals(ProductGroup.BREAD, r("BUL.KAJZ.").group)
        assertEquals("Woda mineralna niegazowana 1,5 l", r("WODA MIN.NGAZ 1,5L").name)
        assertEquals(ProductGroup.DRINKS, r("WODA MIN.NGAZ 1,5L").group)
        assertEquals("Mleko 2% 1 l", r("MLK 2% 1L").name)
        assertEquals("Papier toaletowy 8 szt", r("PAP.TOAL 8SZT").name)
        assertEquals(ProductGroup.HOUSEHOLD, r("PAP.TOAL 8SZT").group)
        assertEquals(ProductGroup.PRODUCE, r("BANANY LUZ").group)
        assertEquals(ProductGroup.PACKAGING, r("TORBA PAPIEROWA").group)
        assertEquals(ProductGroup.SWEETS, r("CZEK.MLECZ.100G").group)
    }

    @Test fun handlesRealOcrQuirks() {
        assertEquals("Bułka kajzerka pszenny", r("BUL.KAJZ.PS ZEN.").name)
        assertEquals("Kiełbasa śląska 450 g", r("KIELB.SLASKA 450G").name)
        val res = ReceiptParser.parse(listOf("BANANY LUZ 0, 856 x5,99 5,13C", "SUMA PLN 5,13"))
        assertEquals("BANANY LUZ", res.items.single().rawName)
        assertEquals(0.856, res.items.single().quantity, 0.0001)
    }

    @Test fun unitAfterNumberIsNotTreatedAsAbbreviation() {
        assertEquals("Sok pomarańczowy 500 ml", r("SOK POMARANCZOWY 500 ML").name)
    }

    @Test fun learnedCorrectionWins() {
        val learned = mapOf(ProductDictionary.key("SEREK WIEJ.PIAT.") to "Serek wiejski Piątnica")
        val res = ProductDictionary.resolve("SEREK WIEJ.PIAT.", learned)
        assertEquals("Serek wiejski Piątnica", res.name)
        assertEquals(ProductGroup.DAIRY, res.group)
    }

    @Test fun fixesOcrMistakesInAmounts() {
        assertEquals("Chleb 4,90 A", ReceiptParser.fixOcr("Chleb 4,9O A"))
        assertEquals("Ser 12,50 C", ReceiptParser.fixOcr("Ser l2,5O C"))
        assertEquals("Mleko 1 x3,99 3,99C", ReceiptParser.fixOcr("Mleko 1 x3, 99 3,99C"))
    }

    @Test fun parsesBiedronkaWithTwoLineItemsAndDiscounts() {
        val lines = listOf(
            "JERONIMO MARTINS POLSKA S.A.",
            "Biedronka nr 1234",
            "PARAGON FISKALNY",
            "2026-10-12",
            "MLEKO LACIATE 2% 1L C 1 x3,99 3,99C",
            "SER ZOLT.GOUDA PLAS.150G",
            "1 szt * 7,49 = 7,49 C",
            "Rabat -1,50",
            "BANANY LUZ 0,856 x 5,99 5,13 C",
            "TORBA PAPIER. 1 x0,79 0,79A",
            "SPRZEDAZ OPODATK. C 16,61",
            "PTU C 5,00% 0,79",
            "SUMA PLN 15,90",
        )
        val res = ReceiptParser.parse(lines)
        assertEquals("Biedronka", res.merchant)
        assertEquals(1590L, res.total)
        assertEquals(4, res.items.size)
        assertEquals("SER ZOLT.GOUDA PLAS.150G", res.items[1].rawName)
        assertEquals(599L, res.items[1].price)       // 7,49 − 1,50 rabatu
        assertEquals(0.856, res.items[2].quantity, 0.0001)
        assertTrue(ReceiptParser.isConsistent(res))
    }
}
