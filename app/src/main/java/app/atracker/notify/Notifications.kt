package app.atracker.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
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
import app.atracker.ATrackerApp
import app.atracker.MainActivity
import app.atracker.R
import app.atracker.data.db.AppSettingEntity
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Optional notifications, all off until switched on in Settings.
 *  - Releases: an episode/chapter/premiere of a title in your library is due within 24 hours. Only dates a source
 *    actually reported are used (AniList airing schedule); nothing is guessed.
 *  - Reminder: titles you are actively following have had no progress for 3 days.
 */
object Notifications {
    const val KEY_RELEASES = "notify.releases"
    const val KEY_REMINDER = "notify.reminder"
    private const val KEY_SENT = "notify.sent"
    private const val CHANNEL = "atracker-updates"
    private const val WORK = "atracker-notifications"
    private const val DAY = 24 * 3_600_000L

    fun needsPermission(context: Context) =
        Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun schedule(context: Context, anyEnabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!anyEnabled) { wm.cancelUniqueWork(WORK); return }
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<Worker>(6, TimeUnit.HOURS).build())
    }

    private fun post(context: Context, id: Int, title: String, text: String) {
        if (needsPermission(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Updates and reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = android.app.PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.mipmap.ic_launcher).setContentTitle(title).setContentText(text)
            .setContentIntent(open).setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val app = applicationContext as ATrackerApp
            val settings = app.database.settingDao()
            val now = System.currentTimeMillis()
            val sent = (settings.get(KEY_SENT) ?: "").split('\n').filter { it.isNotBlank() }.toMutableSet()

            if (settings.get(KEY_RELEASES) == "on") {
                val due = app.database.activityDao().observeLibraryReleases(now, now + DAY).first()
                for (r in due) {
                    val key = "r${r.id}"
                    if (key in sent) continue
                    val what = when (r.kind) { "EPISODE" -> "Episode ${r.number ?: ""}"; "CHAPTER" -> "Chapter ${r.number ?: ""}"; "VOLUME" -> "Volume ${r.number ?: ""}"; else -> "Premiere" }.trim()
                    post(applicationContext, key.hashCode(), r.title, "$what is due within 24 hours.")
                    sent += key
                }
            }
            if (settings.get(KEY_REMINDER) == "on") {
                val day = now / DAY
                val key = "reminder$day"
                if (key !in sent) {
                    val active = app.database.libraryDao().allItems().filter { it.entry.status == "CURRENT" }
                    val recent = app.database.activityDao().observeHistory(now - 3 * DAY, now + 1).first()
                    if (active.isNotEmpty() && recent.none { it.event.type == "PROGRESS" && it.event.voidedAt == null }) {
                        post(applicationContext, 7001, "Keep your place", "You are following ${active.size} titles and haven't updated any for 3 days.")
                        sent += key
                    }
                }
            }
            settings.put(AppSettingEntity(KEY_SENT, sent.toList().takeLast(200).joinToString("\n")))
            return Result.success()
        }
    }
}
