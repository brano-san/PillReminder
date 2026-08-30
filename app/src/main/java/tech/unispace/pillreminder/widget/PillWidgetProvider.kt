package tech.unispace.pillreminder.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import tech.unispace.pillreminder.MainActivity
import tech.unispace.pillreminder.R
import tech.unispace.pillreminder.data.AppDatabase
import tech.unispace.pillreminder.data.DoseStatus
import tech.unispace.pillreminder.data.Settings
import tech.unispace.pillreminder.data.today
import tech.unispace.pillreminder.ui.Lang
import tech.unispace.pillreminder.ui.formatClock

/** Виджет «следующие две таблетки»: время и название, нажатие открывает приложение. */
class PillWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        refresh(context)
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, PillWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return

            Lang.code = Settings(context).language
            val s = Lang.s

            // Виджет обновляется редко и запрос крошечный — блокировка допустима.
            val lines = runBlocking {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.get(context)
                    val day = today()
                    val hasWake = db.wakeDao().getDay(day) != null
                    val pending = db.doseDao().getDay(day)
                        .filter { it.status == DoseStatus.PENDING }
                        .sortedBy { it.plannedAt }
                        .take(2)
                        .map { formatClock(it.plannedAt) + "  " + it.medNameSnapshot }
                    when {
                        pending.isNotEmpty() -> pending
                        hasWake -> listOf(s.widgetEmpty)
                        else -> listOf(s.widgetNoPlan)
                    }
                }
            }

            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_pills)
                views.setTextViewText(R.id.widget_title, s.widgetTitle)
                views.setTextViewText(R.id.widget_line1, lines.getOrNull(0).orEmpty())
                views.setTextViewText(R.id.widget_line2, lines.getOrNull(1).orEmpty())
                views.setOnClickPendingIntent(R.id.widget_root, open)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
