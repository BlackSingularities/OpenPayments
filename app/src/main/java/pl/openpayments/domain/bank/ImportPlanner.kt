package pl.openpayments.domain.bank

import pl.openpayments.data.Category
import pl.openpayments.data.CategoryRule
import pl.openpayments.data.Transaction
import pl.openpayments.data.TxSource
import pl.openpayments.domain.Categorizer
import kotlin.math.abs

/** Operacja gotowa do importu: nazwa, kategoria, ewentualny duplikat w aplikacji. */
data class ImportCandidate(
    val row: BankRow,
    val merchant: String,
    val note: String,
    val categoryId: Long?,
    val isIncome: Boolean,
    val amount: Long,
    val date: Long,
    val duplicate: Transaction?,
    val reason: SkipReason?,
) {
    val id: Int get() = row.line
}

/**
 * Zamienia odczytane operacje na transakcje: porządkuje nazwy, dobiera kategorie
 * (wyuczone reguły → kategoria z pliku → słownik sieci → kategoria banku) i wykrywa duplikaty,
 * także zakupy już wpisane z paragonu.
 */
object ImportPlanner {

    /** Kategorie bankowe (mBank, Pekao, Revolut…) → domyślne kategorie aplikacji. */
    private val bankCategoryMap: List<Pair<String, String>> = listOf(
        "zywnosc" to "Zakupy spożywcze", "spozyw" to "Zakupy spożywcze", "groceries" to "Zakupy spożywcze", "supermarket" to "Zakupy spożywcze",
        "paliwo" to "Transport", "transport" to "Transport", "komunikacj" to "Transport", "samochod" to "Transport", "auto" to "Transport",
        "parking" to "Transport", "taxi" to "Transport", "taksowk" to "Transport", "przejazd" to "Transport", "fuel" to "Transport",
        "restaurac" to "Restauracje", "kawiar" to "Restauracje", "jedzenie na miescie" to "Restauracje", "bary" to "Restauracje",
        "fast food" to "Restauracje", "eating out" to "Restauracje", "dostawa jedzenia" to "Restauracje",
        "zdrowie" to "Zdrowie", "uroda" to "Zdrowie", "apteka" to "Zdrowie", "lekarz" to "Zdrowie", "medyczn" to "Zdrowie", "health" to "Zdrowie",
        "odziez" to "Ubrania", "obuwie" to "Ubrania", "ubrani" to "Ubrania", "moda" to "Ubrania", "clothing" to "Ubrania",
        "rozrywk" to "Rozrywka", "multimedia" to "Rozrywka", "kultura" to "Rozrywka", "hobby" to "Rozrywka", "wypoczynek" to "Rozrywka",
        "entertainment" to "Rozrywka", "sport" to "Rozrywka", "podroz" to "Rozrywka", "travel" to "Rozrywka",
        "rachunk" to "Rachunki", "oplaty" to "Rachunki", "media" to "Rachunki", "telefon" to "Rachunki", "internet" to "Rachunki",
        "energia" to "Rachunki", "prad" to "Rachunki", "gaz" to "Rachunki", "czynsz" to "Rachunki", "bills" to "Rachunki", "utilities" to "Rachunki",
        "dom" to "Dom", "ogrod" to "Dom", "wyposazen" to "Dom", "remont" to "Dom", "home" to "Dom",
        "subskrypc" to "Subskrypcje", "abonament" to "Subskrypcje", "subscriptions" to "Subskrypcje",
        "wynagrodz" to "Wynagrodzenie", "pensja" to "Wynagrodzenie", "salary" to "Wynagrodzenie",
    ).map { fold(it.first) to it.second }

