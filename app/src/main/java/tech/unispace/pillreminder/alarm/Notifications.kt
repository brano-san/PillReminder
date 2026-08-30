package tech.unispace.pillreminder.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import tech.unispace.pillreminder.MainActivity
import tech.unispace.pillreminder.R
import tech.unispace.pillreminder.data.Settings
import tech.unispace.pillreminder.ui.Lang

object Notifications {
    private const val BASE_DEFAULT = "pill_reminders"
    private const val BASE_ALARM = "pill_reminders_alarm"
    const val CHANNEL_VISITS = "doctor_visits"
    const val EXTRA_DOSE_ID = "doseId"

    private fun suffix(context: Context): String {
        val v = Settings(context).channelVersion
        return if (v == 0) "" else "_v$v"
    }

    fun defaultChannelId(context: Context) = BASE_DEFAULT + suffix(context)
    fun alarmChannelId(context: Context) = BASE_ALARM + suffix(context)

    /**
     * Настройки канала неизменяемы после создания, поэтому смена мелодии — это новый канал
     * с новым id (версия в суффиксе) и удаление старых.
     */
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val settings = Settings(context)
        val s = Lang.s
        val defaultId = defaultChannelId(context)
        val alarmId = alarmChannelId(context)

        // Убираем каналы прошлых версий, чтобы не плодить мусор в системных настройках.
        manager.notificationChannels
            .filter { it.id.startsWith(BASE_DEFAULT) && it.id != defaultId && it.id != alarmId }
            .forEach { manager.deleteNotificationChannel(it.id) }

        val customSound = settings.soundUri?.let { Uri.parse(it) }

        val default = NotificationChannel(
            defaultId,
            s.channelDefaultName,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = s.channelDefaultDesc
            enableVibration(true)
            if (customSound != null) {
                setSound(
                    customSound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
        }

        val alarm = NotificationChannel(
            alarmId,
            s.channelAlarmName,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = s.channelAlarmDesc
            enableVibration(true)
            setBypassDnd(true)
            setSound(
                customSound ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }

        val visits = NotificationChannel(
            CHANNEL_VISITS,
            s.channelVisitsName,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            enableVibration(true)
        }

        manager.createNotificationChannels(listOf(default, alarm, visits))
    }

    fun show(
        context: Context,
        doseId: Long,
        title: String,
        text: String,
        useAlarmChannel: Boolean,
        attempt: Int = 0,
        fullScreen: Boolean = false,
    ) {
        val open = PendingIntent.getActivity(
            context,
            doseId.toInt(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val took = action(context, doseId, ActionReceiver.ACTION_TAKEN, "taken")
        val skipped = action(context, doseId, ActionReceiver.ACTION_SKIPPED, "skipped")
        val snoozeMin = Settings(context).snoozeMinutes
        val snoozed = action(context, doseId, ActionReceiver.ACTION_SNOOZE, "snooze")

        val fullText = if (attempt > 0) text + "\n" + Lang.s.reminderN(attempt + 1) else text

        val builder = NotificationCompat.Builder(
            context,
            if (useAlarmChannel) alarmChannelId(context) else defaultChannelId(context),
        )
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(title)
            .setContentText(fullText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            // Каждый повтор должен снова звучать, а не появляться молча.
            .setOnlyAlertOnce(false)
            .setContentIntent(open)
            .addAction(R.drawable.ic_pill, Lang.s.took, took)
            .addAction(R.drawable.ic_pill, Lang.s.skip, skipped)
            .addAction(R.drawable.ic_pill, Lang.s.snoozeAction(snoozeMin), snoozed)

        if (fullScreen) {
            // Со заблокированным/погасшим экраном система откроет AlarmActivity во весь экран,
            // при разблокированном покажет обычное heads-up уведомление.
            val fsi = PendingIntent.getActivity(
                context,
                doseId.toInt(),
                Intent(context, AlarmActivity::class.java)
                    .setData(Uri.parse("pill://fullscreen/" + doseId))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_DOSE_ID, doseId)
                    .putExtra(AlarmActivity.EXTRA_TITLE, title)
                    .putExtra(AlarmActivity.EXTRA_TEXT, fullText),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setFullScreenIntent(fsi, true)
        }

        notifySafely(context, doseId.toInt(), builder)
    }

    fun showVisit(context: Context, visitId: Long, title: String, whenText: String) {
        val open = PendingIntent.getActivity(
            context,
            (VISIT_ID_BASE + visitId).toInt(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_VISITS)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(Lang.s.visitNotifTitle)
            .setContentText(title + " · " + whenText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title + " · " + whenText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(open)
        notifySafely(context, (VISIT_ID_BASE + visitId).toInt(), builder)
    }

    fun showTracker(context: Context, trackerId: Long, title: String, text: String) {
        val builder = NotificationCompat.Builder(context, defaultChannelId(context))
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    (820_000 + trackerId).toInt(),
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        notifySafely(context, (820_000 + trackerId).toInt(), builder)
    }

    fun showLowStock(context: Context, medId: Long, name: String, left: Double) {
        val s = Lang.s
        val leftText = if (left % 1.0 == 0.0) left.toInt().toString() else left.toString()
        val builder = NotificationCompat.Builder(context, defaultChannelId(context))
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(s.lowStockTitle)
            .setContentText(s.lowStockBody(name, leftText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    (700_000 + medId).toInt(),
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        notifySafely(context, (700_000 + medId).toInt(), builder)
    }

    private fun notifySafely(context: Context, id: Int, builder: NotificationCompat.Builder) {
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            // Разрешение на уведомления не выдано — об этом предупреждает экран «Настройки».
        }
    }

    private fun action(context: Context, doseId: Long, action: String, path: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            doseId.toInt(),
            Intent(context, ActionReceiver::class.java)
                .setAction(action)
                .setData(Uri.parse("pill://" + path + "/" + doseId))
                .putExtra(EXTRA_DOSE_ID, doseId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun dismiss(context: Context, doseId: Long) {
        NotificationManagerCompat.from(context).cancel(doseId.toInt())
    }

    private const val VISIT_ID_BASE = 500_000L
}
