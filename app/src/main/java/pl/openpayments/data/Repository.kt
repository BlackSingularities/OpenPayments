package pl.openpayments.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import pl.openpayments.domain.Categorizer
import pl.openpayments.domain.range
import pl.openpayments.domain.toDate
import pl.openpayments.notify.Notifier
import java.io.File
import java.time.YearMonth
import java.util.UUID

class Repository(private val ctx: Context, private val db: AppDatabase, val prefs: Prefs) {

    val categories: Flow<List<Category>> = db.categories().observeAll()
    val transactions: Flow<List<Transaction>> = db.transactions().observeAll()
    val budgets: Flow<List<Budget>> = db.budgets().observeAll()
    val subscriptions: Flow<List<Subscription>> = db.subscriptions().observeAll()
    val ruleCount: Flow<Int> = db.rules().observeCount()
    val aliasCount: Flow<Int> = db.aliases().observeCount()

    private suspend fun learnedNames(): Map<String, String> = db.aliases().all().associate { it.key to it.name }

    /** Rozwija skrócone nazwy z paragonu (słownik + wyuczone poprawki) i przypisuje grupy produktów. */
    suspend fun resolveItems(items: List<pl.openpayments.domain.ReceiptParser.Item>): List<ReceiptItem> {
        val learned = learnedNames()
        return items.map { i ->
            val r = pl.openpayments.domain.ProductDictionary.resolve(i.rawName, learned)
            ReceiptItem(name = r.name, quantity = i.quantity, price = i.price, rawName = i.rawName, productGroup = r.group.name)
        }
    }

    fun productGroups(month: YearMonth): Flow<List<GroupSum>> {
        val r = month.range()
        return db.transactions().observeGroups(r.first, r.last)
    }

    suspend fun clearAliases() = db.aliases().clear()

    /** Pozycje zapisane przed wersją 1.3.0 nie mają grupy — uzupełniamy ją raz, ze słownika. */
    suspend fun backfillGroups() {
        db.transactions().itemsWithoutGroup().forEach {
            db.transactions().setGroup(it.id, pl.openpayments.domain.ProductDictionary.groupOf(it.name).name)
        }
    }

    suspend fun seedIfEmpty() {
        if (db.categories().count() == 0) db.categories().insertAll(Defaults.categories)
    }

    suspend fun transaction(id: Long) = db.transactions().get(id)
    suspend fun items(txId: Long) = db.transactions().items(txId)

    suspend fun suggestCategory(merchant: String): Long? =
        Categorizer.suggest(merchant, db.rules().all(), db.categories().all())

    /**
     * Zapis transakcji. Jeśli kategoria różni się od tej, którą zaproponowałby automat,
     * zapamiętujemy regułę — następnym razem kategoria przypisze się sama.
     */
    suspend fun saveTransaction(t: Transaction, items: List<ReceiptItem>? = null): Long {
        // Grupa liczona z ostatecznej nazwy; poprawione nazwy zapamiętujemy na kolejne paragony.
        val withGroups = items?.map { it.copy(productGroup = pl.openpayments.domain.ProductDictionary.groupOf(it.name).name) }
        withGroups?.forEach { item ->
            val raw = item.rawName ?: return@forEach
            val auto = pl.openpayments.domain.ProductDictionary.resolve(raw).name
            val key = pl.openpayments.domain.ProductDictionary.key(raw)
            if (key.isNotBlank() && item.name.isNotBlank() && !item.name.equals(auto, ignoreCase = true)) {
                db.aliases().put(ProductAlias(key, item.name))
            }
        }
        val id = db.transactions().saveWithItems(t, withGroups)
        if (prefs.learnRules && t.categoryId != null) {
            val key = Categorizer.ruleKey(t.merchant)
            if (key != null && suggestCategory(t.merchant) != t.categoryId) {
                db.rules().put(CategoryRule(keyword = key, categoryId = t.categoryId))
            }
        }
        if (!t.isIncome) checkBudgets(YearMonth.from(t.date.toDate()))
        return id
    }

    /** Usuwa transakcję i zwraca jej pozycje, żeby można było cofnąć. Zdjęcie sprząta [cleanupReceipts]. */
    suspend fun deleteTransaction(t: Transaction): List<ReceiptItem> {
        val items = db.transactions().items(t.id)
        db.transactions().delete(t)
        return items
    }

