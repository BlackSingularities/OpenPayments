package pl.openpayments.domain

import pl.openpayments.data.Category
import pl.openpayments.data.CategoryRule
import pl.openpayments.data.Defaults

/**
 * Automatyczna kategoryzacja. Kolejność:
 * 1. reguły wyuczone od użytkownika (poprawiasz raz — aplikacja pamięta),
 * 2. wbudowany słownik sieci i usług.
 */
object Categorizer {

    fun normalize(merchant: String): String =
        merchant.lowercase(PL).replace(Regex("[^\\p{L}\\p{N}&\\- ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Klucz reguły: pierwsze znaczące słowo nazwy sprzedawcy (np. "lidl sp. z o.o." -> "lidl"). */
    fun ruleKey(merchant: String): String? =
        normalize(merchant).split(' ').firstOrNull { it.length >= 3 }

    fun suggest(merchant: String, rules: List<CategoryRule>, categories: List<Category>): Long? {
        val n = normalize(merchant)
        if (n.isEmpty()) return null
        val padded = " $n "
        rules.sortedByDescending { it.keyword.length }
            .firstOrNull { padded.contains(it.keyword) }
            ?.let { r -> if (categories.any { it.id == r.categoryId }) return r.categoryId }
        val byName = categories.associateBy { it.name }
        return Defaults.keywords.firstOrNull { (k, _) -> padded.contains(k) }
            ?.let { (_, catName) -> byName[catName]?.id }
    }
}
