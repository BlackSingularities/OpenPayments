package pl.openpayments.domain.bank

import kotlin.math.abs

/** Znaczenie kolumny w pliku z banku. */
enum class Role(val label: String) {
    DATE("Data"), AMOUNT("Kwota"), DEBIT("Wydatki (obciążenia)"), CREDIT("Wpływy (uznania)"),
    MERCHANT("Kontrahent / sprzedawca"), DESCRIPTION("Opis / tytuł"), TYPE("Typ operacji"), CATEGORY("Kategoria"),
    CURRENCY("Waluta"), BALANCE("Saldo"), STATUS("Status"), FEE("Opłata / prowizja"), IGNORE("Pomiń");

    val isMoney get() = this == AMOUNT || this == DEBIT || this == CREDIT || this == BALANCE || this == FEE
}

/** Skąd wiadomo, czy operacja to wydatek, czy wpływ. */
enum class SignMode(val label: String) {
    SIGNED("ze znaku kwoty"), DEBIT_CREDIT("z osobnych kolumn wydatków i wpływów"), TYPE("z typu operacji"),
    BALANCE("ze zmiany salda"), ALL_EXPENSES("brak znaku — wszystko jako wydatki"),
}

enum class SkipReason(val label: String) {
    PENDING("Oczekująca, odrzucona lub anulowana"), TRANSFER("Przelew własny, wymiana walut lub oszczędności"),
    DUPLICATE("Już jest w aplikacji"), ZERO("Kwota zero"),
}

/** Tabela po wstępnym odczycie: nagłówek (jeśli jest) i wiersze danych o wyrównanej liczbie kolumn. */
data class CsvTable(
    val header: List<String>?,
    val rows: List<List<String>>,
    val lineNumbers: List<Int>,
    val delimiter: Char,
    val charset: String,
    val preamble: List<String>,
) {
    val columns: Int get() = header?.size ?: rows.firstOrNull()?.size ?: 0
}

/** Wynik analizy: rozpoznane role kolumn i formaty. */
data class Analysis(
    val table: CsvTable,
    val roles: List<Role>,
    val bank: String?,
    val dateOrder: DateOrder,
    val decimal: Char,
    val signature: String,
)

/** Jedna operacja odczytana z wyciągu. Kwota w groszach: minus = wydatek. */
data class BankRow(
    val line: Int,
    val date: Long,
    val amount: Long,
    val currency: String?,
    val counterparty: String,
    val descriptions: List<String>,
    val type: String,
    val bankCategory: String,
    val reason: SkipReason?,
)

data class ReadResult(val rows: List<BankRow>, val ignoredLines: Int, val signMode: SignMode, val foreignCurrency: Int)

data class ReadOptions(val invert: Boolean = false)

object BankStatement {

    private class Kw(val text: String, val weight: Double)
    private fun kw(vararg p: Pair<String, Double>) = p.map { Kw(fold(it.first), it.second) }

