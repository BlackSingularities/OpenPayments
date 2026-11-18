package pl.openpayments.domain.bank

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Surowy odczyt pliku CSV z banku: rozpoznanie kodowania (UTF-8, UTF-16, Windows-1250, ISO-8859-2),
 * separatora (; , tab |) i pól w cudzysłowach, także wielowierszowych.
 */
object CsvReader {

    data class Decoded(val text: String, val charset: String)

    fun decode(bytes: ByteArray): Decoded {
        fun b(i: Int) = bytes[i].toInt() and 0xFF
        if (bytes.size >= 3 && b(0) == 0xEF && b(1) == 0xBB && b(2) == 0xBF)
            return Decoded(String(bytes, 3, bytes.size - 3, Charsets.UTF_8), "UTF-8")
        if (bytes.size >= 2 && b(0) == 0xFF && b(1) == 0xFE) return Decoded(String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE), "UTF-16")
        if (bytes.size >= 2 && b(0) == 0xFE && b(1) == 0xFF) return Decoded(String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE), "UTF-16")
        // UTF-16 bez BOM: co drugi bajt zerowy.
        val sample = minOf(bytes.size, 4000)
        if (sample >= 8) {
            var odd = 0; var even = 0
            for (i in 0 until sample) if (bytes[i].toInt() == 0) { if (i % 2 == 1) odd++ else even++ }
            if (odd > sample / 4) return Decoded(String(bytes, Charsets.UTF_16LE), "UTF-16")
            if (even > sample / 4) return Decoded(String(bytes, Charsets.UTF_16BE), "UTF-16")
        }
        try {
            val dec = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            return Decoded(dec.decode(ByteBuffer.wrap(bytes)).toString(), "UTF-8")
        } catch (_: CharacterCodingException) {
        }
        // Polskie banki często eksportują w Windows-1250; wybieramy kodowanie z najbardziej „polskim” wynikiem.
        val variants = listOf("windows-1250" to "Windows-1250", "ISO-8859-2" to "ISO-8859-2").mapNotNull { (name, label) ->
            runCatching { label to String(bytes, Charset.forName(name)) }.getOrNull()
        }
        val best = variants.maxByOrNull { polishScore(it.second) } ?: ("ISO-8859-1" to String(bytes, Charsets.ISO_8859_1))
        return Decoded(best.second, best.first)
    }

    private const val PL_LETTERS = "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ"
    private const val WRONG_1250 = "±¶¦ˇ¬"  // polskie litery z ISO-8859-2 czytane jako Windows-1250
    private const val WRONG_8859 = "šĽ"     // polskie litery z Windows-1250 czytane jako ISO-8859-2

    internal fun polishScore(t: String): Int {
        var s = 0
        for (ch in t) when {
            ch in PL_LETTERS -> s += 2
            ch.code in 0x80..0x9F -> s -= 6
            ch in WRONG_1250 || ch in WRONG_8859 -> s -= 3
        }
        return s
    }

    /** Separator: ten, przy którym wiersze mają najbardziej powtarzalną liczbę pól. */
    fun detectDelimiter(text: String): Char {
        val sample = text.lineSequence().filter { it.isNotBlank() }.take(120).joinToString("\n")
        var best = ';'; var bestScore = -1.0
        for (d in listOf(';', '\t', ',', '|')) {
            val rows = parse(sample, d).filter { r -> r.any { it.isNotBlank() } }
            if (rows.isEmpty()) continue
            val counts = rows.map { r -> r.size }.filter { it > 1 }
            if (counts.isEmpty()) continue
            val modal = counts.groupingBy { it }.eachCount().maxBy { it.value }
            val score = modal.value.toDouble() / rows.size * 10 + minOf(modal.key, 12) * 0.15 + counts.size.toDouble() / rows.size * 2
            if (score > bestScore + 1e-9) { best = d; bestScore = score }
        }
        return best
    }

    /** Linia „sep=;” dopisywana przez Excela. */
    fun explicitDelimiter(text: String): Pair<Char, String>? {
        val first = text.lineSequence().firstOrNull()?.trim()?.trim('"') ?: return null
        if (!first.startsWith("sep=", true) || first.length != 5) return null
        return first[4] to text.substringAfter('\n', "")
    }

    /** Podział na wiersze i pola zgodnie z RFC 4180 (cudzysłowy, "" wewnątrz pola, pola wielowierszowe). */
    fun parse(text: String, delim: Char): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val f = StringBuilder()
        var inQ = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQ) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { f.append('"'); i++ } else inQ = false
                } else f.append(c)
            } else when {
                c == '"' && f.isBlank() -> { inQ = true; f.setLength(0) }
                c == delim -> { row.add(f.toString()); f.setLength(0) }
                c == '\r' || c == '\n' -> {
                    row.add(f.toString()); f.setLength(0); rows.add(row); row = ArrayList()
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                else -> f.append(c)
            }
            i++
        }
        if (f.isNotEmpty() || row.isNotEmpty()) { row.add(f.toString()); rows.add(row) }
        return rows
    }

    /** Porządkuje komórkę: spacje twarde, zapis Excela ="123", zbędne cudzysłowy. */
    fun cell(raw: String): String {
        var s = raw.replace(' ', ' ').replace(' ', ' ').replace(' ', ' ').replace('​', ' ').trim()
        if (s.length >= 3 && s.startsWith("=\"") && s.endsWith("\"")) s = s.substring(2, s.length - 1)
        if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length - 1)
        if (s.startsWith("'") && s.drop(1).any { it.isDigit() } && s.drop(1).all { it.isDigit() || it == ' ' }) s = s.drop(1)
        return s.trim()
    }
}
