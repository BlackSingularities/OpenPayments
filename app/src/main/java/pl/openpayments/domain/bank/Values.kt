package pl.openpayments.domain.bank

import java.text.Normalizer
import java.time.LocalDate

/** Małe litery bez polskich znaków; zachowuje długość tekstu (indeksy pasują do oryginału). */
internal fun fold(s: String): String {
    val sb = StringBuilder(s.length)
    for (c in s) sb.append(foldChar(c))
    return sb.toString()
}

private fun foldChar(c: Char): Char {
    val l = c.lowercaseChar()
    return when (l) {
        'ą' -> 'a'; 'ć' -> 'c'; 'ę' -> 'e'; 'ł' -> 'l'; 'ń' -> 'n'; 'ó' -> 'o'; 'ś' -> 's'; 'ź', 'ż' -> 'z'
        else -> if (l.code < 128) l else Normalizer.normalize(l.toString(), Normalizer.Form.NFD).firstOrNull() ?: l
    }
}

/** Tekst do dopasowań słownikowych: bez znaków specjalnych, ze spacjami na brzegach. */
internal fun words(s: String): String =
    " " + fold(s).replace('_', ' ').replace(Regex("[^a-z0-9&+]+"), " ").trim() + " "

/** Kolejność dnia i miesiąca w datach typu 03/11/2026. */
enum class DateOrder(val label: String) { DMY("DD.MM.RRRR"), MDY("MM/DD/RRRR"), YMD("RRRR-MM-DD") }

object Dates {
    private val ymd = Regex("^(\\d{4})[-./ ](\\d{1,2})[-./ ](\\d{1,2})(?:$|[T\\s,;])")
    private val dmy = Regex("^(\\d{1,2})[-./ ](\\d{1,2})[-./ ](\\d{4}|\\d{2})(?:$|[T\\s,;])")
    private val compact = Regex("^(\\d{4})(\\d{2})(\\d{2})(?:$|[T\\s])")
    private val named = Regex("^(\\d{1,2})[ .-]+([a-z]{3,})\\.?[ .-]+(\\d{4})")
    private val namedUs = Regex("^([a-z]{3,})\\.?\\s+(\\d{1,2}),?\\s+(\\d{4})")

    private val months = listOf(
        listOf("sty", "jan"), listOf("lut", "feb"), listOf("mar"), listOf("kwi", "apr"), listOf("maj", "may"),
        listOf("cze", "jun"), listOf("lip", "jul"), listOf("sie", "aug"), listOf("wrz", "sep"),
        listOf("paz", "oct", "pa"), listOf("lis", "nov"), listOf("gru", "dec"),
    )

    private fun month(name: String): Int? {
        val n = fold(name)
        months.forEachIndexed { i, keys -> if (keys.any { n.startsWith(it) && it.length >= 3 }) return i + 1 }
        return null
    }

    private fun year(y: String): Int = y.toInt().let { if (y.length == 2) 2000 + it else it }

    private fun of(y: Int, m: Int, d: Int): LocalDate? =
        if (y in 1990..2100 && m in 1..12 && d in 1..31) runCatching { LocalDate.of(y, m, d) }.getOrNull() else null