    /** Słowa w nagłówkach kolumn (bez polskich znaków) z wagą pewności. */
    private val headerWords: Map<Role, List<Kw>> = mapOf(
        Role.DATE to kw(
            "data transakcji" to 5.0, "data operacji" to 5.0, "transaction date" to 5.0, "data zlecenia" to 4.0,
            "started date" to 4.0, "data" to 3.0, "date" to 3.0, "dzien" to 2.5, "day" to 2.0, "data ksiegowania" to 3.5,
            "booking date" to 3.5, "completed date" to 3.5, "data rozliczenia" to 2.0, "data waluty" to 1.0, "value date" to 1.0,
        ),
        Role.AMOUNT to kw(
            "kwota transakcji" to 5.0, "kwota operacji" to 5.0, "waluta rachunku" to 5.0, "kwota" to 4.0, "amount" to 4.0,
            "wartosc" to 3.0, "suma" to 2.0, "value" to 2.0, "kwota pln" to 5.0, "kwota w pln" to 5.0,
        ),
        Role.DEBIT to kw(
            "obciazen" to 5.0, "wyplyw" to 5.0, "wydatk" to 4.5, "wydatek" to 4.5, "debit" to 5.0, "money out" to 5.0,
            "paid out" to 5.0, "withdrawal" to 4.0, "winien" to 3.0, "wyplaty" to 3.0, "rozchod" to 4.5,
        ),
        Role.CREDIT to kw(
            "uznani" to 5.0, "wplyw" to 5.0, "przychod" to 4.5, "credit" to 5.0, "money in" to 5.0, "paid in" to 5.0,
            "deposit" to 4.0, "wplaty" to 3.0, "przych" to 4.0,
        ),
        Role.BALANCE to kw("saldo" to 5.0, "balance" to 5.0, "stan konta" to 4.0, "stan rachunku" to 4.0),
        Role.FEE to kw("prowizj" to 5.0, "oplata" to 4.0, "fee" to 5.0, "commission" to 4.0),
        Role.CURRENCY to kw("waluta" to 5.0, "currency" to 5.0, "wal" to 2.0, "ccy" to 4.0),
        Role.MERCHANT to kw(
            "kontrahent" to 5.0, "odbiorc" to 4.5, "nadawc" to 4.5, "zleceniodawc" to 4.5, "sprzedawc" to 5.0, "merchant" to 5.0,
            "payee" to 5.0, "counterparty" to 5.0, "beneficiary" to 4.0, "sklep" to 4.0, "partner" to 3.0, "nazwa" to 2.0,
            "platnik" to 4.0, "payer" to 4.0, "firma" to 3.0, "dane kontrahenta" to 5.5,
        ),
        Role.DESCRIPTION to kw(
            "opis" to 4.0, "tytul" to 5.0, "description" to 4.0, "szczegol" to 3.0, "details" to 3.0, "memo" to 3.0,
            "notatka" to 3.0, "uwagi" to 2.0, "note" to 3.0, "reference" to 1.0, "title" to 4.0, "tresc" to 3.0, "na co" to 3.0,
        ),
        Role.TYPE to kw(
            "typ" to 4.0, "rodzaj" to 4.0, "type" to 4.0, "kind" to 3.0, "operacja" to 1.5, "przychod/wydatek" to 5.0,
            "wydatek/przychod" to 5.0, "kierunek" to 4.0, "direction" to 4.0,
        ),
        Role.CATEGORY to kw("kategori" to 5.0, "category" to 5.0, "grupa" to 2.0),
        Role.STATUS to kw("status" to 5.0, "state" to 5.0, "stan" to 2.0),
        Role.IGNORE to kw(
            "rachunek" to 5.0, "rachunku" to 5.0, "nr " to 5.0, "numer" to 5.0, "konto" to 5.0, "account" to 5.0,
            "iban" to 5.0, "referenc" to 5.0, "bank" to 4.0, "adres" to 5.0, "id" to 3.0, "product" to 3.0, "produkt" to 3.0,
            "zrodlo" to 4.0, "source" to 3.0, "karta" to 3.0, "card" to 3.0, "lp" to 4.0,
        ),
    )

    /** Fragmenty, które dyskwalifikują nagłówek dla danej roli. */
    private val headerVeto: Map<Role, List<String>> = mapOf(
        Role.AMOUNT to listOf("blokad", "platnosci w walucie", "oryginaln", "saldo", "balance", "po transakcji", "prowizj", "fee"),
        Role.DATE to listOf("kwota", "saldo", "typ"),
        Role.CURRENCY to listOf("kwota", "data", "saldo", "kurs"),
        Role.MERCHANT to listOf("rachun", "adres", "numer", "nr ", "konto", "account", "iban", "bank"),
        Role.DESCRIPTION to listOf("rachun", "numer", "nr "),
        Role.STATUS to listOf("konta", "rachunku"),
        Role.TYPE to listOf("karty", "card"),
        Role.FEE to listOf("kwota operacji"),
    ).mapValues { (_, v) -> v.map(::fold) }

    internal fun headerScore(role: Role, header: String): Double {
        val h = " " + fold(header).replace('#', ' ').replace(Regex("[\"'*:]"), "").replace(Regex("\\s+"), " ").trim() + " "
        if (h.isBlank()) return 0.0
        if (headerVeto[role]?.any { h.contains(it) } == true) return 0.0
        var best = 0.0
        for (k in headerWords[role].orEmpty()) {
            val t = k.text
            val hit = if (t.length <= 4) Regex("(?<![a-z])" + Regex.escape(t.trim()) + "(?![a-z])").containsMatchIn(h) else h.contains(t)
            if (hit && k.weight > best) best = k.weight
        }
        return best
    }

