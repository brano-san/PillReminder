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
    /** Опросы трекеров: отдельный канал обычной важности — без всплывающей плашки поверх напоминания о таблетке. */
    const val CHANNEL_TRACKERS = "tracker_prompts"
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

        val trackers = NotificationChannel(
            CHANNEL_TRACKERS,
            s.channelTrackersName,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = s.channelTrackersDesc
        }

        manager.createNotificationChannels(listOf(default, alarm, visits, trackers))
    }

    /**
     * Напоминание об одном приёме. [withActions] = false — для служебных уведомлений (проверка связи,
     * «пора нажать «Подъём»»), за которыми нет приёма: кнопки «Выпито/Пропустить/Отложить» там мёртвые.
     */
    fun show(
        context: Context,
        doseId: Long,
        /** Плановое время приёма: полноэкранный будильник показывает его, а не застывшие часы. */
        plannedAt: Long = 0L,
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
            // На заблокированном экране показываем обезличенную копию: системная настройка
            // «скрывать содержимое» обязана работать, а полноэкранный интент от неё не зависит.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(redactedVersion(context, useAlarmChannel, doseId))
            .setAutoCancel(true)
            // Каждый повтор должен снова звучать, а не появляться молча.
            .setOnlyAlertOnce(false)
            .setContentIntent(open)
        if (withActions) {
            builder
                .addAction(R.drawable.ic_pill, Lang.s.took, took)
                // «Пропустить» с заблокированного экрана требует разблокировки: чужой человек
                // не должен уметь снять напоминание о лекарстве.
                .addAction(protectedAction(R.drawable.ic_pill, Lang.s.skip, skipped))
                .addAction(R.drawable.ic_pill, Lang.s.snoozeAction(snoozeMin), snoozed)
        }

        if (fullScreen) {
            val alarmIntent = alarmIntent(context, doseId, longArrayOf(doseId), title, fullText, attempt, plannedAt)
            builder.setFullScreenIntent(activityIntent(context, doseId, alarmIntent), true)
            launchIfUnlocked(context, alarmIntent)
        }

        notifySafely(context, doseId.toInt(), builder)
    }

    /** Текст с номером повтора; в приватном режиме текста нет — пустой первой строки быть не должно. */
    private fun withAttempt(text: String, attempt: Int): String =
        listOfNotNull(text.takeIf { it.isNotBlank() }, if (attempt > 0) Lang.s.reminderN(attempt + 1) else null)
            .joinToString("\n")

    private fun alarmIntent(
        context: Context,
        id: Long,
        ids: LongArray,
        title: String,
        text: String,
        attempt: Int,
        plannedAt: Long = 0L,
    ): Intent =
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
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(redactedVersion(context, useAlarmChannel, leaderId))
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
            .addAction(protectedAction(R.drawable.ic_pill, Lang.s.skipAllAction, skipAll))
            // Откладываем всю группу: раньше уезжал только ведущий приём, а остальные звонили снова.
            .addAction(
                R.drawable.ic_pill,
                Lang.s.snoozeAction(snoozeMin),
                PendingIntent.getBroadcast(
                    context,
                    leaderId.toInt(),
                    Intent(context, ActionReceiver::class.java)
                        .setAction(ActionReceiver.ACTION_SNOOZE_GROUP)
                        .setData(Uri.parse("pill://groupsnooze/" + leaderId))
                        .putExtra(ActionReceiver.EXTRA_DOSE_IDS, ids),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )

        if (fullScreen) {
            val alarmIntent = alarmIntent(context, leaderId, ids, title, fullText, attempt, doses.first().plannedAt)
            builder.setFullScreenIntent(activityIntent(context, leaderId, alarmIntent), true)
            launchIfUnlocked(context, alarmIntent)
        }
        notifySafely(context, leaderId.toInt(), builder)
    }

    fun showVisit(context: Context, visitId: Long, title: String, whenText: String) {
        // Врач и адрес клиники — не менее личные данные, чем название таблетки.
        val text = if (Settings(context).privateNotifications) whenText else title + " · " + whenText
        val open = PendingIntent.getActivity(
            context,
            (VISIT_ID_BASE + visitId).toInt(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_VISITS)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(Lang.s.visitNotifTitle)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(open)
        notifySafely(context, (VISIT_ID_BASE + visitId).toInt(), builder)
    }

    /**
     * Одно уведомление на все трекеры, которым пора сейчас: id фиксированный, поэтому три ресивера,
     * сработавшие в одну минуту, перезаписывают одну карточку, а не выкладывают стопку.
     */
    fun showTrackers(context: Context, title: String, text: String) {
        // Какие именно показатели ведут — тоже данные о здоровье.
        val shown = if (Settings(context).privateNotifications) "" else text
        val builder = NotificationCompat.Builder(context, CHANNEL_TRACKERS)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(title)
            .setContentText(shown)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    TRACKERS_ID,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        notifySafely(context, TRACKERS_ID, builder)
    }

    /**
     * Курс закончился и таблетка ушла в архив. Раньше это происходило молча: человек замечал только
     * по исчезнувшей карточке, а недопитый курс антибиотика — это не мелочь.
     */
    fun showCourseDone(context: Context, medId: Long, name: String) {
        if (!Settings(context).notifyCourseDone) return
        val s = Lang.s
        val private = Settings(context).privateNotifications
        val builder = NotificationCompat.Builder(context, defaultChannelId(context))
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(s.courseDoneTitle)
            .setContentText(if (private) s.courseDoneBodyPrivate else s.courseDoneBody(name))
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (private) s.courseDoneBodyPrivate else s.courseDoneBody(name)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    (COURSE_DONE_ID_BASE + medId).toInt(),
                    // Ведём в архив: из шторки человек хочет посмотреть, что именно убралось,
                    // и решить — вернуть в расписание или удалить совсем.
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(MainActivity.EXTRA_OPEN_ARCHIVE, true),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        notifySafely(context, (COURSE_DONE_ID_BASE + medId).toInt(), builder)
    }

    fun showLowStock(context: Context, medId: Long, name: String, left: Double, form: String) {
        if (!Settings(context).notifyLowStock) return
        val s = Lang.s
        // Режим конфиденциальности прячет название и здесь: «Ксарелто: осталось 4» в шторке —
        // такая же утечка, как название в напоминании о приёме.
        val private = Settings(context).privateNotifications
        // Остаток словами по форме выпуска («4 таблетки», «12 капель»), как везде в приложении.
        val builder = NotificationCompat.Builder(context, defaultChannelId(context))
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(s.lowStockTitle)
            .setContentText(if (private) s.lowStockPrivate else s.lowStockBody(name, s.pills(left, form)))
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

    /**
     * Обезличенная копия для экрана блокировки: видно, что пора принять лекарство, но не какое.
     * Показывается, когда владелец включил системное «скрывать содержимое уведомлений».
     */
    private fun redactedVersion(context: Context, useAlarmChannel: Boolean, doseId: Long) =
        NotificationCompat.Builder(
            context,
            if (useAlarmChannel) alarmChannelId(context) else defaultChannelId(context),
        )
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(Lang.s.timeToTakeFallback)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Копия для экрана блокировки обязана работать: без перехода и кнопки «Выпито»
            // напоминание превращалось в строку, по которой нельзя ничего сделать.
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    doseId.toInt(),
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .addAction(R.drawable.ic_pill, Lang.s.took, action(context, doseId, ActionReceiver.ACTION_TAKEN, "taken"))
            .build()

    /**
     * Действие, которое меняет историю приёмов, — только после разблокировки (Android 12+).
     * «Выпито» и «Отложить» остаются быстрыми: их жмут именно с заблокированного экрана.
     */
    private fun protectedAction(icon: Int, title: String, intent: PendingIntent): NotificationCompat.Action =
        NotificationCompat.Action.Builder(icon, title, intent)
            .setAuthenticationRequired(true)
            .build()

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

    /**
     * «Вернуть» после «Пропустить все»: в шторке отмена одним касанием невозможна, поэтому сразу
     * после массового пропуска показываем короткое уведомление с возвратом.
     */
    fun showUndoSkip(context: Context, ids: LongArray) {
        val builder = NotificationCompat.Builder(context, defaultChannelId(context))
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(Lang.s.skippedAllTitle(ids.size))
            .setContentText(Lang.s.skippedAllBody)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTimeoutAfter(UNDO_TIMEOUT_MS)
            .addAction(
                R.drawable.ic_pill,
                Lang.s.undo,
                PendingIntent.getBroadcast(
                    context,
                    UNDO_SKIP_ID,
                    Intent(context, ActionReceiver::class.java)
                        .setAction(ActionReceiver.ACTION_UNDO_SKIP)
                        .setData(Uri.parse("pill://undoskip/" + ids.joinToString("-")))
                        .putExtra(ActionReceiver.EXTRA_DOSE_IDS, ids),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        notifySafely(context, UNDO_SKIP_ID, builder)
    }

    fun dismissUndoSkip(context: Context) {
        NotificationManagerCompat.from(context).cancel(UNDO_SKIP_ID)
    }

    /**
     * Утреннее «Проснулись?»: с кнопкой «Подъём» день начинается прямо из шторки, без открытия
     * приложения и поиска кнопки на экране.
     */
    fun showWakeReminder(context: Context, id: Long, title: String, text: String, useAlarmChannel: Boolean) {
        val builder = NotificationCompat.Builder(
            context,
            if (useAlarmChannel) alarmChannelId(context) else defaultChannelId(context),
        )
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    id.toInt(),
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .addAction(
                R.drawable.ic_pill,
                Lang.s.wakeAction,
                PendingIntent.getBroadcast(
                    context,
                    id.toInt(),
                    Intent(context, ActionReceiver::class.java)
                        .setAction(ActionReceiver.ACTION_WAKE)
                        .setData(Uri.parse("pill://wake")),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        notifySafely(context, id.toInt(), builder)
    }

    fun dismissWakeReminder(context: Context) {
        NotificationManagerCompat.from(context).cancel(WakeReminderReceiver.WAKE_NOTIF_ID.toInt())
    }

    fun dismiss(context: Context, doseId: Long) {
        NotificationManagerCompat.from(context).cancel(doseId.toInt())
    }

    private const val VISIT_ID_BASE = 500_000L
    private const val DAY_DONE_ID = 900_001
    private const val TRACKERS_ID = 820_000

    /** Уведомления «курс закончился»: свой диапазон, чтобы не перезаписать «таблетки заканчиваются». */
    private const val COURSE_DONE_ID_BASE = 710_000L

    /** Уведомление «Пропущено N приёмов · Вернуть» и его время жизни. */
    private const val UNDO_SKIP_ID = 900_002
    private const val UNDO_TIMEOUT_MS = 5 * 60_000L
}
