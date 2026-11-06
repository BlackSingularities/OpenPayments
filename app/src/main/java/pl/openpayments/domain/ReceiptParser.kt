package pl.openpayments.domain

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Parser tekstu z polskiego paragonu fiskalnego (wynik OCR).
 * Wyciąga: sprzedawcę, datę, sumę oraz listę pozycji z rabatami.
 *
 * Obsługiwane układy pozycji:
 *  - „Mleko 2% 1L    1 x4,99   4,99 C”          (Lidl, większość kas)
 *  - „MLEKO LACIATE 2% 1L  C 1 x3,99 3,99C”      (litera PTU przed ilością — Biedronka)
 *  - „Chleb zytni      6,50 A”                    (bez ilości)
 *  - „SER GOUDA PLASTRY”  +  „1 szt * 7,49 = 7,49 C” (nazwa i cena w dwóch liniach)
 *  - „Banany luz 0,856 x 5,99 5,13 C”            (towar na wagę)
 *  - „Rabat  -1,50” / „Opust -1,50”               (odejmowany od poprzedniej pozycji)
 *  - „Rabat -1,20” + „7,96C”                      (Biedronka: cena po rabacie w osobnej linii)
 *  - „Rabat” + „-1,20” + „7,96C”                  (to samo, rozbite przez OCR)
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
    private const val PTU = """[A-G]"""
    /** Nazwa, (litera PTU), ilość (bywa zgubiona przez OCR), „x”, cena jednostkowa, (wartość), (litera PTU). */
    private val itemWithQty = Regex(
        """^(.{2,}?)\s+(?:$PTU\s+)?(?:$QTY\s*)?[x×*]\s*$AMOUNT(?:\s*=?\s*$AMOUNT)?\s*$PTU?$""", RegexOption.IGNORE_CASE,
    )
    private val qtyOnly = Regex("""^(?:$PTU\s+)?(?:$QTY\s*)?[x×*]\s*$AMOUNT(?:\s*=?\s*$AMOUNT)?\s*$PTU?$""", RegexOption.IGNORE_CASE)
    private val itemSimple = Regex("""^(.{2,}?)\s+$AMOUNT\s*$PTU$""", RegexOption.IGNORE_CASE)
    private val bareAmount = Regex("""^$AMOUNT\s*$PTU?$""", RegexOption.IGNORE_CASE)
    private val negativeAmount = Regex("""^-\s*$AMOUNT\s*$PTU?$""", RegexOption.IGNORE_CASE)
    private val anyAmount = Regex("""(-?)\s*$AMOUNT""")
    private val dateIso = Regex("""(20\d{2})[-./](\d{2})[-./](\d{2})""")
    private val datePl = Regex("""(\d{2})[-./](\d{2})[-./](20\d{2})""")
    private val taxSale = Regex("""^SPRZ\S*\s+OPOD\S*\s+$PTU\b\D*$AMOUNT""")

    private val discountWords = listOf("RABAT", "OPUST", "UPUST", "PROMOCJA", "OBNIZKA", "ZNIZKA", "PROMO")
    private val stopWords = listOf(
        "PTU", "SPRZEDA", "PARAGON", "FISKALNY", "NIP", "KASA", "KASJER", "RESZTA", "GOTOWKA",
        "KARTA", "SUMA", "RAZEM", "ZAPLATY", "NR SYS", "NR WYDR", "OPODATK", "WPLATA",
        "PLATNOSC", "BLIK", "TERMINAL", "NR TRANS", "DATA", "GODZ", "ZAOSZCZEDZI",
        "LIDL PLUS", "MOJA BIEDRONKA", "KUPON", "PUNKTY", "TRANSAKCJA", "WYDRUK", "NIEFISKALNY", "ZWROT",
        "SKLEP", "UL.", "UL ", "TEL", "BDO", "PLN",
    )
    private val knownStores = listOf(
        "Biedronka", "Lidl", "Kaufland", "Żabka", "Auchan", "Carrefour", "Dino", "Netto", "Stokrotka",
        "Rossmann", "Hebe", "Orlen", "Shell", "Circle K", "Lewiatan", "Aldi", "Pepco", "Ikea", "Intermarche",
        "Castorama", "Leroy Merlin", "Action", "Decathlon", "Empik", "Media Expert", "McDonald's", "KFC",
        "Polomarket", "Społem", "Freshmarket", "Topaz", "Delikatesy Centrum", "Mila", "Spar", "Eurocash",
        "Super-Pharm", "Ziko", "Apteka Gemini", "Sephora", "Smyk", "Jysk", "Primark",
    )
    /** Nazwy spółek z nagłówka paragonu → sieć. */
    private val operators = mapOf(
        "JERONIMO MARTINS" to "Biedronka", "LIDL SP" to "Lidl", "KAUFLAND POLSKA" to "Kaufland",
        "ZABKA POLSKA" to "Żabka", "DINO POLSKA" to "Dino", "AUCHAN POLSKA" to "Auchan",
        "CARREFOUR POLSKA" to "Carrefour", "ROSSMANN SDP" to "Rossmann", "ALDI SP" to "Aldi",
        "NETTO SP" to "Netto", "POLSKI KONCERN NAFTOWY" to "Orlen", "PKN ORLEN" to "Orlen",
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
        // Minus odklejony od kwoty rabatu: „- 1,20” → „-1,20”; „–” i „—” jako minus.
        s = s.replace('–', '-').replace('—', '-').replace(Regex("""-\s+(?=\d)"""), "-")
        return s
    }

    fun parse(lines: List<String>): Result {
        val clean = lines.map { fixOcr(it.trim().replace(Regex("\\s+"), " ")) }.filter { it.isNotBlank() }
        val keys = clean.map { ProductDictionary.strip(it) }
        val joined = clean.joinToString("\n")

        val start = keys.indexOfFirst { it.contains("PARAGON") && !it.contains("NIEFISK") }
            .let { if (it >= 0 && it < clean.size * 0.6) it + 1 else 0 }

        val merchant = findMerchant(clean, keys, start)
        val date = dateIso.find(joined)?.let { safeDate(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
            ?: datePl.find(joined)?.let { safeDate(it.groupValues[3], it.groupValues[2], it.groupValues[1]) }
        val total = findTotal(clean, keys)

        val items = mutableListOf<Item>()
        val gross = mutableListOf<Long>()     // cena przed rabatami, równolegle do items
        var pendingName: String? = null
        var expectPrice = false               // po „Rabat” może przyjść cena po rabacie
        var discountApplied = false           // czy rabat został już odjęty od ostatniej pozycji

        fun applyDiscount(d: Long) {
            if (items.isEmpty() || d <= 0) return
            val last = items.last()
            items[items.lastIndex] = last.copy(price = (last.price - d).coerceAtLeast(0))
            discountApplied = true
        }

        for (i in start until clean.size) {
            val l = clean[i]
            val up = keys[i]
            if (isTotalLine(up)) break
            if (up.startsWith("SPRZ") && up.contains("OPOD")) break

            // Rabat / opust
            if (isDiscount(up)) {
                val amounts = anyAmount.findAll(l).toList()
                val neg = amounts.firstOrNull { it.groupValues[1] == "-" } ?: amounts.firstOrNull()
                discountApplied = false
                neg?.let { applyDiscount(parseMoney(it.groupValues[2]) ?: 0) }
                // „Rabat -1,20 7,96C” w jednej linii: druga kwota to cena po rabacie
                amounts.lastOrNull()?.takeIf { it !== neg && it.groupValues[1] != "-" }?.let { setAfterDiscount(items, gross, parseMoney(it.groupValues[2])) }
                expectPrice = true
                pendingName = null
                continue
            }
            negativeAmount.find(l)?.let { m ->
                if (expectPrice || items.isNotEmpty()) {
                    if (!discountApplied) applyDiscount(parseMoney(m.groupValues[1]) ?: 0)
                    expectPrice = true
                }
                pendingName = null
                continue
            }
            bareAmount.find(l)?.let { m ->
                val v = parseMoney(m.groupValues[1])
                if (expectPrice) {
                    setAfterDiscount(items, gross, v)
                    expectPrice = false; discountApplied = false
                } else if (pendingName != null && v != null && PTU_END.containsMatchIn(l)) {
                    addItem(items, gross, pendingName!!, "1", null, m.groupValues[1])
                }
                pendingName = null
                continue
            }
            expectPrice = false; discountApplied = false
            if (stopWords.any { up.startsWith(it) } || up.contains("PTU")) { pendingName = null; continue }

            itemWithQty.find(l)?.takeIf { isName(cleanName(it.groupValues[1])) }?.let { m ->
                addItem(items, gross, m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4])
                pendingName = null
                continue
            }
            qtyOnly.find(l)?.let { m ->
                pendingName?.let { addItem(items, gross, it, m.groupValues[1], m.groupValues[2], m.groupValues[3]) }
                pendingName = null
                continue
            }
            itemSimple.find(l)?.let { m ->
                addItem(items, gross, m.groupValues[1], "1", null, m.groupValues[2])
                pendingName = null
                continue
            }
            // Linia z samą nazwą — cena może być w następnej
            pendingName = if (isName(l) && l.length <= 48 && !trailingAmount.containsMatchIn(l)) l else null
        }
        return Result(merchant, date, total ?: items.sumOf { it.price }.takeIf { it > 0 }, items)
    }

    private val PTU_END = Regex("""\d\s*[A-G]$""", RegexOption.IGNORE_CASE)
    private val trailingAmount = Regex("""\d[.,]\d{2}\s*[A-G]?$""", RegexOption.IGNORE_CASE)

    /** Cena po rabacie (Biedronka drukuje ją pod rabatem). Przyjmujemy tylko, gdy jest niższa od ceny przed rabatem. */
    private fun setAfterDiscount(items: MutableList<Item>, gross: List<Long>, v: Long?) {
        if (v == null || items.isEmpty()) return
        val g = gross.last()
        if (v in 0 until g + 1) items[items.lastIndex] = items.last().copy(price = v)
    }

    private fun addItem(items: MutableList<Item>, gross: MutableList<Long>, rawName: String, qty: String, unit: String?, value: String) {
        val name = cleanName(rawName)
        if (!isName(name)) return
        val q = qty.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
        val u = unit?.takeIf { it.isNotBlank() }?.let(::parseMoney)
        val v = value.takeIf { it.isNotBlank() }?.let(::parseMoney)
        val price = when {
            v != null -> v
            u != null -> (u * q).roundToLong()
            else -> return
        }
        items += Item(titleCase(name), q, price, name)
        gross += price
    }

    /** Usuwa z nazwy literę PTU, resztki ilości („2 x”, „1 szt”) i znaki z krawędzi. */
    private fun cleanName(raw: String): String {
        var s = raw.trim()
        repeat(2) {
            s = s.replace(Regex("""\s+[A-G]$"""), "")
                .replace(Regex("""\s+\d{1,4}(?:[.,]\d{1,3})?\s*(?:szt\.?|kg|op\.?)?\s*[x×*]?$""", RegexOption.IGNORE_CASE), "")
                .trim().trimEnd('*', '#', '-', '=', '.', ',').trim()
        }
        return s
    }

    private fun isDiscount(up: String): Boolean {
        val first = up.trimStart('-', ' ', '*').split(' ', ':', '.').firstOrNull().orEmpty()
        if (first.length < 4) return false
        return discountWords.any { it == first || (first.length >= 5 && levenshtein(first, it) <= 1) }
    }

    private val totalStart = Regex("""^\W*(S[UV][MN]A(?:PLN)?|RAZEM|DO ZAPLATY|NALEZNOSC|TOTAL)\b""")

    private fun isTotalLine(up: String) = totalStart.containsMatchIn(up) && !up.contains("PTU") && !up.contains("RABAT")

    /** Suma z linii „SUMA PLN” (także gdy kwota trafiła do następnej linii); awaryjnie suma sprzedaży opodatkowanej. */
    private fun findTotal(clean: List<String>, keys: List<String>): Long? {
        val found = mutableListOf<Long>()
        for (i in clean.indices) {
            if (!isTotalLine(keys[i])) continue
            val here = anyAmount.findAll(clean[i]).lastOrNull()?.groupValues?.get(2)?.let(::parseMoney)
            val next = clean.getOrNull(i + 1)?.let { bareAmount.find(it) }?.groupValues?.get(1)?.let(::parseMoney)
            (here ?: next)?.let { found += it }
        }
        found.maxOrNull()?.let { return it }
        val taxed = keys.mapNotNull { taxSale.find(it)?.groupValues?.get(1)?.let(::parseMoney) }
        return taxed.takeIf { it.isNotEmpty() }?.sum()
    }

    private fun findMerchant(clean: List<String>, keys: List<String>, start: Int): String? {
        val header = keys.take(maxOf(start, 8).coerceAtMost(keys.size))
        val storeKeys = knownStores.map { it to ProductDictionary.strip(it) }
        fun wordMatch(text: String, k: String) = Regex("""(^|[^A-Z])${Regex.escape(k)}([^A-Z]|$)""").containsMatchIn(text)

        // 1. nazwa sieci w nagłówku, 2. spółka-operator, 3. sieć gdziekolwiek (np. „Moja Biedronka” w stopce)
        storeKeys.firstOrNull { (_, k) -> header.any { wordMatch(it, k) } }?.let { return it.first }
        operators.entries.firstOrNull { (k, _) -> keys.any { it.contains(k) } }?.let { return it.value }
        storeKeys.firstOrNull { (_, k) -> k.length >= 4 && keys.any { wordMatch(it, k) } }?.let { return it.first }
        // 4. literówka OCR w nagłówku: „Biedronko”, „L1dl” itp.
        for (line in header) for (word in line.split(Regex("[^A-Z0-9]+"))) {
            if (word.length < 4) continue
            storeKeys.firstOrNull { (_, k) ->
                !k.contains(' ') && k.length >= 4 && abs(k.length - word.length) <= 1 &&
                    levenshtein(word.replace('1', 'I').replace('0', 'O'), k) <= (if (k.length >= 8) 2 else 1)
            }?.let { return it.first }
        }
        return clean.take(4).firstOrNull { l ->
            val up = ProductDictionary.strip(l)
            l.count { it.isLetter() } >= 3 && stopWords.none { up.contains(it) } && !l.any { it.isDigit() }
        }?.let(::titleCase)
    }

    /** Czy suma pozycji zgadza się z sumą paragonu (tolerancja 1 gr). */
    fun isConsistent(r: Result): Boolean = r.total != null && abs(r.total - r.itemsSum) <= 1

    /** Nazwa ma co najmniej dwie litery poza jednostkami („1 szt” to nie nazwa). */
    private fun isName(s: String) =
        s.replace(Regex("""(?i)(?<![A-Za-z])(szt|kg|op|x)(?![A-Za-z])"""), "").count { it.isLetter() } >= 2

    private fun safeDate(y: String, m: String, d: String): Long? = runCatching {
        LocalDate.of(y.toInt(), m.toInt(), d.toInt()).takeIf { !it.isAfter(LocalDate.now().plusDays(1)) }?.toEpochDay()
    }.getOrNull()

    private fun titleCase(s: String): String = s.lowercase(PL).replaceFirstChar { it.titlecase(PL) }

    fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