    private fun looksLikeHeader(row: List<String>): Int {
        var n = 0
        for (c in row) {
            val s = CsvReader.cell(c)
            if (s.isEmpty() || s.length > 60) continue
            if (Dates.parse(s) != null || Amounts.parse(s) != null) return 0
            if (Role.entries.any { it != Role.IGNORE && headerScore(it, s) >= 3.0 }) n++
        }
        return n
    }

    /** Odczyt tabeli: kodowanie, separator, pominięcie nagłówka banku (dane klienta, salda) i stopki. */
    fun table(bytes: ByteArray): CsvTable {
        val decoded = CsvReader.decode(bytes)
        var text = decoded.text.replace("\u0000", "")
        val explicit = CsvReader.explicitDelimiter(text)
        val delim = explicit?.first ?: CsvReader.detectDelimiter(text)
        if (explicit != null) text = explicit.second
        val raw = CsvReader.parse(text, delim)
        val nonEmpty = raw.withIndex().filter { (_, r) -> r.count { CsvReader.cell(it).isNotEmpty() } >= 2 }
        require(nonEmpty.isNotEmpty()) { "Plik nie zawiera tabeli z kolumnami" }

        // Wiersz nagłówka: najwięcej znanych nazw kolumn w pierwszych 60 wierszach.
        var headerIdx = -1; var headerHits = 1
        for ((i, r) in raw.withIndex().take(60)) {
            val hits = looksLikeHeader(r)
            if (hits > headerHits) { headerIdx = i; headerHits = hits }
        }
        val dataRaw = nonEmpty.filter { it.index > headerIdx }
        // Bez nagłówka: zostawiamy wiersze, w których jest data i kwota (odrzuca metryczki i podsumowania).
        val data = if (headerIdx >= 0) dataRaw else dataRaw.filter { (_, r) ->
            val cells = r.map(CsvReader::cell)
            cells.any { Dates.parse(it) != null } && cells.any { Amounts.isStrict(it) || Amounts.parse(it) != null && Dates.parse(it) == null }
        }.ifEmpty { dataRaw }
        val header = if (headerIdx >= 0) raw[headerIdx].map(CsvReader::cell) else null
        val width = maxOf(header?.size ?: 0, data.maxOfOrNull { it.value.size } ?: 0).coerceAtMost(60)
        fun pad(r: List<String>) = (r.map(CsvReader::cell) + List(maxOf(0, width - r.size)) { "" }).take(width)
        // Puste kolumny na końcu (np. średnik po ostatnim polu) obcinamy.
        val rows = data.map { pad(it.value) }
        var used = width
        while (used > 1 && rows.all { it[used - 1].isEmpty() } && (header == null || header.getOrNull(used - 1).isNullOrEmpty())) used--
        val preambleEnd = if (headerIdx >= 0) headerIdx else data.firstOrNull()?.index ?: 0
        return CsvTable(
            header = header?.let { (it + List(maxOf(0, used - it.size)) { "" }).take(used) },
            rows = rows.map { it.take(used) },
            lineNumbers = data.map { it.index + 1 },
            delimiter = delim,
            charset = decoded.charset,
            preamble = raw.take(preambleEnd).map { r -> r.joinToString(" ") { CsvReader.cell(it) }.trim() }.filter { it.isNotEmpty() }.take(30),
        )
    }

    /** Statystyki kolumny liczone na wartościach niepustych. */
    internal class Stats(cells: List<String>) {
        val values = cells.filter { it.isNotBlank() }
        val n = values.size
        val fill = if (cells.isEmpty()) 0.0 else n.toDouble() / cells.size
        private fun rate(p: (String) -> Boolean) = if (n == 0) 0.0 else values.count(p).toDouble() / n
        val date = rate { Dates.parse(it) != null }
        val money = rate { Amounts.parse(it) != null && Dates.parse(it) == null }
        val strictMoney = rate { Amounts.isStrict(it) }
        val negative = rate { (Amounts.parse(it) ?: 0L) < 0 }
        val currency = rate { Amounts.isCurrencyCode(it) }
        val account = rate { it.replace(" ", "").let { s -> s.length >= 16 && s.count(Char::isDigit) >= s.length - 4 } }
        val avgLen = if (n == 0) 0.0 else values.sumOf { it.length }.toDouble() / n
        val distinct = values.toSet().size
        val letters = rate { s -> s.count { it.isLetter() } >= 2 }
        val signWords = rate { directionOf(it) != null }
    }

