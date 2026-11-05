package pl.openpayments.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Kopia zapasowa całej bazy jako JSON (bez zdjęć paragonów).
 * Format jest wersjonowany, żeby przyszłe wersje aplikacji mogły czytać starsze kopie.
 */
object Backup {
    const val FORMAT = 1

    data class Snapshot(
        val categories: List<Category>,
        val transactions: List<Transaction>,
        val items: List<ReceiptItem>,
        val budgets: List<Budget>,
        val subscriptions: List<Subscription>,
        val rules: List<CategoryRule>,
    )

    private inline fun <T> JSONArray.map(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }
    private fun <T> arr(list: List<T>, f: (T) -> JSONObject) = JSONArray().apply { list.forEach { put(f(it)) } }
    private fun JSONObject.optLongOrNull(k: String): Long? = if (isNull(k) || !has(k)) null else getLong(k)
    private fun JSONObject.optStr(k: String): String? = if (isNull(k) || !has(k)) null else getString(k)

    fun encode(s: Snapshot, appVersion: String): String = JSONObject().apply {
        put("app", "OpenPayments"); put("format", FORMAT); put("version", appVersion)
        put("created", System.currentTimeMillis())
        put("categories", arr(s.categories) { JSONObject().put("id", it.id).put("name", it.name).put("icon", it.icon).put("color", it.color).put("isIncome", it.isIncome) })
        put("transactions", arr(s.transactions) {
            JSONObject().put("id", it.id).put("amount", it.amount).put("isIncome", it.isIncome).put("merchant", it.merchant)
                .put("note", it.note).put("categoryId", it.categoryId ?: JSONObject.NULL).put("date", it.date)
                .put("source", it.source.name).put("subscriptionId", it.subscriptionId ?: JSONObject.NULL)
        })
        put("items", arr(s.items) { JSONObject().put("id", it.id).put("transactionId", it.transactionId).put("name", it.name).put("quantity", it.quantity).put("price", it.price) })
        put("budgets", arr(s.budgets) { JSONObject().put("id", it.id).put("name", it.name).put("limit", it.limit).put("categoryId", it.categoryId ?: JSONObject.NULL).put("alertPercent", it.alertPercent) })
        put("subscriptions", arr(s.subscriptions) {
            JSONObject().put("id", it.id).put("name", it.name).put("amount", it.amount).put("categoryId", it.categoryId ?: JSONObject.NULL)
                .put("cycle", it.cycle.name).put("nextDate", it.nextDate).put("autoBook", it.autoBook).put("active", it.active)
        })
        put("rules", arr(s.rules) { JSONObject().put("id", it.id).put("keyword", it.keyword).put("categoryId", it.categoryId) })
    }.toString()

    fun decode(json: String): Snapshot {
        val o = JSONObject(json)
        require(o.optString("app") == "OpenPayments") { "To nie jest kopia zapasowa OpenPayments" }
        require(o.optInt("format") in 1..FORMAT) { "Kopia pochodzi z nowszej wersji aplikacji – zaktualizuj OpenPayments" }
        val cats = o.getJSONArray("categories").map { Category(it.getLong("id"), it.getString("name"), it.getString("icon"), it.getLong("color"), it.getBoolean("isIncome")) }
        val catIds = cats.map { it.id }.toSet()
        val tx = o.getJSONArray("transactions").map {
            Transaction(
                id = it.getLong("id"), amount = it.getLong("amount"), isIncome = it.getBoolean("isIncome"),
                merchant = it.getString("merchant"), note = it.optString("note"),
                categoryId = it.optLongOrNull("categoryId")?.takeIf { c -> c in catIds }, date = it.getLong("date"),
                source = runCatching { TxSource.valueOf(it.getString("source")) }.getOrDefault(TxSource.MANUAL),
                subscriptionId = it.optLongOrNull("subscriptionId"),
            )
        }
        val txIds = tx.map { it.id }.toSet()
        return Snapshot(
            categories = cats,
            transactions = tx,
            items = o.getJSONArray("items").map { ReceiptItem(it.getLong("id"), it.getLong("transactionId"), it.getString("name"), it.getDouble("quantity"), it.getLong("price")) }
                .filter { it.transactionId in txIds },
            budgets = o.getJSONArray("budgets").map { Budget(it.getLong("id"), it.getString("name"), it.getLong("limit"), it.optLongOrNull("categoryId"), it.getInt("alertPercent")) },
            subscriptions = o.getJSONArray("subscriptions").map {
                Subscription(it.getLong("id"), it.getString("name"), it.getLong("amount"), it.optLongOrNull("categoryId"),
                    runCatching { Cycle.valueOf(it.getString("cycle")) }.getOrDefault(Cycle.MONTHLY), it.getLong("nextDate"),
                    it.getBoolean("autoBook"), it.getBoolean("active"))
            },
            rules = o.getJSONArray("rules").map { CategoryRule(it.getLong("id"), it.getString("keyword"), it.getLong("categoryId")) }
                .filter { it.categoryId in catIds },
        )
    }

    @Suppress("unused") private fun unused(o: JSONObject) = o.optStr("x")
}
