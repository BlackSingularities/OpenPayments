package pl.openpayments.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pl.openpayments.MainActivity
import pl.openpayments.R
import pl.openpayments.data.Budget
import pl.openpayments.data.Subscription
import pl.openpayments.domain.formatMoney
import pl.openpayments.domain.formatShortDay

object Notifier {
    private const val CH_BUDGET = "budget"
    private const val CH_SUBS = "subscriptions"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_BUDGET, "Budżety", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Ostrzeżenia przed przekroczeniem limitu" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SUBS, "Subskrypcje", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Przypomnienia o nadchodzących płatnościach" }
        )
    }

    fun budget(ctx: Context, b: Budget, spent: Long, pct: Int) {
        val title = if (pct >= 100) "Przekroczono budżet „${b.name}”" else "Budżet „${b.name}”: wykorzystano $pct%"
        val left = b.limit - spent
        val text = if (left >= 0) "Zostało ${formatMoney(left)} z ${formatMoney(b.limit)}"
        else "Ponad limit o ${formatMoney(-left)}"
        post(ctx, CH_BUDGET, b.id.toInt() + 1000, title, text)
    }

    fun upcoming(ctx: Context, s: Subscription) {
        post(ctx, CH_SUBS, s.id.toInt() + 5000, "Wkrótce płatność: ${s.name}",
            "${formatMoney(s.amount)} · ${formatShortDay(s.nextDate)}")
    }

    private fun post(ctx: Context, channel: String, id: Int, title: String, text: String) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33) return
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }
}