    private val incomeWords = listOf(
        "uznani", "przychod", "wplyw", "wplata", "credit", "przelew przychodzacy", "przelew otrzymany", "zwrot", "refund",
        "wynagrodz", "deposit", "odsetki", "income", "money in", "przych", "wplyn", "otrzym",
    ).map(::fold)
    private val expenseWords = listOf(
        "obciazen", "wydatek", "wydatk", "wyplat", "platnosc", "zakup", "debit", "przelew wychodzacy", "przelew wyslany",
        "oplata", "prowizj", "card payment", "payment", "withdrawal", "atm", "zlecenie stale", "polecenie zaplaty",
        "expense", "money out", "rozchod", "koszt",
    ).map(::fold)
    private val incomeExact = setOf("cr", "c", "+", "in", "u", "ma", "p", "przychod", "wplyw")
    private val expenseExact = setOf("dr", "d", "-", "out", "w", "wn", "o", "wydatek", "obciazenie")

    /** true = wpływ, false = wydatek, null = nie wiadomo (dla kolumny „typ”). */
    fun directionOf(text: String): Boolean? {
        val w = words(text).trim()
        if (w.isEmpty()) return null
        if (w in incomeExact) return true
        if (w in expenseExact) return false
        val p = " $w "
        val inc = incomeWords.filter { p.contains(" $it") }.maxOfOrNull { it.length } ?: 0
        val exp = expenseWords.filter { p.contains(" $it") }.maxOfOrNull { it.length } ?: 0
        return when {
            inc == 0 && exp == 0 -> null
            inc >= exp -> true
            else -> false
        }
    }

    /** Podpis układu kolumn — do zapamiętania ręcznych poprawek przy kolejnym imporcie z tego samego banku. */
    fun signature(t: CsvTable): String =
        t.header?.joinToString("|") { fold(it).replace(Regex("[^a-z0-9]"), "") }?.let { "h:$it" }
            ?: "n:${t.columns}:${t.delimiter.code}"

    private val bankSignatures: List<Pair<String, (String, String) -> Boolean>> = listOf(
        "OpenPayments" to { h, _ -> h.contains("sprzedawca") && h.contains("zrodlo") && h.contains("typ") },
        "Revolut" to { h, _ -> h.contains("started date") && h.contains("completed date") },
        "mBank" to { h, p -> p.contains("mbank") || h.contains("#data operacji") },
        "ING Bank Śląski" to { h, p -> h.contains("dane kontrahenta") && h.contains("nr transakcji") || p.contains("ing bank") },
        "PKO BP" to { h, p -> h.contains("saldo po transakcji") && h.contains("opis transakcji") || p.contains("pko bank") },
        "Bank Millennium" to { h, p -> h.contains("na konto/z konta") || h.contains("odbiorca/zleceniodawca") || p.contains("millennium") },
        "Bank Pekao" to { h, p -> h.contains("nadawca / odbiorca") && h.contains("tytulem") || p.contains("pekao") },
        "Santander" to { _, p -> p.contains("santander") },
        "Alior Bank" to { _, p -> p.contains("alior") },
        "Credit Agricole" to { _, p -> p.contains("credit agricole") },
        "BNP Paribas" to { _, p -> p.contains("bnp paribas") },
        "Citi Handlowy" to { _, p -> p.contains("citi") },
        "Nest Bank" to { _, p -> p.contains("nest bank") },
        "VeloBank" to { _, p -> p.contains("velobank") },
        "Bank Pocztowy" to { _, p -> p.contains("bank pocztowy") },
        "Wise" to { h, _ -> h.contains("transferwise id") || h.contains("wise") && h.contains("running balance") },
    )

