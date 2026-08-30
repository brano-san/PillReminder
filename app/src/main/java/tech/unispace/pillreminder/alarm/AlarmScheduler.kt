package tech.unispace.pillreminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import tech.unispace.pillreminder.MainActivity
import tech.unispace.pillreminder.data.Dose
import tech.unispace.pillreminder.ui.Lang

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun reminderIntent(doseId: Long, attempt: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            doseId.toInt(),
            Intent(context, ReminderReceiver::class.java)
                // data делает PendingIntent уникальным для дозы; extras на тождество не влияют
                .setData(Uri.parse("pill://dose/" + doseId))
                .putExtra(Notifications.EXTRA_DOSE_ID, doseId)
                .putExtra(ReminderReceiver.EXTRA_ATTEMPT, attempt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * Ставит будильник на приём.
     *
     * Используется [AlarmManager.setAlarmClock] — это единственный вид будильника,
     * который система считает пользовательским: он не откладывается в Doze и не попадает
     * под ограничение «не чаще раза в 9 минут», из-за которого повторы раз в 3 минуты
     * иначе разъезжались бы. Побочный эффект — иконка будильника в статусбаре.
     */
    fun schedule(dose: Dose, triggerAt: Long, attempt: Int = 0) {
        val operation = reminderIntent(dose.id, attempt)
        val showIntent = PendingIntent.getActivity(
            context,
            dose.id.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (canScheduleExact()) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAt, showIntent),
                    operation,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    fun cancelAll(doseIds: List<Long>) {
        for (id in doseIds) {
            alarmManager.cancel(reminderIntent(id, 0))
        }
    }

    /** Проверочный сигнал из настроек — чтобы увидеть, доходит ли вообще. */
    fun scheduleTest(delayMillis: Long, fullScreen: Boolean = false) {
        val operation = PendingIntent.getBroadcast(
            context,
            TEST_ID.toInt(),
            Intent(context, ReminderReceiver::class.java)
                .setData(Uri.parse("pill://test"))
                .putExtra(Notifications.EXTRA_DOSE_ID, TEST_ID)
                .putExtra(ReminderReceiver.EXTRA_TEST, true)
                .putExtra(ReminderReceiver.EXTRA_TEST_FULL_SCREEN, fullScreen),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = System.currentTimeMillis() + delayMillis
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    companion object {
        const val TEST_ID = -777L
    }
}

/** «1 таблетка» / "1 pill" — язык берётся из настроек. */
fun formatAmount(amount: Double): String = Lang.s.pills(amount)
