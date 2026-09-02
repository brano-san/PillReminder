package tech.unispace.pillreminder.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import tech.unispace.pillreminder.MainActivity
import tech.unispace.pillreminder.R
import tech.unispace.pillreminder.alarm.ActionReceiver
import tech.unispace.pillreminder.alarm.Notifications
import tech.unispace.pillreminder.data.AppDatabase
import tech.unispace.pillreminder.data.CYCLE_MAX_MS
import tech.unispace.pillreminder.data.DoseStatus
import tech.unispace.pillreminder.data.Settings
import tech.unispace.pillreminder.data.today
import tech.unispace.pillreminder.ui.Lang
import tech.unispace.pillreminder.ui.formatClock

/**
 * Виджет «ближайшие приёмы». Два варианта в системном списке — узкий (одна строка сетки)
 * и широкий (две строки, с кнопкой «Выпил» и напоминанием про еду).
 * Логика одна на оба: различаются только размеры по умолчанию и то, что помещается.
 */
class PillWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // Пользователь растянул виджет — перерисовываем под новый размер.
        refresh(context)
    }

    companion object {
        /** Содержимое виджета: что показать в строках и что нажимается. */
        private data class WidgetData(
            val lines: List<String>,
            val nextDoseId: Long,
            val mealLine: String?,
        )

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val components = listOf(
                ComponentName(context, PillWidgetProvider::class.java),
                ComponentName(context, PillWidgetWideProvider::class.java),
            )
            // Узкий и широкий варианты рисуются разными макетами, поэтому идём по ним отдельно.
            val narrowIds = manager.getAppWidgetIds(components[0])
            val wideIds = manager.getAppWidgetIds(components[1])
            if (narrowIds.isEmpty() && wideIds.isEmpty()) return

            Lang.code = Settings(context).language
            val s = Lang.s
            val data = loadData(context)

            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            for ((ids, wide) in listOf(narrowIds to false, wideIds to true)) {
                for (id in ids) {
                    val views = RemoteViews(
                        context.packageName,
                        if (wide) R.layout.widget_pills_wide else R.layout.widget_pills,
                    )
                    views.setTextViewText(R.id.widget_title, s.widgetTitle)
                    // Обе строки показываем всегда: узкий виджет тоже вмещает два приёма.
                    views.setTextViewText(R.id.widget_line1, data.lines.getOrNull(0).orEmpty())
                    val second = data.lines.getOrNull(1)
                    views.setTextViewText(R.id.widget_line2, second.orEmpty())
                    views.setViewVisibility(R.id.widget_line2, if (second != null) View.VISIBLE else View.GONE)
                    views.setOnClickPendingIntent(R.id.widget_root, open)

                    if (wide) {
                        val meal = data.mealLine
                        if (meal != null) {
                            views.setViewVisibility(R.id.widget_meal, View.VISIBLE)
                            views.setTextViewText(R.id.widget_meal, meal)
                        } else {
                            views.setViewVisibility(R.id.widget_meal, View.GONE)
                        }

                        // Кнопка «Выпил» отмечает ближайший приём прямо с рабочего стола.
                        views.setTextViewText(R.id.widget_take, s.widgetTake)
                        if (data.nextDoseId >= 0) {
                            views.setViewVisibility(R.id.widget_take, View.VISIBLE)
                            views.setOnClickPendingIntent(
                                R.id.widget_take,
                                PendingIntent.getBroadcast(
                                    context,
                                    (960_000L + data.nextDoseId).toInt(),
                                    Intent(context, ActionReceiver::class.java)
                                        .setAction(ActionReceiver.ACTION_TAKEN)
                                        .setData(Uri.parse("pill://widget/" + data.nextDoseId))
                                        .putExtra(Notifications.EXTRA_DOSE_ID, data.nextDoseId),
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                                ),
                            )
                        } else {
                            views.setViewVisibility(R.id.widget_take, View.GONE)
                        }
                    }
                    manager.updateAppWidget(id, views)
                }
            }
        }

        /** Виджет обновляется редко и запрос крошечный — блокировка допустима. */
        private fun loadData(context: Context): WidgetData = runBlocking {
            withContext(Dispatchers.IO) {
                val s = Lang.s
                val db = AppDatabase.get(context)
                // «День» плавающий: берём цикл последнего пробуждения, а не календарную дату.
                val lastWake = db.wakeDao().latest()
                val cycle = lastWake?.takeIf { System.currentTimeMillis() - it.wakeAt in 0 until CYCLE_MAX_MS }
                val day = cycle?.dayEpochDay ?: today()
                val pending = db.doseDao().getDay(day)
                    .filter { it.status == DoseStatus.PENDING }
                    .sortedBy { it.plannedAt }
                    .take(2)

                val lines = pending.map { dose ->
                    // Форма выпуска нужна, чтобы писать «2 капли», а не «2 таблетки».
                    val form = db.medicationDao().getById(dose.medId)?.form ?: "Таблетка"
                    formatClock(dose.plannedAt) + "  " + dose.medNameSnapshot + " · " + s.pills(dose.amount, form)
                }

                // Про еду пишем только если ближайший приём от неё зависит.
                val nextMed = pending.firstOrNull()?.let { db.medicationDao().getById(it.medId) }
                val mealLine = when {
                    nextMed == null -> null
                    nextMed.afterMealMinutes > 0 -> s.mealAfterShort(s.duration(nextMed.afterMealMinutes))
                    nextMed.beforeMealMinutes > 0 -> s.mealBeforeShort(s.duration(nextMed.beforeMealMinutes))
                    else -> null
                }

                WidgetData(
                    lines = when {
                        lines.isNotEmpty() -> lines
                        cycle != null -> listOf(s.widgetEmpty)
                        else -> listOf(s.widgetNoPlan)
                    },
                    nextDoseId = pending.firstOrNull()?.id ?: -1L,
                    mealLine = mealLine,
                )
            }
        }
    }
}

/** Широкий вариант того же виджета: отдельная запись в системном списке. */
class PillWidgetWideProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        PillWidgetProvider.refresh(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        PillWidgetProvider.refresh(context)
    }
}