    fun detectBank(t: CsvTable): String? {
        val h = t.header?.joinToString(";") { fold(it) } ?: ""
        val p = fold(t.preamble.joinToString(" "))
        return bankSignatures.firstOrNull { (_, f) -> f(h, p) }?.first
    }

    /** Rozpoznanie ról kolumn: nazwy nagłówków + zawartość + zależność kwota↔saldo. */
    fun analyze(t: CsvTable, remembered: List<Role>? = null): Analysis {
        val cols = t.columns
        val columnCells = (0 until cols).map { c -> t.rows.take(800).map { it.getOrElse(c) { "" } } }
        val stats = columnCells.map { Stats(it) }
        val header = t.header ?: List(cols) { "" }
        val roles = MutableList(cols) { Role.IGNORE }
        val assigned = BooleanArray(cols)
        fun hs(c: Int, r: Role) = headerScore(r, header[c])
        fun set(c: Int, r: Role) { roles[c] = r; assigned[c] = true }

        // 1. Data: kolumna z datami, najlepiej „data transakcji/operacji”.
        val dateCols = (0 until cols).filter { stats[it].date >= 0.6 && stats[it].n > 0 }
        dateCols.maxByOrNull { c -> hs(c, Role.DATE) * 10 + stats[c].date * 5 + stats[c].fill * 3 - c * 0.01 }?.let { set(it, Role.DATE) }
        dateCols.forEach { if (!assigned[it]) set(it, Role.IGNORE) }

        // 2. Kolumny pieniężne.
        val moneyCols = (0 until cols).filter { c ->
            !assigned[c] && stats[c].n > 0 && stats[c].money >= 0.7 && stats[c].account < 0.5 &&
                (stats[c].strictMoney >= 0.3 || listOf(Role.AMOUNT, Role.DEBIT, Role.CREDIT, Role.BALANCE, Role.FEE).any { r -> hs(c, r) >= 3 })
        }
        val parsedMoney = moneyCols.associateWith { c ->
            val dec = Amounts.detectDecimal(columnCells[c])
            t.rows.map { Amounts.parse(it[c], dec) }
        }
        val byHeader = moneyCols.associateWith { c ->
            listOf(Role.AMOUNT, Role.DEBIT, Role.CREDIT, Role.BALANCE, Role.FEE).maxByOrNull { hs(c, it) }!!.takeIf { hs(c, it) >= 3 }
        }
        fun firstWith(r: Role) = moneyCols.filter { !assigned[it] && byHeader[it] == r }.maxByOrNull { hs(it, r) * 10 + stats[it].fill }
        firstWith(Role.BALANCE)?.let { set(it, Role.BALANCE) }
        firstWith(Role.FEE)?.let { set(it, Role.FEE) }
        val debit = firstWith(Role.DEBIT)
        val credit = firstWith(Role.CREDIT)
        val amount = firstWith(Role.AMOUNT)
        when {
            debit != null && credit != null -> { set(debit, Role.DEBIT); set(credit, Role.CREDIT) }
            amount != null -> set(amount, Role.AMOUNT)
            debit != null -> set(debit, Role.DEBIT)
            credit != null -> set(credit, Role.CREDIT)
        }
        val free = moneyCols.filter { !assigned[it] && byHeader[it] == null }
        if (roles.none { it == Role.AMOUNT || it == Role.DEBIT || it == Role.CREDIT } && free.isNotEmpty()) {
            // Para kolumn wzajemnie się wykluczających = obciążenia i uznania.
            val pair = free.flatMap { a -> free.filter { it > a }.map { a to it } }.firstOrNull { (a, b) ->
                val pa = parsedMoney[a]!!; val pb = parsedMoney[b]!!
                val both = pa.indices.count { pa[it] != null && pb[it] != null && pa[it] != 0L && pb[it] != 0L }
                val any = pa.indices.count { pa[it] != null || pb[it] != null }
                any > 0 && both <= any / 10 && stats[a].fill < 0.95 && stats[b].fill < 0.95
            }
            if (pair != null) {
                val aNeg = stats[pair.first].negative; val bNeg = stats[pair.second].negative
                if (bNeg > aNeg) { set(pair.second, Role.DEBIT); set(pair.first, Role.CREDIT) } else { set(pair.first, Role.DEBIT); set(pair.second, Role.CREDIT) }
            } else {
                val amt = free.maxByOrNull { c -> stats[c].negative * 2 + stats[c].fill - c * 0.01 }!!
                set(amt, Role.AMOUNT)
            }
        }
        // Saldo bez nagłówka: kolumna, w której różnice kolejnych wierszy równają się kwotom.
        val amountCol = roles.indexOf(Role.AMOUNT)
        if (amountCol >= 0 && Role.BALANCE !in roles) {
            moneyCols.filter { !assigned[it] }.maxByOrNull { balanceFit(parsedMoney[amountCol]!!, parsedMoney[it]!!) }?.let { b ->
                if (balanceFit(parsedMoney[amountCol]!!, parsedMoney[b]!!) >= 0.6) set(b, Role.BALANCE)
            }
        }
        moneyCols.forEach { if (!assigned[it]) set(it, Role.IGNORE) }

        // 3. Kolumny tekstowe.
        val textRoles = listOf(Role.CURRENCY, Role.CATEGORY, Role.STATUS, Role.TYPE, Role.MERCHANT, Role.DESCRIPTION)
        for (c in 0 until cols) {
            if (assigned[c]) continue
            val s = stats[c]
            if (s.account >= 0.6) { set(c, Role.IGNORE); continue }
            val ignoreScore = hs(c, Role.IGNORE)
            val best = textRoles.maxByOrNull { hs(c, it) }!!
            val score = hs(c, best)
            when {
                score >= 3 && score >= ignoreScore -> {
                    val ok = when (best) {
                        Role.CURRENCY -> s.currency >= 0.6 || s.n == 0
                        Role.TYPE -> s.letters >= 0.5 || s.signWords >= 0.5 || s.n == 0
                        else -> true
                    }
                    val single = best != Role.DESCRIPTION && roles.contains(best)
                    set(c, if (ok && !single) best else if (ok && best == Role.MERCHANT) Role.DESCRIPTION else Role.IGNORE)
                }
                s.currency >= 0.9 && Role.CURRENCY !in roles -> set(c, Role.CURRENCY)
            }
        }
        // Kolumny bez rozpoznanego nagłówka: typ (mało różnych wartości ze słowami „uznanie/obciążenie”), opisy.
        for (c in 0 until cols) {
            if (assigned[c]) continue
            val s = stats[c]
            val unnamed = header[c].isBlank()
            when {
                s.n == 0 -> set(c, Role.IGNORE)
                s.signWords >= 0.8 && s.distinct <= 12 && Role.TYPE !in roles -> set(c, Role.TYPE)
                s.letters >= 0.6 && unnamed && hs(c, Role.IGNORE) == 0.0 -> {
                    val prevDesc = (c - 1 downTo 0).firstOrNull { assigned[it] && roles[it] != Role.IGNORE || header[it].isNotBlank() }
                        ?.let { roles[it] == Role.DESCRIPTION } ?: false
                    if (t.header == null || prevDesc) set(c, Role.DESCRIPTION)
                }
            }
        }
        // Bez nagłówka: najdłuższy tekst to opis, krótszy i zróżnicowany — kontrahent.
        if (t.header == null) {
            val texts = (0 until cols).filter { roles[it] == Role.DESCRIPTION }
            if (texts.size >= 2 && Role.MERCHANT !in roles) {
                val shortest = texts.minByOrNull { stats[it].avgLen }!!
                if (stats[shortest].distinct >= 2) roles[shortest] = Role.MERCHANT
            }
        }
        if (Role.DESCRIPTION !in roles && Role.MERCHANT !in roles) {
            (0 until cols).filter { roles[it] == Role.IGNORE && stats[it].letters >= 0.6 && stats[it].account < 0.3 && hs(it, Role.IGNORE) < 5 }
                .maxByOrNull { stats[it].avgLen * stats[it].distinct }?.let { roles[it] = Role.DESCRIPTION }
        }

        val decimalCol = roles.indexOfFirst { it == Role.AMOUNT || it == Role.DEBIT || it == Role.CREDIT }
        val decimal = if (decimalCol >= 0) Amounts.detectDecimal(columnCells[decimalCol]) else ','
        val dateCol = roles.indexOf(Role.DATE)
        val order = if (dateCol >= 0) Dates.detectOrder(columnCells[dateCol]) else DateOrder.DMY
        val finalRoles = if (remembered != null && remembered.size == cols) remembered else roles
        return Analysis(t, finalRoles, detectBank(t), order, decimal, signature(t))
    }

