package pl.openpayments.domain

import java.time.LocalDate
import kotlin.math.abs

/**
 * Parser tekstu z polskiego paragonu fiskalnego (wynik OCR).
 * Wyciąga: sprzedawcę, datę, sumę oraz listę pozycji z rabatami.
 *
 * Obsługiwane układy pozycji:
 *  - „Mleko 2% 1L    1 x4,99   4,99 C”          (Lidl, większość kas)
 *  - „MLEKO LACIATE 2% 1L  C 1 x3,99 3,99C”      (litera PTU przed ilością)
 *  - „Chleb zytni      6,50 A”                    (bez ilości)
 *  - „SER GOUDA PLASTRY”  +  „1 szt * 7,49 = 7,49 C” (nazwa i cena w dwóch liniach)
 *  - „Banany luz 0,856 x 5,99 5,13 C”            (towar na wagę)
 *  - „Rabat  -1,50” / „Opust -1,50”               (odejmowany od poprzedniej pozycji)
 */
object ReceiptParser {

    data class Item(val name: String, val quantity: Double, val price: Long, val rawName: String = name)
    data class Result(
        val merchant: String?,
        val date: Long?,
        val total: Long?,
        val items: List<Item>,
    ) {
        val itemsSum: Long get() = items.sumOf { it.price }
    }

    private const val AMOUNT = """(\d{1,5}[.,]\d{2})"""
    private const val QTY = """(\d{1,4}(?:[.,]\d{1,3})?)\s*(?:szt\.?|kg|l|op\.?)?"""
    private val itemWithQty = Regex(
        """^(.{2,}?)\s+(?:[A-G]\s+)?$QTY\s*[x×*]\s*$AMOUNT\s*=?\s*$AMOUNT\s*[A-G]?$""", RegexOption.IGNORE_CASE,
    )
    private val qtyOnly = Regex("""^(?:[A-G]\s+)?$QTY\s*[x×*]\s*$AMOUNT\s*=?\s*$AMOUNT\s*[A-G]?$""", RegexOption.IGNORE_CASE)
    private val itemSimple = Regex("""^(.{2,}?)\s+$AMOUNT\s*[A-G]$""", RegexOption.IGNORE_CASE)
    private val discount = Regex("""^(RABAT|OPUST|UPUST|PROMOCJA|OBNI[ŻZ]KA|ZNI[ŻZ]KA)\b.*?-\s*$AMOUNT""", RegexOption.IGNORE_CASE)
    private val totalLine = Regex("""(SUMA|RAZEM|DO ZAP[ŁL]ATY|TOTAL)\b\D*$AMOUNT""", RegexOption.IGNORE_CASE)
    private val dateIso = Regex("""(20\d{2})[-./](\d{2})[-./](\d{2})""")
    private val datePl = Regex("""(\d{2})[-./](\d{2})[-./](20\d{2})""")

    private val stopWords = listOf(
        "PTU", "SPRZEDA", "PARAGON", "FISKALNY", "NIP", "KASA", "KASJER", "RESZTA", "GOTÓWKA", "GOTOWKA",
        "KARTA", "SUMA", "RAZEM", "ZAPŁATY", "ZAPLATY", "NR SYS", "NR WYDR", "OPODATK", "WPŁATA", "WPLATA",
        "PŁATNOŚĆ", "PLATNOSC", "BLIK", "TERMINAL", "NR TRANS", "DATA", "GODZ", "ZAOSZCZĘDZI", "ZAOSZCZEDZI",
        "LIDL PLUS", "MOJA BIEDRONKA", "KUPON", "PUNKTY", "TRANSAKCJA", "WYDRUK", "NIEFISKALNY", "ZWROT",
    )
    private val knownStores = listOf(
        "Lidl", "Biedronka", "Kaufland", "Żabka", "Zabka", "Auchan", "Carrefour", "Dino", "Netto", "Stokrotka",
        "Rossmann", "Hebe", "Orlen", "Shell", "Circle K", "Lewiatan", "Aldi", "Pepco", "Ikea", "Intermarche",
        "Castorama", "Leroy Merlin", "Action", "Decathlon", "Empik", "Media Expert", "McDonald's", "KFC",
        "Polomarket", "Społem", "Freshmarket", "Topaz", "Delikatesy Centrum", "Mila", "Spar", "Eurocash",
    )

