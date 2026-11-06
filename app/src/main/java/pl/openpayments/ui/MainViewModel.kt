package pl.openpayments.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.openpayments.BuildConfig
import pl.openpayments.data.*
import pl.openpayments.update.Release
import pl.openpayments.update.Updates
import java.time.YearMonth

/** Szkic transakcji z zeskanowanego paragonu — trafia do ekranu edycji do sprawdzenia. */
data class ReceiptDraft(
    val merchant: String,
    val date: Long?,
    val total: Long?,
    val items: List<ReceiptItem>,
    val imagePath: String,
    val consistent: Boolean,
    val rawLines: List<String>,
)

/** Komunikat na dole ekranu, opcjonalnie z akcją (np. „Cofnij”). */
data class Snack(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val app: Application, val repo: Repository) : ViewModel() {
    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    val categories = repo.categories.state(emptyList())
    val transactions = repo.transactions.state(emptyList())
    val budgets = repo.budgets.state(emptyList())
    val subscriptions = repo.subscriptions.state(emptyList())
    val ruleCount = repo.ruleCount.state(0)
    val theme: StateFlow<String> = repo.prefs.theme
    val dynamicColor: StateFlow<Boolean> = repo.prefs.dynamicColor

    private val _month = MutableStateFlow(YearMonth.now())
    val month = _month.asStateFlow()
    fun shiftMonth(delta: Long) { _month.value = _month.value.plusMonths(delta) }

    val products = _month.flatMapLatest { repo.products(it, 8) }.state(emptyList())
    val productGroups = _month.flatMapLatest { repo.productGroups(it) }.state(emptyList())
    val aliasCount = repo.aliasCount.state(0)
    fun clearAliases() = viewModelScope.launch { repo.clearAliases(); snack("Wyczyszczono wyuczone nazwy produktów") }

    var draft: ReceiptDraft? = null

    private val _snack = MutableStateFlow<Snack?>(null)
    val snack = _snack.asStateFlow()
    fun snack(msg: String) { _snack.value = Snack(msg) }
    fun snackShown() { _snack.value = null }

    var onboarded by mutableStateOf(repo.prefs.onboarded)
        private set
    fun finishOnboarding(monthlyBudget: Long?) {
        if (monthlyBudget != null && monthlyBudget > 0) saveBudget(Budget(name = "Miesięczny", limit = monthlyBudget, categoryId = null))
        repo.prefs.onboarded = true; onboarded = true
    }

    fun saveTransaction(t: Transaction, items: List<ReceiptItem>?, done: () -> Unit = {}) = viewModelScope.launch {
        repo.saveTransaction(t, items); done()
    }
    fun deleteTransaction(t: Transaction) = viewModelScope.launch {
        val items = repo.deleteTransaction(t)
        _snack.value = Snack("Usunięto: ${t.merchant}", "Cofnij") {
            viewModelScope.launch { repo.restoreTransaction(t, items) }
        }
    }
    fun saveBudget(b: Budget) = viewModelScope.launch { repo.saveBudget(b) }
    fun deleteBudget(b: Budget) = viewModelScope.launch { repo.deleteBudget(b) }
    fun saveSubscription(s: Subscription) = viewModelScope.launch { repo.saveSubscription(s) }
    fun deleteSubscription(s: Subscription) = viewModelScope.launch { repo.deleteSubscription(s) }
    fun saveCategory(c: Category) = viewModelScope.launch { repo.saveCategory(c) }
    fun deleteCategory(c: Category) = viewModelScope.launch { repo.deleteCategory(c) }
    fun clearRules() = viewModelScope.launch { repo.clearRules(); snack("Wyczyszczono wyuczone reguły") }
    fun exportCsv(onReady: (Uri) -> Unit) = viewModelScope.launch { onReady(repo.exportCsv()) }

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        runCatching { repo.exportBackup(uri) }
            .onSuccess { snack("Zapisano kopię ($it transakcji)") }
            .onFailure { snack("Nie udało się zapisać kopii: ${it.message}") }
    }
    fun importBackup(uri: Uri) = viewModelScope.launch {
        runCatching { repo.importBackup(uri) }
            .onSuccess { snack("Przywrócono $it transakcji") }
            .onFailure { snack("Nie udało się przywrócić: ${it.message}") }
    }

    // ——— Aktualizacje (redzikowski.com) ———
    private val updates = Updates(app)
    var release by mutableStateOf<Release?>(null); private set
    var updateBusy by mutableStateOf(false); private set
    var updateProgress by mutableFloatStateOf(0f); private set
    var updateStatus by mutableStateOf<String?>(null); private set
    var showUpdateDialog by mutableStateOf(false)
    var autoUpdate by mutableStateOf(repo.prefs.autoUpdate); private set
    val certificate: String? by lazy { Updates.installedCertificate(app) }

    fun changeAutoUpdate(v: Boolean) { repo.prefs.autoUpdate = v; autoUpdate = v }

    fun checkUpdates(silent: Boolean = false) {
        if (!BuildConfig.UPDATES_ENABLED || updateBusy) return
        updateBusy = true; updateStatus = null
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { updates.check() } }
                .onSuccess { r ->
                    release = r
                    if (r != null) showUpdateDialog = true
                    else if (!silent) updateStatus = "Masz najnowszą wersję"
                }
                .onFailure { if (!silent) updateStatus = "Nie udało się sprawdzić: ${it.message}" }
            updateBusy = false
        }
    }

    fun installUpdate() {
        val r = release ?: return
        if (updateBusy) return
        updateBusy = true; updateProgress = 0f; updateStatus = "Pobieranie…"
        viewModelScope.launch {
            runCatching {
                val apk = withContext(Dispatchers.IO) { updates.download(r) { p -> updateProgress = p } }
                updateStatus = "Instalowanie…"
                withContext(Dispatchers.IO) { updates.install(apk) }
            }.onFailure { updateStatus = it.message ?: "Aktualizacja nieudana" }
            updateBusy = false
        }
    }

    init {
        if (BuildConfig.UPDATES_ENABLED && repo.prefs.autoUpdate) checkUpdates(silent = true)
    }

    class Factory(private val app: Application, private val repo: Repository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(app, repo) as T
    }
}