    /** Daty „17.08.2026”, „2026-08-17 14:22”, „08/17/2026”, „20260817”, „17 sie 2026”. */
    fun parse(raw: String, order: DateOrder = DateOrder.DMY): LocalDate? {
        val s = fold(raw.trim().trimStart('#'))
        if (s.isEmpty() || s.length > 40) return null
        ymd.find(s)?.let { m -> return of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
        dmy.find(s)?.let { m ->
            val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt(); val y = year(m.groupValues[3])
            return if (order == DateOrder.MDY) of(y, a, b) ?: of(y, b, a) else of(y, b, a) ?: of(y, a, b)
        }
        compact.find(s)?.let { m -> return of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
        named.find(s)?.let { m -> month(m.groupValues[2])?.let { mo -> return of(m.groupValues[3].toInt(), mo, m.groupValues[1].toInt()) } }
        namedUs.find(s)?.let { m -> month(m.groupValues[1])?.let { mo -> return of(m.groupValues[3].toInt(), mo, m.groupValues[2].toInt()) } }
        return null
    }

    /** Kolejność dnia i miesiąca dla całej kolumny: wartość > 12 rozstrzyga. */
    fun detectOrder(cells: List<String>): DateOrder {
        var dayFirst = 0; var monthFirst = 0; var isoLike = 0
        for (c in cells) {
            val s = c.trim()
            if (ymd.containsMatchIn(s) || compact.containsMatchIn(s)) { isoLike++; continue }
            val m = dmy.find(s) ?: continue
            val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
            if (a > 12 && b <= 12) dayFirst++
            if (b > 12 && a <= 12) monthFirst++
        }
        return when {
            monthFirst > dayFirst -> DateOrder.MDY
            dayFirst == 0 && monthFirst == 0 && isoLike > 0 -> DateOrder.YMD
            else -> DateOrder.DMY
        }
    }
}

object Amounts {
    private val currencyToken = Regex("(?i)(PLN|EUR|USD|GBP|CHF|CZK|NOK|SEK|DKK|HUF|UAH|JPY|CAD|AUD|zł|zl|€|\\$|£)")
    private val currencyCode = Regex("(?i)^(PLN|EUR|USD|GBP|CHF|CZK|NOK|SEK|DKK|HUF|UAH|JPY|CAD|AUD|RON|BGN|TRY|zł)$")
    private val strictShape = Regex("^\\d{1,3}(?:[.,'’]\\d{3})*[.,]\\d{1,2}$|^\\d+[.,]\\d{1,2}$")

    fun isCurrencyCode(s: String) = currencyCode.matches(s.trim())

    /** Waluta zapisana w komórce z kwotą („-12,34 PLN”). */
    fun currencyIn(s: String): String? = currencyToken.find(s)?.value?.uppercase()?.let {
        when (it) { "ZŁ", "ZL" -> "PLN"; "€" -> "EUR"; "$" -> "USD"; "£" -> "GBP"; else -> it }
    }

    private data class Prepared(val digits: String, val negative: Boolean)

    private fun prepare(raw: String): Prepared? {
        var s = raw.trim()
        if (s.isEmpty() || s.length > 32) return null
        s = currencyToken.replace(s, " ")
        s = s.replace(Regex("[\\s  ]"), "")
        if (s.isEmpty()) return null
        var neg = false
        if (s.startsWith("(") && s.endsWith(")")) { neg = true; s = s.substring(1, s.length - 1) }
        while (s.isNotEmpty() && s[0] in "-−–+") { if (s[0] != '+') neg = !neg; s = s.substring(1) }
        while (s.isNotEmpty() && s.last() in "-−–+") { if (s.last() != '+') neg = !neg; s = s.dropLast(1) }
        if (s.isEmpty() || !s[0].isDigit() || !s.last().isDigit()) return null
        if (!s.all { it.isDigit() || it in ".,'’" }) return null
        return Prepared(s, neg)
    }

    /** Czy komórka wygląda jak kwota z groszami (np. „1 234,56”, „-12.50 PLN”). */
    fun isStrict(raw: String): Boolean {
        val p = prepare(raw) ?: return false
        return strictShape.matches(p.digits)
    }

    /** Separator dziesiętny w kolumnie: ten, po którym najczęściej stoją dokładnie dwie cyfry na końcu. */
    fun detectDecimal(cells: List<String>): Char {
        var comma = 0; var dot = 0
        for (c in cells) {
            val p = prepare(c) ?: continue
            val d = p.digits
            if (d.length >= 3 && d[d.length - 3] == ',') comma++
            if (d.length >= 3 && d[d.length - 3] == '.') dot++
        }
        return if (dot > comma) '.' else ','
    }

    /** Kwota w groszach ze znakiem; null, gdy komórka nie jest kwotą. */
    fun parse(raw: String, decimal: Char = ','): Long? {
        val p = prepare(raw) ?: return null
        val s = p.digits.replace("'", "").replace("’", "")
        val lastDot = s.lastIndexOf('.'); val lastComma = s.lastIndexOf(',')
        val sep: Char? = when {
            lastDot >= 0 && lastComma >= 0 -> if (lastDot > lastComma) '.' else ','
            lastDot < 0 && lastComma < 0 -> null
            else -> {
                val ch = if (lastDot >= 0) '.' else ','
                val count = s.count { it == ch }
                val after = s.length - s.lastIndexOf(ch) - 1
                when {
                    count > 1 -> null                         // 1.234.567 — tylko grupowanie
                    after == 3 && ch != decimal -> null       // 1,234 przy przecinku jako separatorze tysięcy
                    else -> ch
                }
            }
        }
        val intPart: String; val frac: String
        if (sep == null) { intPart = s.filter { it.isDigit() }; frac = "" } else {
            val idx = s.lastIndexOf(sep)
            intPart = s.substring(0, idx).filter { it.isDigit() }
            frac = s.substring(idx + 1).filter { it.isDigit() }
        }
        if (intPart.isEmpty() && frac.isEmpty()) return null
        if (intPart.length > 13) return null
        val whole = intPart.ifEmpty { "0" }.toLong()
        val grosze = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 10
            2 -> frac.toLong()
            else -> Math.round(("0." + frac).toDouble() * 100)
        }
        val v = whole * 100 + grosze
        return if (p.negative) -v else v
    }
}
