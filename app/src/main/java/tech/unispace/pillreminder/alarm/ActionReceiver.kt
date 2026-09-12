package tech.unispace.pillreminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.unispace.pillreminder.container
import tech.unispace.pillreminder.data.Settings

/** Кнопки «Выпил» и «Пропустить» прямо в шторке уведомлений. */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in setOf(ACTION_TAKEN, ACTION_SKIPPED, ACTION_SNOOZE, ACTION_MEAL, ACTION_TAKE_GROUP, ACTION_SKIP_GROUP)) return

        // «Принять все» / «Пропустить все» приходят со списком приёмов одной группы.
        if (action == ACTION_TAKE_GROUP || action == ACTION_SKIP_GROUP) {
            val ids = intent.getLongArrayExtra(EXTRA_DOSE_IDS) ?: return
            val pendingGroup = goAsync()
            val appCtx = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val planner = appCtx.container.planner
                    ids.forEach { if (action == ACTION_TAKE_GROUP) planner.markTaken(it) else planner.markSkipped(it) }
                    ids.forEach { Notifications.dismiss(appCtx, it) }
                } finally {
                    pendingGroup.finish()
                }
            }
            return
        }

        val doseId = intent.getLongExtra(Notifications.EXTRA_DOSE_ID, -1L)
        if (doseId < 0) return

        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    // Отметки снимают статус PENDING, а значит и обрывают цепочку повторов.
                    ACTION_TAKEN -> app.container.planner.markTaken(doseId)
                    ACTION_SKIPPED -> app.container.planner.markSkipped(doseId)
                    // Отложить: статус не меняем; момент запоминается в приёме, чтобы пересборка
                    // будильников его не затёрла.
                    ACTION_SNOOZE -> app.container.planner.snooze(doseId, Settings(app).snoozeMinutes)
                    // «Еда» из напоминания о еде: отметка общая на день — приём встанет на «еда + N» и напомнит сам.
                    ACTION_MEAL -> app.container.planner.recordMeal()
                }
                Notifications.dismiss(app, doseId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TAKEN = "tech.unispace.pillreminder.TAKEN"
        const val ACTION_SKIPPED = "tech.unispace.pillreminder.SKIPPED"
        const val ACTION_SNOOZE = "tech.unispace.pillreminder.SNOOZE"
        const val ACTION_MEAL = "tech.unispace.pillreminder.MEAL"
        const val ACTION_TAKE_GROUP = "tech.unispace.pillreminder.TAKE_GROUP"
        const val ACTION_SKIP_GROUP = "tech.unispace.pillreminder.SKIP_GROUP"
        const val EXTRA_DOSE_IDS = "doseIds"
    }
}
