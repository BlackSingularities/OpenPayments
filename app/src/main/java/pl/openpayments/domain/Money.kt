package pl.openpayments.domain

import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

val PL: Locale = Locale.forLanguageTag("pl-PL")

private val moneyFmt = NumberFormat.getNumberInstance(PL).apply {
    minimumFractionDigits = 2; maximumFractionDigits = 2
}
private val moneyFmtShort = NumberFormat.getNumberInstance(PL).apply { maximumFractionDigits = 0 }

/** 284050 -> "2 840,50 zł" */
fun formatMoney(grosze: Long, short: Boolean = false): String {
    val v = grosze / 100.0
    val s = if (short && abs(grosze) % 100 == 0L) moneyFmtShort.format(v) else moneyFmt.format(v)
    return "$s zł".replace(' ', ' ').replace(' ', ' ')
}

/** "12,5" / "12.50" / "1 234,99" -> grosze; null gdy niepoprawne. */
fun parseMoney(input: String): Long? {
    val clean = input.trim().replace(" ", "").replace(" ", "").replace("zł", "", true).replace(',', '.')
    if (clean.isEmpty()) return null
    val d = clean.toDoubleOrNull() ?: return null
    if (d < 0) return null
    return (d * 100).roundToLong()
}

fun moneyInput(grosze: Long): String =
    if (grosze == 0L) "" else "%d,%02d".format(grosze / 100, grosze % 100)

fun today(): Long = LocalDate.now().toEpochDay()
fun Long.toDate(): LocalDate = LocalDate.ofEpochDay(this)

private val dayFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", PL)
private val shortDayFmt = DateTimeFormatter.ofPattern("d MMM", PL)

fun formatDay(epochDay: Long): String {
    val d = epochDay.toDate()
    val t = LocalDate.now()
    return when (d) {
        t -> "Dzisiaj"
        t.minusDays(1) -> "Wczoraj"
        else -> if (d.year == t.year) d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", PL))
            .replaceFirstChar { it.uppercase() } else d.format(dayFmt)
    }
}

fun formatShortDay(epochDay: Long): String = epochDay.toDate().format(shortDayFmt)

fun monthName(ym: YearMonth, withYear: Boolean = true): String {
    val m = ym.month.getDisplayName(TextStyle.FULL_STANDALONE, PL).replaceFirstChar { it.uppercase() }
    return if (withYear && ym.year != YearMonth.now().year) "$m ${ym.year}" else m
}

fun monthShort(ym: YearMonth): String =
    ym.month.getDisplayName(TextStyle.SHORT_STANDALONE, PL).replace(".", "")

fun YearMonth.range(): LongRange = atDay(1).toEpochDay()..atEndOfMonth().toEpochDay()