    fun plan(
        rows: List<BankRow>,
        categories: List<Category>,
        rules: List<CategoryRule>,
        existing: List<Transaction>,
    ): List<ImportCandidate> {
        val byName = categories.associateBy { fold(it.name).trim() }
        // Każda istniejąca transakcja może „pokryć” tylko jedną operację z pliku.
        val pool = existing.groupBy { it.isIncome to it.amount }.mapValues { it.value.toMutableList() }

        return rows.sortedByDescending { it.date }.map { r ->
            val isIncome = r.amount > 0
            val amount = abs(r.amount)
            val merchant = MerchantName.extract(r.counterparty, r.descriptions, r.type)
            val baseNote = MerchantName.note(merchant, r.descriptions, r.type)
            val note = listOfNotNull(
                r.currency?.takeIf { it != "PLN" }?.let { "Kwota w $it" },
                baseNote.takeIf { it.isNotBlank() },
            ).joinToString(" · ")

            fun ok(id: Long?) = id?.takeIf { cid -> categories.any { it.id == cid && it.isIncome == isIncome } }
            val text = (listOf(merchant, r.counterparty) + r.descriptions).joinToString(" ")
            val bc = r.bankCategory.trim()
            val categoryId = ok(byRules(merchant, rules))
                ?: ok(byRules(r.counterparty, rules))
                ?: bc.takeIf { it.isNotEmpty() }?.let { ok(byName[fold(it)]?.id) }           // własna tabela, eksport OpenPayments
                ?: ok(Categorizer.suggest(merchant, emptyList(), categories))
                ?: bc.takeIf { it.isNotEmpty() }?.let { b ->
                    bankCategoryMap.filter { (k, _) -> words(b).contains(" $k") }
                        .firstNotNullOfOrNull { (_, name) -> ok(byName[fold(name)]?.id) }
                }
                ?: ok(Categorizer.suggest(text, emptyList(), categories))
                ?: if (isIncome) ok(byName[fold("Inne przychody")]?.id) else null

            val dup = findDuplicate(pool[isIncome to amount], r.date, merchant)
            if (dup != null) pool[isIncome to amount]?.remove(dup)
            ImportCandidate(
                row = r, merchant = merchant, note = note, categoryId = categoryId, isIncome = isIncome, amount = amount,
                date = r.date, duplicate = dup, reason = r.reason ?: if (dup != null) SkipReason.DUPLICATE else null,
            )
        }
    }

    /** Reguły wyuczone z poprawek użytkownika (te same, których używa ręczne dodawanie). */
    private fun byRules(text: String, rules: List<CategoryRule>): Long? {
        val n = Categorizer.normalize(text)
        if (n.isEmpty()) return null
        val padded = " $n "
        return rules.sortedByDescending { it.keyword.length }.firstOrNull { padded.contains(it.keyword) }?.categoryId
    }

    private fun similar(a: String, b: String): Boolean {
        val ka = Categorizer.ruleKey(a) ?: return false
        val kb = Categorizer.ruleKey(b) ?: return false
        if (ka == kb) return true
        val fa = words(a); val fb = words(b)
        return fa.contains(" ${fold(kb)} ") || fb.contains(" ${fold(ka)} ") ||
            MerchantName.brandOf(a)?.let { it == MerchantName.brandOf(b) } == true
    }

    /**
     * Ta sama kwota i kierunek, data w oknie 3 dni (bank księguje z opóźnieniem względem paragonu).
     * Wcześniejszy import musi zgadzać się co do dnia; wpis ręczny/paragon — dzień różnicy albo podobna nazwa.
     */
    private fun findDuplicate(candidates: List<Transaction>?, date: Long, merchant: String): Transaction? {
        if (candidates.isNullOrEmpty()) return null
        return candidates.filter { t ->
            val diff = abs(t.date - date)
            when (t.source) {
                TxSource.IMPORT -> diff == 0L
                TxSource.RECURRING -> diff <= 3 && (similar(t.merchant, merchant) || diff <= 1)
                else -> diff <= 1 || diff <= 3 && similar(t.merchant, merchant)
            }
        }.minByOrNull { t -> abs(t.date - date) * 10 - (if (similar(t.merchant, merchant)) 5 else 0) }
    }
}