    /** Odsetek wierszy, w których różnica sąsiednich sald równa się kwocie (w dowolnej kolejności sortowania). */
    internal fun balanceFit(amount: List<Long?>, balance: List<Long?>): Double {
        var hits = 0; var tests = 0
        for (i in 1 until minOf(amount.size, balance.size)) {
            val b0 = balance[i - 1] ?: continue; val b1 = balance[i] ?: continue
            val a0 = amount[i - 1] ?: continue; val a1 = amount[i] ?: continue
            tests++
            val d = abs(b0 - b1)
            if (d == abs(a0) || d == abs(a1)) hits++
        }
        return if (tests < 2) 0.0 else hits.toDouble() / tests
    }

    private val pendingWords = listOf(
        "odrzuc", "anulow", "reverted", "declined", "failed", "cancel", "pending", "oczekuj", "w realizacji", "blokad",
        "niezrealiz", "wstrzym", "rejected",
    ).map(::fold)
    private val transferWords = listOf(
        "przelew wlasny", "przelew na rachunek wlasny", "na wlasny rachunek", "na rachunek wlasny", "miedzy rachunkami",
        "miedzy wlasnymi", "wlasne konto", "konto wlasne", "rachunek wlasny", "transfer between", "own account", "to savings",
        "przewalutowanie", "wymiana walut", "kantor", "exchanged to", "exchange", "topup", "top up", "lokata", "skarbonk",
        "cel oszczednosciowy", "konto oszczednosciowe", "rachunek oszczednosciowy", "oszczedzam", "savings", "pocket",
        "splata karty", "splata zadluzenia karty", "splata kredytu karty",
    ).map(::fold)

