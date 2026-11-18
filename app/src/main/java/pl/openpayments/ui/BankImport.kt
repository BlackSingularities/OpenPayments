package pl.openpayments.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.openpayments.data.CategoryRule
import pl.openpayments.data.Repository
import pl.openpayments.data.Transaction
import pl.openpayments.data.TxSource
import pl.openpayments.domain.Categorizer
import pl.openpayments.domain.bank.*

/**
 * Stan importu wyciągu z banku: plik → analiza kolumn → podgląd z możliwością poprawek → zapis.
 * Role kolumn poprawione ręcznie zapamiętujemy dla danego układu pliku.
 */
class BankImportController(
    private val repo: Repository,
    private val scope: CoroutineScope,
    private val showSnack: (Snack) -> Unit,
) {
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var fileName by mutableStateOf<String?>(null); private set
    var analysis by mutableStateOf<Analysis?>(null); private set
    var autoRoles by mutableStateOf<List<Role>>(emptyList()); private set
    var remembered by mutableStateOf(false); private set
    var result by mutableStateOf<ReadResult?>(null); private set
    var candidates by mutableStateOf<List<ImportCandidate>>(emptyList()); private set
    var invert by mutableStateOf(false); private set
    var skip by mutableStateOf(SkipReason.entries.toSet()); private set
    private var included by mutableStateOf<Set<Int>>(emptySet())
    private var excluded by mutableStateOf<Set<Int>>(emptySet())
    private var categoryOverrides by mutableStateOf<Map<Int, Long?>>(emptyMap())
    private var nameOverrides by mutableStateOf<Map<Int, String>>(emptyMap())
    private var context: Repository.ImportContext? = null
    private var job: Job? = null

    /** Plik otwarty z innej aplikacji („Otwórz za pomocą…”) — ekran importu wczytuje go po wejściu. */
    var pendingUri by mutableStateOf<Uri?>(null)

    val hasPreview get() = analysis != null

    fun isSelected(c: ImportCandidate): Boolean = when (c.id) {
        in excluded -> false
        in included -> true
        else -> c.reason == null || c.reason !in skip
    }

    fun toggle(c: ImportCandidate) = setSelected(c, !isSelected(c))

    fun setSelected(c: ImportCandidate, on: Boolean) {
        if (on) { excluded = excluded - c.id; included = included + c.id } else { included = included - c.id; excluded = excluded + c.id }
    }

    fun selectAll(on: Boolean) {
        val ids = candidates.map { it.id }.toSet()
        if (on) { included = ids; excluded = emptySet() } else { excluded = ids; included = emptySet() }
    }

    fun categoryOf(c: ImportCandidate): Long? = if (c.id in categoryOverrides) categoryOverrides[c.id] else c.categoryId
    fun nameOf(c: ImportCandidate): String = nameOverrides[c.id] ?: c.merchant
    fun setCategory(c: ImportCandidate, id: Long?) { categoryOverrides = categoryOverrides + (c.id to id) }
    fun setName(c: ImportCandidate, name: String) {
        nameOverrides = if (name.isBlank() || name == c.merchant) nameOverrides - c.id else nameOverrides + (c.id to name)
    }

    val selected: List<ImportCandidate> get() = candidates.filter(::isSelected)

    fun load(uri: Uri) {
        job?.cancel()
        clearState()
        busy = true
        job = scope.launch {
            runCatching {
                val (name, bytes) = repo.readDocument(uri)
                fileName = name
                val ctx = repo.importContext()
                context = ctx
                withContext(Dispatchers.Default) {
                    val table = BankStatement.table(bytes)
                    require(table.rows.isNotEmpty()) { "Nie znaleziono żadnych wierszy z danymi" }
                    val auto = BankStatement.analyze(table)
                    val saved = repo.prefs.csvMapping(auto.signature)
                        ?.mapNotNull { n -> Role.entries.firstOrNull { it.name == n } }
                        ?.takeIf { it.size == table.columns }
                    auto to saved
                }
            }.onSuccess { (auto, saved) ->
                autoRoles = auto.roles
                remembered = saved != null && saved != auto.roles
                analysis = if (saved != null) auto.copy(roles = saved) else auto
                recompute()
            }.onFailure {
                error = when (it) {
                    is IllegalArgumentException, is IllegalStateException -> it.message
                    else -> "Nie udało się odczytać pliku: ${it.message}"
                }
                busy = false
            }
        }
    }

    /** Zmiana roli kolumny; role występujące raz (data, kwota…) zabieramy z poprzedniej kolumny. */
    fun setRole(column: Int, role: Role) {
        val a = analysis ?: return
        val roles = a.roles.toMutableList()
        if (role != Role.IGNORE && role != Role.DESCRIPTION) {
            roles.indices.filter { roles[it] == role && it != column }.forEach { roles[it] = Role.IGNORE }
        }
        if (role == Role.AMOUNT) roles.indices.filter { roles[it] == Role.DEBIT || roles[it] == Role.CREDIT }.forEach { roles[it] = Role.IGNORE }
        if (role == Role.DEBIT || role == Role.CREDIT) roles.indices.filter { roles[it] == Role.AMOUNT }.forEach { roles[it] = Role.IGNORE }
        roles[column] = role
        analysis = a.copy(roles = roles)
        recompute()
    }

    fun resetRoles() {
        val a = analysis ?: return
        analysis = a.copy(roles = autoRoles)
        remembered = false
        repo.prefs.setCsvMapping(a.signature, null)
        recompute()
    }

    fun changeInvert(v: Boolean) { invert = v; recompute() }
    fun changeSkip(reason: SkipReason, on: Boolean) {
        skip = if (on) skip + reason else skip - reason
        // Ręczne zaznaczenia tej grupy przestają obowiązywać — przełącznik decyduje o wszystkich.
        val ids = candidates.filter { it.reason == reason }.map { it.id }.toSet()
        included = included - ids; excluded = excluded - ids
    }

    private fun recompute() {
        val a = analysis ?: return
        val ctx = context ?: return
        busy = true
        job = scope.launch {
            val (r, plan) = withContext(Dispatchers.Default) {
                val r = BankStatement.read(a, ReadOptions(invert = invert))
                r to ImportPlanner.plan(r.rows, ctx.categories, ctx.rules, ctx.transactions)
            }
            result = r
            candidates = plan
            busy = false
        }
    }

    fun commit(done: () -> Unit) {
        val a = analysis ?: return
        val chosen = selected
        if (chosen.isEmpty() || busy) return
        busy = true
        scope.launch {
            val txs = chosen.map { c ->
                Transaction(
                    amount = c.amount, isIncome = c.isIncome, merchant = nameOf(c).trim().ifEmpty { c.merchant },
                    note = c.note, categoryId = categoryOf(c), date = c.date, source = TxSource.IMPORT,
                )
            }
            // Ręcznie poprawione kategorie stają się regułami (jak przy zwykłym zapisie transakcji).
            val learned = chosen.filter { it.id in categoryOverrides && categoryOf(it) != null && categoryOf(it) != it.categoryId }
                .mapNotNull { c -> Categorizer.ruleKey(nameOf(c))?.let { CategoryRule(keyword = it, categoryId = categoryOf(c)!!) } }
                .distinctBy { it.keyword }
            runCatching { repo.importTransactions(txs, learned) }
                .onSuccess { ids ->
                    if (a.roles != autoRoles) repo.prefs.setCsvMapping(a.signature, a.roles.map { it.name })
                    clearState()
                    done()
                    showSnack(Snack("Zaimportowano ${plural(ids.size, "transakcję", "transakcje", "transakcji")}", "Cofnij") {
                        scope.launch { repo.deleteTransactions(ids) }
                    })
                }
                .onFailure { error = "Nie udało się zapisać: ${it.message}"; busy = false }
        }
    }

    fun clear() { job?.cancel(); clearState() }

    private fun clearState() {
        busy = false; error = null; fileName = null; analysis = null; autoRoles = emptyList(); remembered = false
        result = null; candidates = emptyList(); invert = false; skip = SkipReason.entries.toSet()
        included = emptySet(); excluded = emptySet(); categoryOverrides = emptyMap(); nameOverrides = emptyMap(); context = null
    }
}

/** „1 transakcję”, „3 transakcje”, „5 transakcji”. */
fun plural(n: Int, one: String, few: String, many: String): String {
    val word = when {
        n == 1 -> one
        n % 10 in 2..4 && n % 100 !in 12..14 -> few
        else -> many
    }
    return "$n $word"
}
