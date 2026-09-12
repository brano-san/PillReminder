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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import tech.unispace.pillreminder.MainActivity
import tech.unispace.pillreminder.R
import tech.unispace.pillreminder.alarm.ActionReceiver
import tech.unispace.pillreminder.alarm.Notifications
import tech.unispace.pillreminder.data.AppDatabase
import tech.unispace.pillreminder.data.DoseStatus
import tech.unispace.pillreminder.data.EARLY_TAKE_THRESHOLD_MS
import tech.unispace.pillreminder.data.Settings
import tech.unispace.pillreminder.data.byClock
import tech.unispace.pillreminder.data.isActive
import tech.unispace.pillreminder.data.mealSatisfied
import tech.unispace.pillreminder.data.today
import tech.unispace.pillreminder.ui.Lang
import tech.unispace.pillreminder.ui.formatClock

/**
 * Виджет «ближайшие приёмы». Два варианта в системном списке — узкий (одна строка сетки)
 * и широкий (две строки с подстроками, кнопка «Выпито»).
 * Логика одна на оба: различаются только размеры по умолчанию и то, что помещается.
 * Цвет и прозрачность фона, цвет текста — из настроек ([Settings.widgetColor] и соседние).
 * «День» определяется так же, как на главном экране ([tech.unispace.pillreminder.data.isActive]):
 * после «Сон» и без подъёма виджет показывает только приёмы «по часам».
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
        /** Один приём на виджете: строка «время · название · количество · дозировка», подстрока с условиями и флаг «ждёт еду». */
        private data class WidgetLine(val main: String, val sub: String?, val gated: Boolean = false)

        /** Содержимое виджета: строки, ближайший приём и можно ли отметить его кнопкой прямо сейчас. */
        private data class WidgetData(
            val lines: List<WidgetLine>,
            val nextDoseId: Long,
            /** Ближайший приём в пределах часа и не ждёт еду — кнопка отмечает его; иначе она открывает приложение. */
            val nextDue: Boolean,
            /** Выпито из запланированного за день — вместо заголовка-названия приложения. */
            val progress: Pair<Int, Int>?,
        )

        /** Для вызовов из UI: `refresh` блокирует поток запросом к базе, из настроек его зовут в фоне. */
        fun refreshAsync(context: Context) {
            val app = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch { refresh(app) }
        }

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

            val settings = Settings(context)
            Lang.code = settings.language
            val s = Lang.s
            val data = loadData(context)

            val light = WidgetStyle.lightText(settings.widgetColor, settings.widgetText)
            val primaryText = WidgetStyle.textColor(primary = true, light = light)
            val secondaryText = WidgetStyle.textColor(primary = false, light = light)

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
                    // Виджет могли сжать: сначала прячем подстроки, потом вторую строку, у узкого — заголовок.
                    // Иначе содержимое режется молча, без многоточия.
                    val minHeight = manager.getAppWidgetOptions(id)?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
                    val showSubs = wide && (minHeight == 0 || minHeight >= 155)
                    val showSecond = minHeight == 0 || minHeight >= (if (wide) 125 else 58)
                    val showTitle = wide || minHeight == 0 || minHeight >= 76

                    // Фон: цвет — фильтром, прозрачность — альфой; оба метода ImageView доступны RemoteViews,
                    // и скруглённые углы shape при этом не теряются (в отличие от setBackgroundColor).
                    views.setInt(R.id.widget_bg, "setColorFilter", settings.widgetColor)
                    views.setInt(R.id.widget_bg, "setImageAlpha", WidgetStyle.alpha(settings.widgetOpacity))

                    // Заголовок — прогресс дня, когда он есть: название приложения пользователь и так знает.
                    val title = data.progress?.let { (taken, total) -> s.widgetProgress(taken, total) } ?: s.widgetTitle
                    bindLine(views, R.id.widget_title, if (showTitle) title else null, secondaryText)
                    val first = data.lines.getOrNull(0)
                    val second = data.lines.getOrNull(1)
                    // У узкого виджета подстроки нет — «ждёт «Еда»» дописываем в строку приёма.
                    fun mainOf(line: WidgetLine?): String? = line?.let { if (!wide && it.gated) it.main + " · " + s.waitsMealShort else it.main }
                    bindLine(views, R.id.widget_line1, mainOf(first), primaryText)
                    bindLine(views, R.id.widget_line2, if (showSecond) mainOf(second) else null, primaryText)
                    views.setOnClickPendingIntent(R.id.widget_root, open)

                    if (wide) {
                        // Условия приёма — под своей строкой, а не общей строкой под списком.
                        bindLine(views, R.id.widget_sub1, if (showSubs) first?.sub else null, secondaryText)
                        bindLine(views, R.id.widget_sub2, if (showSubs && showSecond) second?.sub else null, secondaryText)

                        // Кнопка «Выпито»: цвет плашки и текста — по стилю виджета, а не по теме лаунчера.
                        views.setTextViewText(R.id.widget_take, s.widgetTake)
                        views.setTextColor(R.id.widget_take, primaryText)
                        views.setInt(R.id.widget_take, "setBackgroundResource", if (light) R.drawable.widget_take_light else R.drawable.widget_take_dark)
                        if (data.nextDoseId >= 0 && first != null) {
                            views.setViewVisibility(R.id.widget_take, View.VISIBLE)
                            views.setContentDescription(R.id.widget_take, s.widgetTakeDesc(first.main))
                            // Приём далеко или ждёт еду — кнопка ведёт в приложение, где спросят «Ещё рано»:
                            // второй тап после утреннего приёма не должен отмечать вечерний.
                            val takeIntent = if (data.nextDue) {
                                PendingIntent.getBroadcast(
                                    context,
                                    (960_000L + data.nextDoseId).toInt(),
                                    Intent(context, ActionReceiver::class.java)
                                        .setAction(ActionReceiver.ACTION_TAKEN)
                                        .setData(Uri.parse("pill://widget/" + data.nextDoseId))
                                        .putExtra(Notifications.EXTRA_DOSE_ID, data.nextDoseId),
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                                )
                            } else {
                                open
                            }
                            views.setOnClickPendingIntent(R.id.widget_take, takeIntent)
                        } else {
                            views.setViewVisibility(R.id.widget_take, View.GONE)
                        }
                    }
                    manager.updateAppWidget(id, views)
                }
            }
        }

        /** Текст и цвет строки; пустая строка скрывается, чтобы не оставлять дыр в макете. */
        private fun bindLine(views: RemoteViews, viewId: Int, text: String?, color: Int) {
            views.setTextViewText(viewId, text.orEmpty())
            views.setTextColor(viewId, color)
            views.setViewVisibility(viewId, if (text.isNullOrBlank()) View.GONE else View.VISIBLE)
        }

        /** Виджет обновляется редко и запрос крошечный — блокировка допустима (из UI — через [refreshAsync]). */
        private fun loadData(context: Context): WidgetData = runBlocking {
            withContext(Dispatchers.IO) {
                val s = Lang.s
                val db = AppDatabase.get(context)
                val now = System.currentTimeMillis()
                // «День» плавающий: берём цикл последнего пробуждения, а не календарную дату — и то же
                // определение «цикл активен», что у планировщика (после «Сон» цикла нет).
                val cycle = db.wakeDao().latest()?.takeIf { it.isActive(now) }
                val day = cycle?.dayEpochDay ?: today()
                val calendar = today()
                val medsById = db.medicationDao().getAllIncludingInactive().associateBy { it.id }
                // Приёмы цикла плюс сегодняшние «по часам», если цикл начался вчера.
                val all = (db.doseDao().getDay(day) + (if (day != calendar) db.doseDao().getDay(calendar) else emptyList()))
                    .distinctBy { it.id }
                    .filter { d ->
                        val med = medsById[d.medId] ?: return@filter false
                        if (!med.active) return@filter false
                        if (med.byClock) d.dayEpochDay == calendar else d.dayEpochDay == day
                    }
                    // Без цикла карточки показывают только «по часам» — виджет тоже, иначе остатки истёкшего дня
                    // висят с живой кнопкой «Выпито».
                    .filter { d -> cycle != null || medsById[d.medId]?.byClock == true }
                val meals = db.mealDao().getAll().map { it.atMillis }
                val wakeAt = db.wakeDao().getDay(day)?.wakeAt
                val pending = all.filter { it.status == DoseStatus.PENDING }.sortedBy { it.plannedAt }
                val shown = pending.take(2)

                fun gated(dose: tech.unispace.pillreminder.data.Dose): Boolean {
                    val med = medsById[dose.medId] ?: return false
                    return !mealSatisfied(med, dose, all, meals, wakeAt)
                }

                val lines = shown.map { dose ->
                    val med = medsById[dose.medId]
                    // Форма выпуска нужна, чтобы писать «2 капли», а не «2 таблетки».
                    val form = med?.form ?: "Таблетка"
                    val main = buildString {
                        append(formatClock(dose.plannedAt)).append("  ").append(dose.medNameSnapshot)
                        append(" · ").append(s.pills(dose.amount, form))
                        med?.doseInfo?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                    }
                    val isGated = gated(dose)
                    val sub = med?.let { m ->
                        listOfNotNull(
                            if (isGated) s.waitsMealShort else null,
                            s.mealRelation(m.afterMealMinutes, m.beforeMealMinutes, m.mealCalories),
                            m.comment.takeIf { it.isNotBlank() },
                        ).joinToString(" · ").ifBlank { null }
                    }
                    WidgetLine(main, sub, isGated)
                }

                val first = shown.firstOrNull()
                val scheduled = all.filter { medsById[it.medId]?.asNeeded != true }
                WidgetData(
                    lines = when {
                        lines.isNotEmpty() -> lines
                        cycle == null && all.isEmpty() -> listOf(WidgetLine(s.widgetNoPlan, null))
                        scheduled.isEmpty() -> listOf(WidgetLine(s.widgetNothingPlanned, null))
                        scheduled.any { it.status == DoseStatus.TAKEN } && scheduled.none { it.status == DoseStatus.SKIPPED } -> listOf(WidgetLine(s.widgetEmpty, null))
                        else -> listOf(WidgetLine(s.widgetAllMarked, null))
                    },
                    nextDoseId = first?.id ?: -1L,
                    nextDue = first != null && !gated(first) && first.plannedAt - now <= EARLY_TAKE_THRESHOLD_MS,
                    progress = if (cycle != null && scheduled.isNotEmpty()) scheduled.count { it.status == DoseStatus.TAKEN } to scheduled.size else null,
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
