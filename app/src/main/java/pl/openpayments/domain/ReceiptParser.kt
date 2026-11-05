package pl.openpayments.domain

import java.time.LocalDate
import kotlin.math.abs

/**
 * Parser tekstu z polskiego paragonu fiskalnego (wynik OCR).
 * Wyciąga: sprzedawcę, datę, sumę oraz listę pozycji.
 *
 * Typowa pozycja paragonu: "Mleko 2% 1L    1 x4,99   4,99 C"
 * albo bez ilości: "Chleb zytni      6,50 A".
 */
object ReceiptParser {

    data class Item(val name: String, val quantity: Double, val price: Long)
    data class Result(
        val merchant: String?,
        val date: Long?,
        val total: Long?,
        val items: List<Item>,
    ) {
        val itemsSum: Long get() = items.sumOf { it.price }
    }

    private val amount = """(\d{1,5}[.,]\d{2})"""
    private val itemWithQty = Regex(
        """^(.{2,}?)\s+(\d+(?:[.,]\d{1,3})?)\s*(?:szt\.?|kg|l)?\s*[x×*]\s*$amount\s*=?\s*$amount\s*[A-G]?$""",
        RegexOption.IGNORE_CASE,
    )
    private val itemSimple = Regex("""^(.{2,}?)\s+$amount\s*[A-G]$""", RegexOption.IGNORE_CASE)
    private val totalLine = Regex("""(SUMA|RAZEM|DO ZAP[ŁL]ATY|SUMA PLN|TOTAL)\D*$amount""", RegexOption.IGNORE_CASE)
    private val dateIso = Regex("""(20\d{2})[-./](\d{2})[-./](\d{2})""")
    private val datePl = Regex("""(\d{2})[-./](\d{2})[-./](20\d{2})""")

    private val stopWords = listOf(
        "PTU", "SPRZEDA", "PARAGON", "FISKALNY", "NIP", "KASA", "KASJER", "RESZTA", "GOTÓWKA", "GOTOWKA",
        "KARTA", "RABAT", "SUMA", "RAZEM", "ZAPŁATY", "ZAPLATY", "NR SYS", "NR WYDR", "OPODATK", "WPŁATA",
    )
    private val knownStores = listOf(
        "Lidl", "Biedronka", "Kaufland", "Żabka", "Auchan", "Carrefour", "Dino", "Netto", "Stokrotka",
        "Rossmann", "Hebe", "Orlen", "Shell", "Circle K", "BP", "Lewiatan", "Aldi", "Pepco", "Ikea",
        "Castorama", "Leroy Merlin", "Action", "Decathlon", "Empik", "Media Expert", "McDonald's", "KFC",
    )

    fun parse(lines: List<String>): Result {
        val clean = lines.map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotBlank() }
        val joined = clean.joinToString("\n")

        val merchant = knownStores.firstOrNull { joined.contains(it, ignoreCase = true) }
            ?: clean.take(4).firstOrNull { l ->
                l.count { it.isLetter() } >= 3 && stopWords.none { l.uppercase().contains(it) } && !l.any { it.isDigit() }
            }?.let(::titleCase)

        val date = dateIso.find(joined)?.let { safeDate(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
            ?: datePl.find(joined)?.let { safeDate(it.groupValues[3], it.groupValues[2], it.groupValues[1]) }

        val total = clean.mapNotNull { totalLine.find(it)?.groupValues?.get(2)?.let(::toGrosze) }.maxOrNull()

        val items = mutableListOf<Item>()
        var reachedTotal = false
        for (l in clean) {
            if (totalLine.containsMatchIn(l)) { reachedTotal = true }
            if (reachedTotal) continue
            val up = l.uppercase()
            if (stopWords.any { up.startsWith(it) || up.contains("PTU") }) continue
            itemWithQty.find(l)?.let { m ->
                val name = m.groupValues[1].trim()
                val qty = m.groupValues[2].replace(',', '.').toDoubleOrNull() ?: 1.0
                val price = toGrosze(m.groupValues[4])
                if (price != null && isName(name)) items += Item(titleCase(name), qty, price)
                return@let
            } ?: itemSimple.find(l)?.let { m ->
                val name = m.groupValues[1].trim()
                val price = toGrosze(m.groupValues[2])
                if (price != null && isName(name)) items += Item(titleCase(name), 1.0, price)
            }
        }
        return Result(merchant, date, total ?: items.sumOf { it.price }.takeIf { it > 0 }, items)
    }

    /** Sprawdza czy suma pozycji zgadza się z sumą paragonu (tolerancja 1 gr na rabaty/zaokrąglenia). */
    fun isConsistent(r: Result): Boolean = r.total != null && abs(r.total - r.itemsSum) <= 1

    private fun isName(s: String) = s.count { it.isLetter() } >= 2

    private fun toGrosze(s: String): Long? = parseMoney(s)

    private fun safeDate(y: String, m: String, d: String): Long? = runCatching {
        LocalDate.of(y.toInt(), m.toInt(), d.toInt()).takeIf { !it.isAfter(LocalDate.now().plusDays(1)) }?.toEpochDay()
    }.getOrNull()

    private fun titleCase(s: String): String =
        s.lowercase(PL).replaceFirstChar { it.titlecase(PL) }
}
