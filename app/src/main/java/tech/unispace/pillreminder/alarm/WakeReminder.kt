package tech.unispace.pillreminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.unispace.pillreminder.container
import tech.unispace.pillreminder.data.AppDatabase
import tech.unispace.pillreminder.data.Settings
import tech.unispace.pillreminder.data.today
import tech.unispace.pillreminder.ui.Lang
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Напоминание нажать «я проснулся»: если к назначенному времени день не начат —
 * приходит уведомление. Честно отследить «телефон неактивен N часов» обычному
 * приложению нельзя (нужен спецдоступ Usage Access), поэтому — время.
 */
object WakeReminder {

    private fun intent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            801_001,
            Intent(context, WakeReminderReceiver::class.java)
                .setData(Uri.parse("pill://wakecheck")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Поставить будильник на ближайшее назначенное время (сегодня или завтра). */
    suspend fun schedule(context: Context) {
        val settings = Settings(context)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pi = intent(context)
        alarmManager.cancel(pi)
        if (!settings.wakeReminderEnabled) return

        val db = AppDatabase.get(context)
        val wakePressedToday = db.wakeDao().getDay(today()) != null
        val time = LocalTime.of(settings.wakeReminderMinutes / 60, settings.wakeReminderMinutes % 60)
        val zone = ZoneId.systemDefault()
        val todayAt = LocalDate.now().atTime(time).atZone(zone).toInstant().toEpochMilli()
        val at = if (!wakePressedToday && todayAt > System.currentTimeMillis()) {
            todayAt
        } else {
            LocalDate.now().plusDays(1).atTime(time).atZone(zone).toInstant().toEpochMilli()
        }
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}

class WakeReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = app.container.db
                if (db.wakeDao().getDay(today()) == null) {
                    Notifications.show(
                        context = app,
                        doseId = WAKE_NOTIF_ID,
                        title = Lang.s.wakeRemindNotifTitle,
                        text = Lang.s.wakeRemindNotifBody,
                        useAlarmChannel = Settings(app).alarmSound,
                    )
                }
                WakeReminder.schedule(app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val WAKE_NOTIF_ID = -801L
    }
}
