package pl.openpayments.domain

import org.junit.Assert.*
import org.junit.Test
import pl.openpayments.data.Category
import pl.openpayments.data.CategoryRule
import java.time.LocalDate

class DomainTest {

    private val lidl = listOf(
        "LIDL sp. z o.o. sp.k.",
        "ul. Poznańska 48, Jankowice",
        "NIP 781-18-97-358",
        "2026-08-17 18:42",
        "PARAGON FISKALNY",
        "Mleko 2% 1L 1 x4,99 4,99C",
        "Chleb zytni 1 x6,50 6,50C",
        "Jablka luz 1,000 x5,99 5,99C",
        "Maslo extra 2 x7,49 14,98C",
        "SPRZEDAZ OPODATK. C 32,46",
        "PTU C 5,00% 1,55",
        "SUMA PLN 32,46",
        "Karta 32,46",
    )

    @Test fun parsesLidlReceipt() {
        val r = ReceiptParser.parse(lidl)
        assertEquals("Lidl", r.merchant)
        assertEquals(LocalDate.of(2026, 8, 17).toEpochDay(), r.date)
        assertEquals(3246L, r.total)
        assertEquals(4, r.items.size)
        assertEquals("Maslo extra", r.items[3].name)
        assertEquals(2.0, r.items[3].quantity, 0.0)
        assertEquals(1498L, r.items[3].price)
        assertTrue(ReceiptParser.isConsistent(r))
    }

    @Test fun parsesPolishDateAndSimpleItems() {
        val r = ReceiptParser.parse(listOf("Sklep u Ani", "17.08.2026", "Bulka 1,20 A", "Woda 2,30 B", "SUMA 3,50"))
        assertEquals("Sklep u ani", r.merchant)
        assertEquals(LocalDate.of(2026, 8, 17).toEpochDay(), r.date)
        assertEquals(350L, r.total)
        assertEquals(2, r.items.size)
    }

    @Test fun money() {
        assertEquals(123450L, parseMoney("1 234,50"))
        assertEquals(1250L, parseMoney("12.5"))
        assertNull(parseMoney("abc"))
        assertEquals("2 840,50 zł", formatMoney(284050))
    }

    @Test fun categorizerPrefersLearnedRules() {
        val cats = listOf(Category(1, "Zakupy spożywcze", "cart", 0), Category(2, "Dom", "home", 0))
        assertEquals(1L, Categorizer.suggest("LIDL Sp. z o.o.", emptyList(), cats))
        assertEquals(2L, Categorizer.suggest("LIDL Sp. z o.o.", listOf(CategoryRule(keyword = "lidl", categoryId = 2)), cats))
        assertNull(Categorizer.suggest("Nieznany sklep", emptyList(), cats))
        assertEquals("lidl", Categorizer.ruleKey("LIDL sp. z o.o."))
    }
}