    suspend fun restoreTransaction(t: Transaction, items: List<ReceiptItem>) {
        db.withTransaction {
            db.transactions().insertAll(listOf(t))
            db.transactions().insertAllItems(items)
        }
    }

    fun products(month: YearMonth, limit: Int = 10): Flow<List<ProductStat>> {
        val r = month.range()
        return db.transactions().observeProducts(r.first, r.last, limit)
    }

    /** Usuwa zdjęcia paragonów, do których nie odwołuje się żadna transakcja. */
    suspend fun cleanupReceipts() = withContext(Dispatchers.IO) {
        val used = db.transactions().all().mapNotNull { it.receiptImage }.toSet()
        val cutoff = System.currentTimeMillis() - 24 * 3600 * 1000L
        File(ctx.filesDir, "receipts").listFiles()?.forEach { f ->
            if (f.absolutePath !in used && f.lastModified() < cutoff) f.delete()
        }
    }

    suspend fun exportBackup(uri: Uri) = withContext(Dispatchers.IO) {
        val snap = Backup.Snapshot(
            db.categories().all(), db.transactions().all(), db.transactions().allItems(),
            db.budgets().all(), db.subscriptions().all(), db.rules().all(), db.aliases().all(),
        )
        ctx.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use {
            it.write(Backup.encode(snap, pl.openpayments.BuildConfig.VERSION_NAME))
        }
        snap.transactions.size
    }

    /** Zastępuje wszystkie dane zawartością kopii. Zwraca liczbę przywróconych transakcji. */
    suspend fun importBackup(uri: Uri): Int = withContext(Dispatchers.IO) {
        val json = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        val s = Backup.decode(json)
        db.withTransaction {
            db.clearAllTables()
            db.categories().insertAll(s.categories)
            db.transactions().insertAll(s.transactions)
            db.transactions().insertAllItems(s.items)
            db.budgets().insertAll(s.budgets)
            db.subscriptions().insertAll(s.subscriptions)
            db.rules().insertAll(s.rules)
            db.aliases().insertAll(s.aliases)
        }
        s.transactions.size
    }

    suspend fun spent(month: YearMonth, categoryId: Long?): Long {
        val r = month.range()
        return db.transactions().spent(r.first, r.last, categoryId)
    }

    suspend fun saveBudget(b: Budget) { db.budgets().upsert(b); checkBudgets(YearMonth.now()) }
    suspend fun deleteBudget(b: Budget) = db.budgets().delete(b)

    suspend fun saveSubscription(s: Subscription) { db.subscriptions().upsert(s); processDueSubscriptions() }
    suspend fun deleteSubscription(s: Subscription) = db.subscriptions().delete(s)

    suspend fun saveCategory(c: Category) = db.categories().upsert(c)
    suspend fun deleteCategory(c: Category) = db.categories().delete(c)
    suspend fun clearRules() = db.rules().clear()

    /** Ostrzeżenie zanim przekroczysz limit (próg alertPercent) i po przekroczeniu. */
    private suspend fun checkBudgets(month: YearMonth) {
        if (!prefs.budgetAlerts || month != YearMonth.now()) return
        for (b in db.budgets().all()) {
            val spent = spent(month, b.categoryId)
            val pct = if (b.limit > 0) (spent * 100 / b.limit).toInt() else 0
            val level = when {
                pct >= 100 -> 100
                pct >= b.alertPercent -> b.alertPercent
                else -> 0
            }
            val key = "budget_${b.id}_${month}"
            if (level > prefs.alertLevel(key)) {
                prefs.setAlertLevel(key, level)
                Notifier.budget(ctx, b, spent, pct)
            }
        }
    }

    /** Księguje subskrypcje, których termin minął, i przesuwa datę kolejnej płatności. */
    suspend fun processDueSubscriptions(): Int {
        val today = pl.openpayments.domain.today()
        var booked = 0
        for (s in db.subscriptions().due(today)) {
            var next = s.nextDate
            while (next <= today) {
                if (s.autoBook) {
                    db.transactions().saveWithItems(
                        Transaction(
                            amount = s.amount, merchant = s.name, categoryId = s.categoryId, date = next,
                            source = TxSource.RECURRING, subscriptionId = s.id, note = "Płatność cykliczna",
                        ), null,
                    )
                    booked++
                }
                next = advance(next, s.cycle)
            }
            db.subscriptions().upsert(s.copy(nextDate = next))
        }
        if (booked > 0) checkBudgets(YearMonth.now())
        return booked
    }

