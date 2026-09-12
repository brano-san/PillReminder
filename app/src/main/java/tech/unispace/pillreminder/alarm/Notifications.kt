package tech.unispace.pillreminder.alarm

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.PowerManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import tech.unispace.pillreminder.MainActivity
import tech.unispace.pillreminder.R
import tech.unispace.pillreminder.data.Dose
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

    /**
     * Напоминание об одном приёме. [withActions] = false — для служебных уведомлений (проверка связи,
     * «пора нажать «Подъём»»), за которыми нет приёма: кнопки «Выпито/Пропустить/Отложить» там мёртвые.
     */
    fun show(
        context: Context,
        doseId: Long,
        title: String,
        text: String,
        useAlarmChannel: Boolean,
        attempt: Int = 0,
        fullScreen: Boolean = false,
        withActions: Boolean = true,
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

        val fullText = withAttempt(text, attempt)

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
            // Публичная видимость: на заблокированном экране контент не скрывается,
            // иначе некоторые оболочки не показывают полноэкранный интент.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            // Каждый повтор должен снова звучать, а не появляться молча.
            .setOnlyAlertOnce(false)
            .setContentIntent(open)
        if (withActions) {
            builder
                .addAction(R.drawable.ic_pill, Lang.s.took, took)
                .addAction(R.drawable.ic_pill, Lang.s.skip, skipped)
                .addAction(R.drawable.ic_pill, Lang.s.snoozeAction(snoozeMin), snoozed)
        }

        if (fullScreen) {
            val alarmIntent = alarmIntent(context, doseId, longArrayOf(doseId), title, fullText, attempt)
            builder.setFullScreenIntent(activityIntent(context, doseId, alarmIntent), true)
            launchIfUnlocked(context, alarmIntent)
        }

        notifySafely(context, doseId.toInt(), builder)
    }

    /** Текст с номером повтора; в приватном режиме текста нет — пустой первой строки быть не должно. */
    private fun withAttempt(text: String, attempt: Int): String =
        listOfNotNull(text.takeIf { it.isNotBlank() }, if (attempt > 0) Lang.s.reminderN(attempt + 1) else null)
            .joinToString("\n")

    private fun alarmIntent(context: Context, id: Long, ids: LongArray, title: String, text: String, attempt: Int): Intent =
        Intent(context, AlarmActivity::class.java)
            .setData(Uri.parse("pill://fullscreen/" + id))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_DOSE_ID, id)
            // Все приёмы группы: большая кнопка «Выпито» отмечает их всех, а не только старшего.
            .putExtra(ActionReceiver.EXTRA_DOSE_IDS, ids)
            .putExtra(AlarmActivity.EXTRA_TITLE, title)
            .putExtra(AlarmActivity.EXTRA_TEXT, text)
            .putExtra(AlarmActivity.EXTRA_ATTEMPT, attempt)

    private fun activityIntent(context: Context, id: Long, intent: Intent): PendingIntent =
        PendingIntent.getActivity(context, id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /**
     * Со заблокированным/погасшим экраном система откроет AlarmActivity во весь экран сама,
     * при разблокированном покажет только heads-up. Если пользователь дал «показ поверх других
     * приложений» — открываем экран сами; и для одного приёма, и для группы.
     */
    private fun launchIfUnlocked(context: Context, alarmIntent: Intent) {
        val pm = context.getSystemService(PowerManager::class.java)
        val km = context.getSystemService(KeyguardManager::class.java)
        val unlocked = pm?.isInteractive == true && km?.isKeyguardLocked == false
        if (unlocked && android.provider.Settings.canDrawOverlays(context)) {
            try {
                context.startActivity(alarmIntent)
            } catch (_: Exception) {
                // Оболочка запретила — остаётся обычное уведомление.
            }
        }
    }

    /**
     * Мягкое напоминание о еде: приём «после еды» дождался планового времени, а кнопку «Еда» не нажали.
     * Обычный канал, без цепочки повторов и без будильника — за забытую кнопку не наказываем звонком.
     * Тот же id, что у приёма: «Еда» или отметка снимут его вместе с остальным.
     */
    fun showMealPrompt(context: Context, doseId: Long, title: String, text: String) {
        val open = PendingIntent.getActivity(
            context,
            doseId.toInt(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, defaultChannelId(context))
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(R.drawable.ic_pill, Lang.s.mealBtn, action(context, doseId, ActionReceiver.ACTION_MEAL, "meal"))
            .addAction(R.drawable.ic_pill, Lang.s.took, action(context, doseId, ActionReceiver.ACTION_TAKEN, "taken"))
        notifySafely(context, doseId.toInt(), builder)
    }

    /** Одно уведомление на несколько приёмов, назначенных в одну минуту. */
    fun showGroup(
        context: Context,
        doses: List<Dose>,
        title: String,
        text: String,
        useAlarmChannel: Boolean,
        attempt: Int = 0,
        fullScreen: Boolean = false,
    ) {
        val leaderId = doses.first().id
        val ids = doses.map { it.id }.toLongArray()
        val fullText = withAttempt(text, attempt)

        val takeAll = PendingIntent.getBroadcast(
            context,
            leaderId.toInt(),
            Intent(context, ActionReceiver::class.java)
                .setAction(ActionReceiver.ACTION_TAKE_GROUP)
                .setData(Uri.parse("pill://group/" + leaderId))
                .putExtra(ActionReceiver.EXTRA_DOSE_IDS, ids),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // «Пропустить все»: иначе приём, который решили не пить, можно было только оставить звонить.
        val skipAll = PendingIntent.getBroadcast(
            context,
            leaderId.toInt(),
            Intent(context, ActionReceiver::class.java)
                .setAction(ActionReceiver.ACTION_SKIP_GROUP)
                .setData(Uri.parse("pill://groupskip/" + leaderId))
                .putExtra(ActionReceiver.EXTRA_DOSE_IDS, ids),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozeMin = Settings(context).snoozeMinutes
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
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    leaderId.toInt(),
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .addAction(R.drawable.ic_pill, Lang.s.takeAllAction, takeAll)
            .addAction(R.drawable.ic_pill, Lang.s.skipAllAction, skipAll)
            .addAction(R.drawable.ic_pill, Lang.s.snoozeAction(snoozeMin), action(context, leaderId, ActionReceiver.ACTION_SNOOZE, "snooze"))

        if (fullScreen) {
            val alarmIntent = alarmIntent(context, leaderId, ids, title, fullText, attempt)
            builder.setFullScreenIntent(activityIntent(context, leaderId, alarmIntent), true)
            launchIfUnlocked(context, alarmIntent)
        }
        notifySafely(context, leaderId.toInt(), builder)
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

    fun showLowStock(context: Context, medId: Long, name: String, left: Double, form: String) {
        val s = Lang.s
        // Остаток словами по форме выпуска («4 таблетки», «12 капель»), как везде в приложении.
        val builder = NotificationCompat.Builder(context, defaultChannelId(context))
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(s.lowStockTitle)
            .setContentText(s.lowStockBody(name, s.pills(left, form)))
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

    /** «Все приёмы на сегодня отмечены» — можно начинать новый день, когда проснётесь. */
    fun showDayDone(context: Context) {
        val s = Lang.s
        val builder = NotificationCompat.Builder(context, defaultChannelId(context))
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(s.dayDoneTitle)
            .setContentText(s.dayDoneBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(s.dayDoneBody))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    DAY_DONE_ID,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        notifySafely(context, DAY_DONE_ID, builder)
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
    private const val DAY_DONE_ID = 900_001
}
