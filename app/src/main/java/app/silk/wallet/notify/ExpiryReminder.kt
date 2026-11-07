package app.silk.wallet.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.silk.wallet.MainActivity
import app.silk.wallet.R
import app.silk.wallet.SilkApp
import app.silk.wallet.domain.CardMath
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 到期提醒：每天在本机检查一次，有即将到期或已过期（未归档）的卡就发一条通知。
 * 通知里只写数量，不写卡号或银行，锁屏上也不泄露信息。
 */
class ExpiryReminder(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SilkApp
        val settings = app.prefs.settings.first()
        if (!settings.expiryNotify) return Result.success()
        val cards = runCatching { app.cards.all() }.getOrElse { return Result.retry() }.filterNot { it.archived }
        val states = cards.map { CardMath.expiry(it.expMonth, it.expYear, settings.noticeDays) }
        val soon = states.count { it == CardMath.Expiry.SOON }
        val expired = states.count { it == CardMath.Expiry.EXPIRED }
        if (soon + expired == 0) return Result.success()
        val text = buildList {
            if (soon > 0) add("$soon 张卡将在 ${settings.noticeDays} 天内到期")
            if (expired > 0) add("$expired 张卡已过期")
        }.joinToString("，")
        notify(applicationContext, text)
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "expiry"
        private const val WORK = "silk_expiry_daily"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(WORK)
                return
            }
            wm.enqueueUniquePeriodicWork(
                WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ExpiryReminder>(1, TimeUnit.DAYS).build(),
            )
        }

        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        private fun notify(context: Context, text: String) {
            if (!canNotify(context)) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "到期提醒", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("卡片到期提醒")
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(context).notify(1001, n)
        }
    }
}
