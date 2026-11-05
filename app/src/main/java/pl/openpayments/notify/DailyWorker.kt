package pl.openpayments.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import pl.openpayments.OpenPaymentsApp
import java.util.concurrent.TimeUnit

/** Raz dziennie: księguje płatności cykliczne i przypomina o tych za 2 dni. */
class DailyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as OpenPaymentsApp).repository
        repo.processDueSubscriptions()
        if (repo.prefs.subscriptionReminders) {
            repo.upcomingSubscriptions(2).filter { it.nextDate > pl.openpayments.domain.today() }
                .forEach { Notifier.upcoming(applicationContext, it) }
        }
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "daily", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
