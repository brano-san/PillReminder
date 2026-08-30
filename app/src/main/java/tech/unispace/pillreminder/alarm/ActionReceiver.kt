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
        if (action != ACTION_TAKEN && action != ACTION_SKIPPED && action != ACTION_SNOOZE) return
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
                    // Отложить: статус не меняем, просто ставим будильник заново.
                    ACTION_SNOOZE -> {
                        val dose = app.container.db.doseDao().getById(doseId)
                        if (dose != null) {
                            val minutes = Settings(app).snoozeMinutes
                            AlarmScheduler(app).schedule(
                                dose = dose,
                                triggerAt = System.currentTimeMillis() + minutes * 60_000L,
                            )
                        }
                    }
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
    }
}