    private fun containsAny(text: String, list: List<String>): Boolean {
        val w = words(text)
        return list.any { w.contains(" $it") }
    }

    /** Odczyt operacji według ról kolumn. */
    fun read(a: Analysis, options: ReadOptions = ReadOptions()): ReadResult {
        val t = a.table
        val r = a.roles
        fun col(role: Role) = r.indexOf(role).takeIf { it >= 0 }
        val cDate = col(Role.DATE); val cAmount = col(Role.AMOUNT); val cDebit = col(Role.DEBIT); val cCredit = col(Role.CREDIT)
        val cBalance = col(Role.BALANCE); val cFee = col(Role.FEE); val cCurrency = col(Role.CURRENCY); val cMerchant = col(Role.MERCHANT)
        val cType = col(Role.TYPE); val cCategory = col(Role.CATEGORY); val cStatus = col(Role.STATUS)
        val cDesc = r.indices.filter { r[it] == Role.DESCRIPTION }
        if (cDate == null || (cAmount == null && cDebit == null && cCredit == null)) {
            return ReadResult(emptyList(), t.rows.size, SignMode.ALL_EXPENSES, 0)
        }
        fun dec(c: Int?) = c?.let { Amounts.detectDecimal(t.rows.take(500).map { row -> row[it] }) } ?: a.decimal
        val decAmount = dec(cAmount); val decDebit = dec(cDebit); val decCredit = dec(cCredit)
        val decBalance = dec(cBalance); val decFee = dec(cFee)

        val amounts = t.rows.map { row -> cAmount?.let { Amounts.parse(row[it], decAmount) } }
        val balances = t.rows.map { row -> cBalance?.let { Amounts.parse(row[it], decBalance) } }
        val types = t.rows.map { row -> cType?.let { row[it] } ?: "" }

        // Jak ustalić znak, gdy jest jedna kolumna kwoty.
        val mode = when {
            cDebit != null || cCredit != null -> SignMode.DEBIT_CREDIT
            amounts.any { (it ?: 0) < 0 } -> SignMode.SIGNED
            cType != null && types.count { directionOf(it) != null } >= maxOf(1, types.count { it.isNotBlank() } * 6 / 10) -> SignMode.TYPE
            cBalance != null && balanceFit(amounts, balances) >= 0.5 -> SignMode.BALANCE
            else -> SignMode.ALL_EXPENSES
        }
        // Kolejność wierszy dla salda: od najnowszych (saldo[i] = saldo[i+1] + kwota[i]) czy od najstarszych.
        val newestFirst = if (mode == SignMode.BALANCE) {
            var nf = 0; var of = 0
            for (i in amounts.indices) {
                val a0 = amounts[i] ?: continue; val b = balances[i] ?: continue
                balances.getOrNull(i + 1)?.let { if (abs(b - it) == abs(a0)) nf++ }
                balances.getOrNull(i - 1)?.let { if (abs(b - it) == abs(a0)) of++ }
            }
            nf >= of
        } else true

        val out = ArrayList<BankRow>()
        var ignored = 0; var foreign = 0
        for ((i, row) in t.rows.withIndex()) {
            val date = Dates.parse(row[cDate], a.dateOrder)
            val parsed: Long? = when (mode) {
                SignMode.DEBIT_CREDIT -> {
                    val d = cDebit?.let { Amounts.parse(row[it], decDebit) }?.takeIf { it != 0L }
                    val c = cCredit?.let { Amounts.parse(row[it], decCredit) }?.takeIf { it != 0L }
                    val fromAmount = amounts[i]
                    when {
                        d != null && c != null -> abs(c) - abs(d)
                        d != null -> -abs(d)
                        c != null -> abs(c)
                        fromAmount != null -> fromAmount
                        else -> null
                    }
                }
                SignMode.SIGNED -> amounts[i]
                SignMode.TYPE -> amounts[i]?.let { v -> if (directionOf(types[i]) == true) abs(v) else -abs(v) }
                SignMode.BALANCE -> amounts[i]?.let { v ->
                    val b = balances[i]
                    val other = if (newestFirst) balances.getOrNull(i + 1) else balances.getOrNull(i - 1)
                    val up = when {
                        b == null -> null
                        other != null && abs(b - other) == abs(v) -> b > other
                        // Brak sąsiada (pierwszy/ostatni wiersz): typ operacji, a w ostateczności wydatek.
                        else -> directionOf(types[i])
                    } ?: false
                    if (up) abs(v) else -abs(v)
                }
                SignMode.ALL_EXPENSES -> amounts[i]?.let { -abs(it) }
            }
            if (date == null || parsed == null) { ignored++; continue }
            var value: Long = parsed
            val fee = cFee?.let { Amounts.parse(row[it], decFee) }
            if (fee != null && fee != 0L) value -= abs(fee)
            if (options.invert) value = -value
            val currency = (cCurrency?.let { row[it].trim().uppercase().takeIf { s -> Amounts.isCurrencyCode(s) } }
                ?: cAmount?.let { Amounts.currencyIn(row[it]) } ?: cDebit?.let { Amounts.currencyIn(row[it]) }
                ?: cCredit?.let { Amounts.currencyIn(row[it]) })?.let { if (it == "ZŁ") "PLN" else it }
            if (currency != null && currency != "PLN") foreign++
            val descriptions = cDesc.map { row[it] }.filter { it.isNotBlank() }
            val status = cStatus?.let { row[it] } ?: ""
            val counterparty = cMerchant?.let { row[it] } ?: ""
            val all = listOf(types[i], status, counterparty, descriptions.joinToString(" ")).joinToString(" ")
            val reason = when {
                value == 0L -> SkipReason.ZERO
                status.isNotBlank() && containsAny(status, pendingWords) -> SkipReason.PENDING
                containsAny(types[i] + " " + descriptions.joinToString(" "), pendingWords.filter { it == "blokad" || it == "odrzuc" || it == "anulow" }) &&
                    !containsAny(all, listOf("zwolnienie blokady").map(::fold)) -> SkipReason.PENDING
                containsAny(all, transferWords) -> SkipReason.TRANSFER
                else -> null
            }
            out += BankRow(
                line = t.lineNumbers[i], date = date.toEpochDay(), amount = value, currency = currency,
                counterparty = counterparty, descriptions = descriptions, type = types[i],
                bankCategory = cCategory?.let { row[it] } ?: "", reason = reason,
            )
        }
        return ReadResult(out, ignored, mode, foreign)
    }
}