    suspend fun upcomingSubscriptions(days: Int) =
        db.subscriptions().upcoming(pl.openpayments.domain.today(), pl.openpayments.domain.today() + days)

    /** Zapisuje zdjęcie paragonu w prywatnym katalogu aplikacji. */
    fun newReceiptFile(): Pair<File, Uri> {
        val dir = File(ctx.filesDir, "receipts").apply { mkdirs() }
        val f = File(dir, "receipt_${UUID.randomUUID()}.jpg")
        return f to FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    }

    suspend fun copyToReceipts(uri: Uri): File = withContext(Dispatchers.IO) {
        val (f, _) = newReceiptFile()
        ctx.contentResolver.openInputStream(uri)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
        f
    }

    // ——— Import historii z banku (CSV) ———

    data class ImportContext(val categories: List<Category>, val rules: List<CategoryRule>, val transactions: List<Transaction>)

    suspend fun importContext() = ImportContext(db.categories().all(), db.rules().all(), db.transactions().all())

    /** Nazwa i zawartość wybranego pliku (do 20 MB). */
    suspend fun readDocument(uri: Uri): Pair<String, ByteArray> = withContext(Dispatchers.IO) {
        val name = runCatching {
            ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "plik.csv"
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                require(out.size() <= 20 * 1024 * 1024) { "Plik jest za duży (ponad 20 MB)" }
            }
            out.toByteArray()
        } ?: error("Nie udało się otworzyć pliku")
        name to bytes
    }

    /** Zapis zaimportowanych transakcji jednym ruchem; reguły z poprawek kategorii trafiają do nauki. */
    suspend fun importTransactions(list: List<Transaction>, learned: List<CategoryRule>): List<Long> {
        val ids = db.withTransaction {
            if (prefs.learnRules) learned.forEach { db.rules().put(it) }
            list.chunked(400).flatMap { db.transactions().insertMany(it) }
        }
        checkBudgets(YearMonth.now())
        return ids
    }

    suspend fun deleteTransactions(ids: List<Long>) = db.withTransaction {
        ids.chunked(400).forEach { db.transactions().deleteIds(it) }
    }

    /** Wzór uniwersalnej tabeli do samodzielnego wypełnienia (Excel, Arkusze Google). */
    suspend fun writeCsvTemplate(uri: Uri) = withContext(Dispatchers.IO) {
        ctx.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter(Charsets.UTF_8).use {
            it.write(CSV_TEMPLATE)
        }
    }

    /** Eksport CSV (separator ";" — Excel w polskiej lokalizacji otwiera to poprawnie). */
    suspend fun exportCsv(): Uri = withContext(Dispatchers.IO) {
        val cats = db.categories().all().associateBy { it.id }
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val f = File(dir, "openpayments_${YearMonth.now()}.csv")
        f.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("﻿data;typ;kwota;sprzedawca;kategoria;notatka;źródło\n")
            for (t in db.transactions().all()) {
                val cells = listOf(
                    t.date.toDate().toString(),
                    if (t.isIncome) "przychód" else "wydatek",
                    "%d,%02d".format(t.amount / 100, t.amount % 100),
                    t.merchant, cats[t.categoryId]?.name ?: "", t.note, t.source.name.lowercase(),
                )
                w.write(cells.joinToString(";") { "\"" + it.replace("\"", "\"\"") + "\"" } + "\n")
            }
        }
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    }

    companion object {
        const val CSV_TEMPLATE = "﻿Data;Kwota;Opis;Kontrahent;Kategoria\r\n" +
            "2026-11-03;-45,20;Zakupy na tydzień;Biedronka;Zakupy spożywcze\r\n" +
            "2026-11-05;-120,00;Rachunek za prąd;PGE;Rachunki\r\n" +
            "2026-11-08;-18,50;Kawa z ciastkiem;Kawiarnia Pod Lipą;Restauracje\r\n" +
            "2026-11-10;5400,00;Wypłata za październik;Pracodawca;Wynagrodzenie\r\n"

        fun advance(day: Long, cycle: Cycle): Long {
            val d = day.toDate()
            return when (cycle) {
                Cycle.WEEKLY -> d.plusWeeks(1)
                Cycle.MONTHLY -> d.plusMonths(1)
                Cycle.YEARLY -> d.plusYears(1)
            }.toEpochDay()
        }
    }
}