    /** Naprawia typowe pomyłki OCR w kwotach: „4,9O” → „4,90”, „l2.5O” → „12,50”, „4 ,99” → „4,99”. */
    fun fixOcr(line: String): String {
        var s = line.replace(Regex("""(\d)\s*([.,])\s*(\d{2})\b"""), "$1$2$3")
        // Ilość na wagę rozbita przez OCR: „0, 856 x5,99” → „0,856 x5,99”
        s = s.replace(Regex("""(\d)\s*([.,])\s+(\d{1,3})(?=\s*[x×*])"""), "$1$2$3")
        s = Regex("""(?<![A-Za-z])[\dOoIl|SB]{1,5}[.,][\dOoIl|SB]{2}(?![A-Za-z])""").replace(s) { m ->
            val t = m.value
            if (t.count { it.isDigit() } == 0) t
            else t.map { c ->
                when (c) { 'O', 'o' -> '0'; 'I', 'l', '|' -> '1'; 'S' -> '5'; 'B' -> '8'; else -> c }
            }.joinToString("")
        }
        return s
    }

    fun parse(lines: List<String>): Result {
        val clean = lines.map { fixOcr(it.trim().replace(Regex("\\s+"), " ")) }.filter { it.isNotBlank() }
        val joined = clean.joinToString("\n")

        val merchant = knownStores.firstOrNull { joined.contains(it, ignoreCase = true) }
            ?.let { if (it == "Zabka") "Żabka" else it }
            ?: clean.take(4).firstOrNull { l ->
                l.count { it.isLetter() } >= 3 && stopWords.none { l.uppercase().contains(it) } && !l.any { it.isDigit() }
            }?.let(::titleCase)

        val date = dateIso.find(joined)?.let { safeDate(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
            ?: datePl.find(joined)?.let { safeDate(it.groupValues[3], it.groupValues[2], it.groupValues[1]) }

        val total = clean.mapNotNull { totalLine.find(it)?.groupValues?.get(2)?.let(::parseMoney) }.maxOrNull()

        val items = mutableListOf<Item>()
        var pendingName: String? = null
        for (l in clean) {
            if (totalLine.containsMatchIn(l)) break
            val up = l.uppercase(PL)

            discount.find(l)?.let { m ->
                val d = parseMoney(m.groupValues[2]) ?: 0
                if (items.isNotEmpty() && d > 0) {
                    val last = items.last()
                    items[items.lastIndex] = last.copy(price = (last.price - d).coerceAtLeast(0))
                }
                pendingName = null
                continue
            }
            if (stopWords.any { up.startsWith(it) } || up.contains("PTU")) { pendingName = null; continue }

            itemWithQty.find(l)?.let { m ->
                addItem(items, m.groupValues[1], m.groupValues[2], m.groupValues[4])
                pendingName = null
                continue
            }
            qtyOnly.find(l)?.let { m ->
                pendingName?.let { addItem(items, it, m.groupValues[1], m.groupValues[3]) }
                pendingName = null
                continue
            }
            itemSimple.find(l)?.let { m ->
                addItem(items, m.groupValues[1], "1", m.groupValues[2])
                pendingName = null
                continue
            }
            // Linia z samą nazwą — cena może być w następnej
            pendingName = if (isName(l) && l.length <= 48) l else null
        }
        return Result(merchant, date, total ?: items.sumOf { it.price }.takeIf { it > 0 }, items)
    }

    private fun addItem(items: MutableList<Item>, rawName: String, qty: String, price: String) {
        val name = rawName.trim().trimEnd('*', '#', '-').trim()
        val p = parseMoney(price) ?: return
        if (!isName(name)) return
        items += Item(titleCase(name), qty.replace(',', '.').toDoubleOrNull() ?: 1.0, p, name)
    }

    /** Czy suma pozycji zgadza się z sumą paragonu (tolerancja 1 gr). */
    fun isConsistent(r: Result): Boolean = r.total != null && abs(r.total - r.itemsSum) <= 1

    private fun isName(s: String) = s.count { it.isLetter() } >= 2

    private fun safeDate(y: String, m: String, d: String): Long? = runCatching {
        LocalDate.of(y.toInt(), m.toInt(), d.toInt()).takeIf { !it.isAfter(LocalDate.now().plusDays(1)) }?.toEpochDay()
    }.getOrNull()

    private fun titleCase(s: String): String = s.lowercase(PL).replaceFirstChar { it.titlecase(PL) }
}
