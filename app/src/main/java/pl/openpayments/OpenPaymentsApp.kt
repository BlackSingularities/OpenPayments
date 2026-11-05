package pl.openpayments

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pl.openpayments.data.AppDatabase
import pl.openpayments.data.Prefs
import pl.openpayments.data.Repository
import pl.openpayments.notify.DailyWorker
import pl.openpayments.notify.Notifier

class OpenPaymentsApp : Application() {
    lateinit var repository: Repository
        private set
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this, AppDatabase.build(this), Prefs(this))
        Notifier.createChannels(this)
        DailyWorker.schedule(this)
        appScope.launch {
            repository.seedIfEmpty()
            repository.processDueSubscriptions()
            repository.cleanupReceipts()
        }
    }
}
