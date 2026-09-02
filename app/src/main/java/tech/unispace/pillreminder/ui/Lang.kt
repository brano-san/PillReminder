package tech.unispace.pillreminder.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import kotlin.math.abs

/**
 * Локализация без ресурсов: все строки — поля объекта, выбор языка — одно состояние.
 * Compose-код читает [Lang.s] и перерисовывается сам при смене [Lang.code];
 * ресиверы и виджет читают то же поле (оно выставляется в App.onCreate).
 */
object Lang {
    var code by mutableStateOf("ru")
    val s: S get() = if (code == "en") EN else RU
}

interface S {
    val locale: Locale

    // Общее
    val back: String
    val closeNoSave: String
    val save: String
    val cancel: String
    val done: String
    val delete: String
    val edit: String
    val next: String
    val undo: String
    fun stepOf(n: Int, total: Int): String

    // Нижняя навигация
    val tabPills: String
    val tabHistory: String
    val tabNotes: String
    val tabSettings: String

    // Главный экран
    val goodMorning: String
    val wakeIntro: String
    val iWokeUp: String
    fun wokeAt(time: String): String
    val dayPlanned: String
    val shiftDay: String
    val emptyTitle: String
    val emptyBody: String
    val deliveryWarnTitle: String
    val deliveryWarnBody: String
    val asNeededShort: String
    fun takenTodayCount(n: Int): String
    val takeNow: String
    fun notTodayEveryN(n: Int): String
    val waitingWake: String
    fun allDone(taken: Int, total: Int): String
    fun intakeOf(n: Int, total: Int): String
    val took: String
    val skip: String
    val pillFab: String
    fun waitsFor(name: String): String
    fun afterMed(name: String, delay: String): String
    fun perIntake(amount: String): String

    // Мастер таблетки
    val newPill: String
    val editPill: String
    val finishCourseNow: String
    val nameQ: String
    val nameBody: String
    val nameLabel: String
    val namePlaceholder: String
    val nameOptionalHint: String
    val formQ: String
    val formBody: String
    val otherForm: String
    val customFormLabel: String
    val customFormPlaceholder: String
    val commentQ: String
    val commentBody: String
    val commentLabel: String
    val commentPlaceholder: String
    val commentHint: String
    val amountQ: String
    val amountBody: String
    val amountLabel: String
    val doseInfoLabel: String
    val doseInfoPlaceholder: String
    val doseInfoSupport: String
    val freqQ: String
    val freqBody: String
    val asNeededTitle: String
    val asNeededBody: String
    val perDaySection: String
    val otherNumber: String
    val everyNSection: String
    val everyDayChip: String
    val everyOtherDayChip: String
    val every3DaysChip: String
    val otherPeriodLabel: String
    fun scheduleResult(text: String): String
    val durationQ: String
    val durationBody: String
    val durUnlimited: String
    val durWeek: String
    val dur2Weeks: String
    val durMonth: String
    val durationLabel: String
    val durationNote: String
    val intervalQ: String
    val intervalBodyMulti: String
    val intervalBodySingle: String
    val hoursLabel: String
    val minutesLabel: String
    val firstDoseQ: String
    val firstDoseBody: String
    val fromWake: String
    val afterOtherPill: String
    val linkPickLabel: String
    val linkDelayLabel: String
    val linkNoMeds: String
    val immediately: String
    val offsetLabel: String
    val summaryTitle: String
    fun summaryPreview(times: String): String
    val forms: List<String>

    // Статистика
    val tabJournal: String
    val tabCalendar: String
    fun wakeAtLabel(time: String): String
    val wakeNotMarked: String
    val noIntakes: String
    fun takenAt(time: String): String
    fun skippedAt(time: String): String
    fun plannedAt(time: String): String
    fun planLabel(time: String): String
    val heatLegend: String
    val monthNames: List<String>
    val weekPrev: String
    val weekNext: String

    // Заметки и врачи
    val notesTab2: String
    val visitsTab2: String
    val notesEmptyTitle: String
    val notesEmptyBody: String
    val noteFab: String
    val visitFab: String
    val newNote: String
    val editNote: String
    val noteNameQ: String
    val noteTimeSection: String
    val noteTimeHint: String
    val noteDescQ: String
    val noteDescBody: String
    val noteDescLabel: String
    val noteBodyQ: String
    val noteBodyLabel: String
    val timeDialogTitle: String
    val visitsEmptyTitle: String
    val visitsEmptyBody: String
    val upcomingVisits: String
    val pastVisits: String
    val newVisit: String
    val editVisit: String
    val visitTitleLabel: String
    val visitTitlePlaceholder: String
    val visitCommentLabel: String

    // Настройки
    val settingsTitle: String
    val repeatsCard: String
    fun repeatsOn(interval: Int, count: Int): String
    val repeatsOff: String
    val soundCard: String
    val soundAlarmShort: String
    val soundNormalShort: String
    val fullScreenShort: String
    val notifShort: String
    val deliveryCard: String
    val deliveryOkSub: String
    val deliveryBadSub: String
    val visitsCard: String
    fun visitsCardSub(n: Int): String
    val widgetCard: String
    val widgetCardSub: String
    val widgetAdd: String
    val widgetUnsupported: String
    val languageCard: String
    val repeatTitle: String
    val repeatBody: String
    val repeatHowOften: String
    val repeatHowMany: String
    val customIntervalLabel: String
    fun repeatTotal(duration: String): String
    val soundScreenTitle: String
    val alarmSoundTitle: String
    val alarmSoundBody: String
    val notifSoundTitle: String
    val notifSoundBody: String
    val pickSound: String
    val fullScreenTitle: String
    val fullScreenBody: String
    val allowFullScreen: String
    val fsi14Note: String
    val testSection: String
    val testHint: String
    val testNormal: String
    val testFullScreen: String
    val testScheduled: String
    val testFsScheduled: String
    val deliveryTitle: String
    val deliveryIntro: String
    val stepNotifTitle: String
    val stepNotifBody: String
    val allow: String
    val openBtn: String
    val stepAlarmTitle: String
    val stepAlarmBodyOk: String
    val stepAlarmBodyBad: String
    val stepBatteryTitle: String
    val stepBatteryBody: String
    val configure: String
    val stepDndTitle: String
    val stepDndBody: String
    val grantAccess: String
    val stepAutostartTitle: String
    val openAppSettings: String
    val extrasTitle: String
    val extrasBody: String
    val checkAgain: String
    val allAllowed: String
    fun remaining(items: String): String
    val notifWord: String
    val alarmsWord: String
    val batteryWord: String
    fun diag(notif: Boolean, alarms: Boolean, battery: Boolean, dnd: Boolean): String
    val visitRemindersTitle: String
    val visitRemindersBody: String
    val offset2d: String
    val offset1d: String
    val offset3h: String
    fun vendorHint(manufacturer: String): String

    // Уведомления и будильник
    fun timeToTake(name: String): String
    fun reminderN(n: Int): String
    val testTitle: String
    val testFsTitle: String
    val testBody: String
    val channelDefaultName: String
    val channelDefaultDesc: String
    val channelAlarmName: String
    val channelAlarmDesc: String
    val channelVisitsName: String
    val timeToTakeFallback: String
    val fsClose: String
    val visitNotifTitle: String

    // Виджет
    val widgetTitle: String
    val widgetEmpty: String
    val widgetNoPlan: String

    // Форматирование
    /** Количество за приём словами; [form] — форма выпуска из MED_FORMS (RU-ключ). */
    fun pills(amount: Double, form: String = "Таблетка"): String
    fun countdown(deltaMs: Long): String
    fun duration(minutes: Int): String
    fun schedule(timesPerDay: Int, intervalMinutes: Int, everyNDays: Int): String
    val todayWord: String
    val yesterdayWord: String

    // Каталог, запас, приватность, напоминание проснуться
    fun notesCount(n: Int): String
    fun visitsCount(n: Int): String
    fun libraryCount(n: Int): String
    val stockHintEmpty: String
    val reportBtn: String
    val overlayTitle: String
    val overlayBody: String
    val overlayAllow: String
    val overlayOk: String
    val fsLockHint: String
    val confirmDeleteTitle: String
    val stockLabel: String
    val stockSupport: String
    fun stockLeft(n: String): String
    val lowStockTitle: String
    fun lowStockBody(name: String, left: String): String
    val privacyTitle: String
    val privacyBody: String
    val miscCard: String
    val miscCardSub: String
    val thresholdLabel: String
    val wakeRemindCard: String
    val wakeRemindBody: String
    val wakeRemindTime: String
    val wakeRemindNotifTitle: String
    val wakeRemindNotifBody: String
    val libraryTab: String
    val libraryEmptyTitle: String
    val libraryEmptyBody: String
    val libraryFab: String
    val newLibEntry: String
    val editLibEntry: String
    val libNameLabel: String
    val libStartLabel: String
    val libEndLabel: String
    val libEndHint: String
    val libEffectLabel: String
    val libFeelingLabel: String
    val fromLibraryHint: String
    val fsPermWarn: String

    // Трекеры
    val tabTrackers: String
    val trackerWeight: String
    val trackerMood: String
    val trackerSleep: String
    val trackerFab: String
    val trackersEmptyTitle: String
    val trackersEmptyBody: String
    val newTracker: String
    val editTracker: String
    val trackerTypeQ: String
    val heightLabel: String
    val sexLabel: String
    val sexM: String
    val sexF: String
    val askEveryLabel: String
    val askAtLabel: String
    val remindSwitch: String
    val allTrackersExist: String
    val addValue: String
    val windowLabel: String
    val customWindowLabel: String
    val minLabel: String
    val maxLabel: String
    val avgLabel: String
    val logTitle: String
    val bmiTitle: String
    fun bmiValue(v: String): String
    val bmiTable: String
    val bmiUnder: String
    val bmiNormal: String
    val bmiPre: String
    val bmiOb1: String
    val bmiOb2: String
    val bmiOb3: String
    val bmiNeedHeight: String
    val weightLabel: String
    val moodLabel: String
    val sleepQualityLabel: String
    val sleepWentLabel: String
    val sleepWokeLabel: String
    fun sleptFor(d: String): String
    val awakeningsLabel: String
    val sleepTagsLabel: String
    val sleepTagPresets: List<String>
    val customTagLabel: String
    val noteLabel: String
    fun trackerDoneToday(time: String): String
    val trackerNotToday: String
    fun lastEntryAgo(days: Long): String
    val neverRecorded: String
    fun trackerNotifTitle(name: String): String
    val trackerNotifBody: String
    val miniPointsLabel: String
    val notEnoughData: String

    // Отложить, тихие часы, «выпил всё», undo
    fun snoozeAction(n: Int): String
    val snoozeSettingTitle: String
    fun snoozed(n: Int): String
    val quietTitle: String
    val quietBody: String
    val quietFrom: String
    val quietTo: String
    fun takeAllBtn(n: Int): String
    fun snackTaken(name: String): String
    fun snackSkipped(name: String): String

    // Бэкап
    val backupCard: String
    val backupCardSub: String
    val backupTitle: String
    val exportJson: String
    val exportJsonBody: String
    val exportCsvDoses: String
    val exportCsvTrackers: String
    val exportBtn: String
    val importJson: String
    val importBody: String
    val importDone: String
    val importError: String
    val exportDone: String

    // Отчёт для врача
    val reportCard: String
    val reportCardSub: String
    val reportTitle: String
    val periodLabel: String
    fun periodDays(n: Int): String
    val reportShare: String
    val reportSave: String
    val repAdherence: String
    val repPlanned: String
    val repTaken: String
    val repSkipped: String
    val repMeds: String
    val repNotes: String
    val repVisits: String
    val repNoData: String

    // Корреляции
    val corrTitle: String
    val corrButton: String
    val corrSeries1: String
    val corrSeries2: String
    val seriesSleepHours: String
    val seriesAdherence: String
    fun corrCoef(r: String): String
    val corrNotEnough: String

    // Фото
    val photoPick: String
    val photoRemove: String

    // Разнос разделов, туториал, шаблоны трекеров, отчёт-PDF и прочее
    val libraryCardSub: String
    val openLibrary: String
    val privacyCard: String
    val stockCard: String
    val chartsCard: String
    val chartsCardSub: String
    val reportSavePdf: String
    val repIntakes: String
    val repTrackers: String
    val reportSections: String
    fun visitOffsetLabel(minutes: Int): String
    val customOffsetHours: String
    val addBtn: String
    val restoreConfirmTitle: String
    val restoreConfirmBody: String
    val shiftConfirmTitle: String
    val shiftConfirmBody: String
    val askTimesLabel: String
    val addTime: String
    val createTracker: String
    val weightTemplateBody: String
    val moodTemplateBody: String
    val sleepTemplateBody: String
    val windowAll: String
    val fsOpenNow: String
    val tutorialCard: String
    val tutorialCardSub: String
    val tutorialStart: String
    val tutorialSkip: String
    val tutorialSlides: List<Pair<String, String>>

    // Советы, внешний вид, фото
    val tipsButton: String
    val tipsTitle: String
    val tips: List<Pair<String, String>>
    val photoWhyHint: String
    val appearanceCard: String
    val appearanceCardSub: String
    val homeModeLabel: String
    val homeFull: String
    val homeCompact: String

    // Дозировка, группы заметок, прочие мелочи UX
    val doseValueLabel: String
    val doseUnits: List<String>
    val otherUnit: String
    val customUnitLabel: String
    val amountSection: String
    val doseSection: String
    val stockSection: String
    val intervalSection: String
    val intervalAutoHint: String
    val earlyTitle: String
    fun earlyBody(time: String, left: String): String
    val earlyConfirm: String
    val toToday: String
    fun versionLabel(v: String): String

    // Расписание «по часам», напоминания о визитах, «отложить»
    val scheduleSection: String
    val scheduleBody: String
    val modeWake: String
    val modeClock: String
    val clockTimesTitle: String
    val clockTimesHint: String
    val clockNoOffset: String
    val byClockShort: String
    val visitCustomAdd: String
    val visitCustomTitle: String
    val daysField: String
    val hoursField: String
    val snoozeOptionsTitle: String
    val snoozeOptionsBody: String
    val askTimesHint: String
    fun snoozeFor(minutes: String): String

    // Плавающий день, сон по кнопкам, еда, справка по корреляциям
    val later: String
    val dayDoneTitle: String
    val dayDoneBody: String
    val dayDoneHome: String
    val newDayBtn: String
    val resetDayTitle: String
    val resetDayBody: String
    val resetDayConfirm: String
    val bedtimeBtn: String
    fun bedtimeSaved(time: String): String
    val mealBtn: String
    fun mealSaved(time: String): String
    val sleepWakeQuality: String
    val sleepRateBtn: String
    val sleepAutoBadge: String
    val sleepAdviceTitle: String
    fun sleepAdvice(bed: String, wake: String, duration: String): String
    fun sleepAdviceNeedMore(n: Int): String
    val sleepRateTitle: String
    val askSleepTitle: String
    val askSleepBody: String
    val mealSection: String
    val mealSectionBody: String
    val mealNo: String
    fun mealAfter(duration: String): String
    val apartSection: String
    val apartSectionBody: String
    val apartNo: String
    fun apartFor(duration: String): String
    val corrInfoTitle: String
    val corrInfoBody: String
    val corrPairsTitle: String

    // Быстрое сохранение, шаблоны, дубли, поиск, теги, биометрия
    fun durationLabelShort(days: Int): String
    val homeActionsTitle: String

    // Третий пакет правок: сон, журнал, виджеты, конфликты правил
    val periodDaysLabel: String
    val periodCustomBtn: String
    fun periodCustomSet(period: String): String
    val dndWord: String
    val mealImmediately: String
    val sleepAdviceNeedData: String
    val wakeRatingLine: String
    val sleepShortTitle: String
    fun sleepShortBody(duration: String): String
    val sleepShortConfirm: String
    val importFromHistoryTitle: String
    fun importFromHistoryBody(n: Int): String
    val importBtn: String
    val heightFieldLabel: String
    val journalFilters: String
    val filterDoses: String
    val filterMeals: String
    val filterSleep: String
    val eventWokeUp: String
    val eventBed: String
    val eventMeal: String
    val pickWidgetTitle: String
    val widgetNarrowName: String
    val widgetWideName: String
    val apartConflictHint: String

    // Второй пакет UX-правок: цикл дня, графики, рекомендации, отчёт
    fun bedtimeShort(time: String): String
    val newDayConfirmTitle: String
    val newDayConfirmBody: String
    val recommendBtn: String
    val recommendTitle: String
    val recommendBody: String
    val fromMeal: String
    val chartStyleTitle: String
    val chartStyleBody: String
    val chartSmooth: String
    val chartSharp: String
    val createRemaining: String
    val corrReportSection: String
    val overlayMissing: String
    val heightSubsection: String
    val sexSubsection: String
    val summaryHint: String
    val visitsCalendarHint: String
    fun mealsAt(times: String): String

    // Пакет правок UX: мастер, трекеры, отчёт, еда, полноэкранный будильник, история версий
    val formSection: String
    val commentSection: String
    val freqDurationQ: String
    val freqDurationBody: String
    val conditionsQ: String
    val conditionsBody: String
    val summaryQ: String
    val summaryBody: String
    val firstDoseSection: String
    val apartPickLabel: String
    val apartAny: String
    val mealAfterSection: String
    val mealBeforeSection: String
    val mealNone: String
    val mealCustom: String
    val mealCustomTitle: String
    fun mealAfterShort(duration: String): String
    fun mealBeforeShort(duration: String): String
    val trackerRemindSection: String
    val trackerTimesSection: String
    val trackerPresetsLabel: String
    val trackerSelectedLabel: String
    val trackerAddOwnTime: String
    val trackerBodySection: String
    val trackerNoTimes: String
    val chartPointsLabel: String
    val reportPeriodSection: String
    val reportSectionsTitle: String
    val reportExportTitle: String
    val reportPreviewTitle: String
    fun fsCloseIn(duration: String): String
    val fsCloseNoRepeat: String
    val changelogTitle: String
    val versionUnreleased: String
    val bedtimeDone: String
    val homeActionsBody: String
    val hideActions: String
    val quickSaveBtn: String
    val quickSaveTitle: String
    val quickSaveBody: String
    val quickSaveMore: String
    val detailsQ: String
    val detailsBody: String
    val templatesSection: String
    val tplMorningEvening: String
    val tplAfterMeals: String
    val tplCourse10: String
    val photoPromptTitle: String
    val photoPromptBody: String
    val photoPromptAdd: String
    val duplicateBtn: String
    val copySuffix: String
    val quickMoodTitle: String
    val quickWeightTitle: String
    val savedShort: String
    val createAllTrackers: String
    fun groupNotifTitle(n: Int): String
    val takeAllAction: String
    val widgetTake: String
    fun stockUntil(date: String): String
    val streakTitle: String
    fun streakDays(n: Int): String
    val adherenceTitle: String
    val searchHint: String
    val noteTagsLabel: String
    val noteMedLabel: String
    val noteMedNone: String
    val lockTitle: String
    val lockBody: String
    val lockPrompt: String
    val lockUnlock: String
    val lockUnavailable: String
    val groupToday: String
    val groupYesterday: String
    val groupWeek: String
    val groupMonth: String
    val groupOlder: String
    val tutorialBtn: String
    val thresholdShort: String
    val customOffsetTime: String
    val windowOther: String
    val sleepTimesSwitch: String
    val corrPick: String
    val recordsTab: String
    val noEntriesYet: String
    val repSchedule: String
}

private fun ruPlural(n: Int, one: String, few: String, many: String): String = when {
    n % 10 == 1 && n % 100 != 11 -> one
    n % 10 in 2..4 && n % 100 !in 12..14 -> few
    else -> many
}

object RU : S {
    override val locale: Locale = Locale("ru")

    override val back = "Назад"
    override val closeNoSave = "Закрыть без сохранения"
    override val save = "Сохранить"
    override val cancel = "Отмена"
    override val done = "Готово"
    override val delete = "Удалить"
    override val edit = "Изменить"
    override val next = "Далее"
    override val undo = "Вернуть"
    override fun stepOf(n: Int, total: Int) = "Шаг $n из $total"

    override val tabPills = "Главная"
    override val tabHistory = "История"
    override val tabNotes = "Записи"
    override val tabSettings = "Настройки"

    override val goodMorning = "Доброе утро"
    override val wakeIntro = "Расписание на сегодня ещё не построено. Нажмите, когда проснулись, — " +
        "все приёмы отсчитаются от этого момента."
    override val iWokeUp = "Подъём"
    override fun wokeAt(time: String) = "Проснулись в $time"
    override val dayPlanned = "День уже размечен"
    override val shiftDay = "Сдвинуть"
    override val emptyTitle = "Пока пусто"
    override val emptyBody = "Добавьте первую таблетку кнопкой внизу."
    override val deliveryWarnTitle = "Напоминания могут не прийти"
    override val deliveryWarnBody = "Не все разрешения выданы. Нажмите, чтобы настроить."
    override val asNeededShort = "по необходимости"
    override fun takenTodayCount(n: Int) = "Сегодня принято: $n"
    override val takeNow = "Выпил сейчас"
    override fun notTodayEveryN(n: Int) =
        "Сегодня не нужно — приём раз в $n " + ruPlural(n, "день", "дня", "дней")
    override val waitingWake = "Ждём кнопку «Подъём»"
    override fun allDone(taken: Int, total: Int) = "Всё выпито на сегодня · $taken из $total"
    override fun intakeOf(n: Int, total: Int) = "Приём $n из $total"
    override val took = "Выпито"
    override val skip = "Пропустить"
    override val pillFab = "Таблетка"
    override fun waitsFor(name: String) = "Ждёт таблетку «$name»"
    override fun afterMed(name: String, delay: String) = "через $delay после «$name»"
    override fun perIntake(amount: String) = "$amount за приём"

    override val newPill = "Новая таблетка"
    override val editPill = "Изменить"
    override val finishCourseNow = "Завершить курс сейчас"
    override val nameQ = "Как называется таблетка?"
    override val nameBody = "Название видно на главном экране и в уведомлении, " +
        "когда придёт время её выпить."
    override val nameLabel = "Название"
    override val namePlaceholder = "Магний B6"
    override val nameOptionalHint = "Без названия сохранить нельзя"
    override val formQ = "Форма выпуска"
    override val formBody = "Что это вообще — таблетка, укол, капли? Ни на что не влияет, " +
        "просто подписывается на главном экране."
    override val otherForm = "Другое"
    override val customFormLabel = "Своя форма"
    override val customFormPlaceholder = "пластырь"
    override val commentQ = "Ваша пометка"
    override val commentBody = "Свободный текст — он будет висеть прямо под названием на главном " +
        "экране и попадёт в уведомление. Сюда пишут то, что легко забыть."
    override val commentLabel = "Комментарий"
    override val commentPlaceholder = "Запивать полным стаканом воды"
    override val commentHint = "Личная пометка: чем запивать, куда колоть, что проверить, " +
        "«не вместе с кальцием». Связь с едой задаётся отдельным блоком. Можно оставить пустым."
    override val amountQ = "Сколько за один приём?"
    override val amountBody = "Не за день, а именно за один раз. Дробные значения тоже можно — " +
        "например 0.5, если таблетку надо делить."
    override val amountLabel = "Штук за приём"
    override val doseInfoLabel = "Дозировка, для справки"
    override val doseInfoPlaceholder = "500 мг"
    override val doseInfoSupport = "Ни на что не влияет, подписывается для удобства"
    override val freqQ = "Как часто принимать?"
    override val freqBody = "Сколько раз за день — и надо ли пропускать дни."
    override val asNeededTitle = "По необходимости"
    override val asNeededBody = "Расписание и напоминания не строятся — на главном " +
        "экране будет кнопка, просто фиксирующая приём."
    override val perDaySection = "Раз в день"
    override val otherNumber = "Другое число"
    override val everyNSection = "Раз в сколько дней"
    override val everyDayChip = "каждый день"
    override val everyOtherDayChip = "через день"
    override val every3DaysChip = "раз в 3 дня"
    override val otherPeriodLabel = "Другой период, в днях"
    override fun scheduleResult(text: String) = "Получается: $text"
    override val durationQ = "Как долго длится курс?"
    override val durationBody = "Когда дни закончатся, таблетка сама пропадёт с главного экрана. " +
        "Отсчёт с первого дня приёма."
    override val durUnlimited = "бессрочно"
    override val durWeek = "неделя"
    override val dur2Weeks = "2 недели"
    override val durMonth = "месяц"
    override val durationLabel = "Дней курса (0 — без ограничения)"
    override val durationNote = "Курс можно закончить и досрочно — кнопкой ниже."
    override val intervalQ = "Промежуток между приёмами"
    override val intervalBodyMulti = "Через сколько после предыдущего приёма пить следующий. " +
        "Отсчёт идёт от момента, когда вы нажали «выпил», а не от плана."
    override val intervalBodySingle = "Приём один раз в день — промежуток ни на что не влияет. " +
        "Можно просто идти дальше."
    override val hoursLabel = "часов"
    override val minutesLabel = "минут"
    override val firstDoseQ = "Когда первый приём?"
    override val firstDoseBody = "От чего отсчитывать первую таблетку дня: от кнопки " +
        "«Подъём» или от приёма другой таблетки. Так разводятся утренние: одна " +
        "«за 15 минут до еды» — сразу, другая — через 2 часа после первой."
    override val fromWake = "От пробуждения"
    override val afterOtherPill = "После другой таблетки"
    override val linkPickLabel = "После какой таблетки"
    override val linkDelayLabel = "Через сколько минут после неё"
    override val linkNoMeds = "Пока не с чем связывать — добавьте сначала другую таблетку."
    override val immediately = "сразу"
    override val offsetLabel = "Минут после пробуждения"
    override val summaryTitle = "Итог"
    override fun summaryPreview(times: String) =
        "Если проснуться в 8:00, приёмы встанут на $times"
    override val forms = listOf("Таблетка", "Инъекция", "Раствор", "Капли", "Ингалятор", "Порошок", "Свечи")

    override val tabJournal = "Журнал"
    override val tabCalendar = "Календарь"
    override fun wakeAtLabel(time: String) = "Подъём в $time"
    override val wakeNotMarked = "Подъём не отмечен"
    override val noIntakes = "В этот день приёмов не было."
    override fun takenAt(time: String) = "Выпито в $time"
    override fun skippedAt(time: String) = "Пропущено в $time"
    override fun plannedAt(time: String) = "Запланировано на $time"
    override fun planLabel(time: String) = "план $time"
    override val heatLegend = "Зелёный — в этот день выпито всё. Оранжевый — часть приёмов пропущена: " +
        "чем бледнее, тем больше пропусков. Красный — не выпито ничего. Серый — приёмов не планировалось. " +
        "Нажатие на день открывает его журнал."
    override val monthNames = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    )
    override val weekPrev = "Неделя назад"
    override val weekNext = "Неделя вперёд"

    override val notesTab2 = "Заметки"
    override val visitsTab2 = "Визиты"
    override val notesEmptyTitle = "Заметок пока нет"
    override val notesEmptyBody = "Самочувствие, побочки, вопросы врачу — всё сюда."
    override val noteFab = "Заметка"
    override val visitFab = "Визит"
    override val newNote = "Новая заметка"
    override val editNote = "Изменить заметку"
    override val noteNameQ = "Как назвать заметку?"
    override val noteTimeSection = "Время заметки"
    override val noteTimeHint = "По умолчанию — текущие. Нажмите, чтобы поменять."
    override val noteDescQ = "Короткое описание"
    override val noteDescBody = "Видно в списке заметок под названием. Можно пропустить."
    override val noteDescLabel = "Описание"
    override val noteBodyQ = "Текст заметки"
    override val noteBodyLabel = "Текст"
    override val timeDialogTitle = "Время"
    override val visitsEmptyTitle = "Визитов пока нет"
    override val visitsEmptyBody = "Добавьте приём у врача — и приложение напомнит о нём заранее."
    override val upcomingVisits = "Предстоящие"
    override val pastVisits = "Прошедшие"
    override val newVisit = "Новый визит"
    override val editVisit = "Изменить визит"
    override val visitTitleLabel = "Врач или клиника"
    override val visitTitlePlaceholder = "Терапевт Иванова"
    override val visitCommentLabel = "Комментарий"

    override val settingsTitle = "Настройки"
    override val repeatsCard = "Повторы напоминаний"
    override fun repeatsOn(interval: Int, count: Int) = "Каждые $interval мин, до $count раз"
    override val repeatsOff = "Выключены — напомнит один раз"
    override val soundCard = "Звук и экран"
    override val soundAlarmShort = "Звук будильника"
    override val soundNormalShort = "Обычный звук"
    override val fullScreenShort = "во весь экран"
    override val notifShort = "обычное уведомление"
    override val deliveryCard = "Доставка уведомлений"
    override val deliveryOkSub = "Все разрешения выданы"
    override val deliveryBadSub = "Есть невыданные разрешения — напоминания могут не прийти"
    override val visitsCard = "Напоминания о врачах"
    override fun visitsCardSub(n: Int) =
        "Выбрано напоминаний: $n"
    override val widgetCard = "Виджет"
    override val widgetCardSub = "Следующие две таблетки прямо на главном экране телефона"
    override val widgetAdd = "Добавить виджет на главный экран"
    override val widgetUnsupported = "Оболочка не поддерживает добавление виджета кнопкой — " +
        "добавьте его долгим нажатием по рабочему столу."
    override val languageCard = "Язык / Language"
    override val repeatTitle = "Повторы напоминаний"
    override val repeatBody = "Смахнули уведомление и забыли — оно вернётся. " +
        "Повторы прекращаются, как только нажато «Выпито» или «Пропустить»."
    override val repeatHowOften = "Как часто повторять"
    override val repeatHowMany = "Сколько раз повторить"
    override val customIntervalLabel = "Свой интервал, минут"
    override fun repeatTotal(duration: String) =
        "Итого будет напоминать $duration и потом отстанет."
    override val soundScreenTitle = "Звук и экран"
    override val alarmSoundTitle = "Звук будильника"
    override val alarmSoundBody = "Звук идёт по каналу будильника, а не уведомлений. Беззвучный и " +
        "вибро-режим этот канал не глушат — телефон зазвонит даже с выключенным звонком. " +
        "Громкость берётся из ползунка «Будильник». Режим «Не беспокоить» обходится, " +
        "только если выдать доступ (см. «Доставка уведомлений»)."
    override val notifSoundTitle = "Мелодия напоминания"
    override val notifSoundBody = "Какой звук играть, когда пора пить таблетку."
    override val pickSound = "Выбрать мелодию"
    override val fullScreenTitle = "Во весь экран"
    override val fullScreenBody = "При погашенном или заблокированном экране напоминание " +
        "откроется во весь экран с большими кнопками, как входящий звонок. " +
        "При разблокированном — обычное уведомление сверху."
    override val allowFullScreen = "Разрешить полноэкранные уведомления"
    override val fsi14Note = "Android 14+ требует отдельное разрешение — без него будет " +
        "обычное уведомление."
    override val testSection = "Проверка"
    override val testHint = "Заблокируйте экран после нажатия и подождите 3 секунды. " +
        "Не пришло — смотрите «Доставку уведомлений»."
    override val testNormal = "Проверить обычное уведомление"
    override val testFullScreen = "Проверить во весь экран"
    override val testScheduled = "Сигнал поставлен — придёт через 3 секунды"
    override val testFsScheduled = "Полноэкранный сигнал через 3 секунды — заблокируйте экран"
    override val deliveryTitle = "Доставка уведомлений"
    override val deliveryIntro = "Пройдите по пунктам сверху вниз. Пока хоть один горит жёлтым, " +
        "система может задержать или проглотить уведомление."
    override val stepNotifTitle = "Разрешить уведомления"
    override val stepNotifBody = "Без этого приложение вообще не сможет ничего показать."
    override val allow = "Разрешить"
    override val openBtn = "Открыть"
    override val stepAlarmTitle = "Будильники и напоминания"
    override val stepAlarmBodyOk = "Разрешено. Приложение ставит именно будильник " +
        "(setAlarmClock) — такие система не сдвигает и не душит в спящем режиме."
    override val stepAlarmBodyBad = "Android 12 и новее иначе сдвигает напоминание на удобный " +
        "системе момент — иногда на десятки минут. На Samsung пункт называется " +
        "«Будильники и напоминания»."
    override val stepBatteryTitle = "Снять ограничения батареи"
    override val stepBatteryBody = "В «Батарея» для этого приложения выберите " +
        "«Без ограничений» / «Не оптимизировать»."
    override val configure = "Настроить"
    override val stepDndTitle = "Доступ к режиму «Не беспокоить»"
    override val stepDndBody = "Нужен только для того, чтобы звук будильника пробивался сквозь " +
        "«Не беспокоить». Если этот режим вы не включаете — пункт можно пропустить."
    override val grantAccess = "Выдать доступ"
    override val stepAutostartTitle = "Автозапуск и работа в фоне"
    override val openAppSettings = "Открыть настройки приложения"
    override val extrasTitle = "Ещё пара мелочей"
    override val extrasBody = "• Не смахивайте приложение из списка недавних — на некоторых " +
        "оболочках это отменяет запланированные будильники.\n" +
        "• После перезагрузки телефона расписание восстанавливается само — " +
        "открывать приложение не нужно."
    override val checkAgain = "Проверить снова"
    override val allAllowed = "Всё разрешено — напоминания должны приходить"
    override fun remaining(items: String) = "Осталось разрешить: $items"
    override val notifWord = "уведомления"
    override val alarmsWord = "будильники"
    override val batteryWord = "батарея"
    override fun diag(notif: Boolean, alarms: Boolean, battery: Boolean, dnd: Boolean): String {
        fun yn(v: Boolean) = if (v) "да" else "нет"
        return "уведомления: " + yn(notif) + " · точные будильники: " + yn(alarms) +
            " · батарея без ограничений: " + yn(battery) + " · доступ к «Не беспокоить»: " + yn(dnd)
    }
    override val visitRemindersTitle = "Напоминания о врачах"
    override val visitRemindersBody = "За сколько напоминать о визите. Можно выбрать несколько — " +
        "придёт по уведомлению на каждый пункт."
    override val offset2d = "За 2 дня"
    override val offset1d = "За 1 день"
    override val offset3h = "За 3 часа"
    override fun vendorHint(manufacturer: String): String = when (manufacturer.lowercase()) {
        "xiaomi", "redmi", "poco" -> "Xiaomi/MIUI: Настройки → Приложения → «День приёма» → " +
            "«Автозапуск» включить, «Контроль активности» → «Нет ограничений». " +
            "Плюс в списке недавних закрепите приложение замочком."
        "huawei", "honor" -> "Huawei/Honor: Настройки → Приложения → «День приёма» → Батарея → " +
            "«Запуск приложений» переключить на ручное управление и включить все три пункта."
        "samsung" -> "Samsung (One UI) — самая душная оболочка, пройдите все четыре пункта:\n" +
            "1. Настройки → Приложения → «День приёма» → Батарея → «Без ограничений».\n" +
            "2. Настройки → Приложения → «День приёма» → «Будильники и напоминания» — включить.\n" +
            "3. Настройки → Обслуживание устройства → Батарея → «Ограничения фоновой работы»: " +
            "приложения не должно быть ни в «Спящих», ни в «Глубоко спящих».\n" +
            "4. Там же выключите «Переводить неиспользуемые приложения в спящий режим»."
        "oppo", "realme", "oneplus" -> "OPPO/realme/OnePlus: Настройки → Батарея → Оптимизация → " +
            "«Не оптимизировать», и включите автозапуск в менеджере приложений."
        "vivo" -> "vivo: i Manager → Управление приложениями → Автозапуск — включить; " +
            "в настройках батареи снять ограничения фоновой работы."
        else -> "Найдите в настройках телефона раздел автозапуска или фоновой активности " +
            "и разрешите приложению работать в фоне."
    }

    override fun timeToTake(name: String) = "Пора выпить: $name"
    override fun reminderN(n: Int) = "Напоминание $n"
    override val testTitle = "Проверка связи"
    override val testFsTitle = "Проверка полноэкранного режима"
    override val testBody = "Если вы это видите — уведомления доходят."
    override val channelDefaultName = "Напоминания о таблетках"
    override val channelDefaultDesc = "Уведомление в момент, когда пора выпить таблетку"
    override val channelAlarmName = "Напоминания со звуком будильника"
    override val channelAlarmDesc = "То же самое, но звук идёт как у будильника — " +
        "слышно даже в беззвучном режиме"
    override val channelVisitsName = "Визиты к врачу"
    override val timeToTakeFallback = "Пора выпить таблетку"
    override val fsClose = "Закрыть — напомнит ещё раз"
    override val visitNotifTitle = "Приём у врача"

    override val widgetTitle = "День приёма"
    override val widgetEmpty = "Всё выпито"
    override val widgetNoPlan = "Нажмите «Подъём» в приложении"

    override fun pills(amount: Double, form: String): String {
        // Слово подбирается по форме выпуска: «3 капли», а не «капли · 3 таблетки».
        val words = when (form) {
            "Таблетка" -> Triple("таблетка", "таблетки", "таблеток")
            "Инъекция" -> Triple("инъекция", "инъекции", "инъекций")
            "Раствор" -> Triple("доза", "дозы", "доз")
            "Капли" -> Triple("капля", "капли", "капель")
            "Ингалятор" -> Triple("вдох", "вдоха", "вдохов")
            "Порошок" -> Triple("порция", "порции", "порций")
            "Свечи" -> Triple("свеча", "свечи", "свечей")
            else -> Triple("шт.", "шт.", "шт.")
        }
        if (amount % 1.0 != 0.0) return amount.toString() + " " + words.second
        val n = amount.toInt()
        return n.toString() + " " + ruPlural(n, words.first, words.second, words.third)
    }

    override fun countdown(deltaMs: Long): String {
        val totalMinutes = deltaMs / 60_000
        if (totalMinutes in -1..0) return "сейчас"
        val late = totalMinutes < 0
        val minutes = abs(totalMinutes)
        val h = minutes / 60
        val m = minutes % 60
        val body = when {
            h > 0 && m > 0 -> "$h ч $m мин"
            h > 0 -> "$h ч"
            else -> "$m мин"
        }
        return if (late) "опоздание $body" else "через $body"
    }

    override fun duration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "$h ч $m мин"
            h > 0 -> "$h ч"
            else -> "$m мин"
        }
    }

    override fun schedule(timesPerDay: Int, intervalMinutes: Int, everyNDays: Int): String {
        val times = timesPerDay.toString() + " " + ruPlural(timesPerDay, "раз", "раза", "раз")
        val period = if (everyNDays <= 1) "в день" else "в $everyNDays " +
            ruPlural(everyNDays, "день", "дня", "дней")
        val gap = if (timesPerDay > 1) ", каждые " + duration(intervalMinutes) else ""
        return "$times $period$gap"
    }

    override val todayWord = "Сегодня"
    override val yesterdayWord = "Вчера"

    override fun notesCount(n: Int) =
        "$n " + ruPlural(n, "заметка", "заметки", "заметок")
    override val confirmDeleteTitle = "Удалить?"
    override val stockLabel = "Сколько таблеток осталось"
    override fun visitsCount(n: Int) = ruPlural(n, "врач", "врача", "врачей")
    override fun libraryCount(n: Int) = ruPlural(n, "запись", "записи", "записей")
    override val stockHintEmpty = "Пусто — не считать."
    override val reportBtn = "Отчёт"
    override val overlayTitle = "Показывать поверх других приложений"
    override val overlayBody = "Android открывает экран будильника сам только при заблокированном экране. " +
        "С этим разрешением приложение откроет его и на разблокированном телефоне."
    override val overlayAllow = "Разрешить показ поверх"
    override val overlayOk = "Разрешение выдано"
    override val fsLockHint = "После нажатия заблокируйте экран — на разблокированном Android показывает обычное уведомление."
    override val stockSupport = "Каждый приём уменьшает остаток. Порог уведомления " +
        "«заканчиваются» — в настройках."
    override fun stockLeft(n: String) = "осталось $n"
    override val lowStockTitle = "Таблетки заканчиваются"
    override fun lowStockBody(name: String, left: String) = "$name: осталось $left"
    override val privacyTitle = "Скрывать названия таблеток"
    override val privacyBody = "В уведомлении будет просто «пора выпить таблетку», " +
        "без названия и комментария — на случай чужих глаз на экране."
    override val miscCard = "Конфиденциальность и запас"
    override val miscCardSub = "Скрытие названий, порог «таблетки заканчиваются»"
    override val thresholdLabel = "Напоминать о покупке, когда остаток опустится ниже этого числа"
    override val wakeRemindCard = "Напоминание «Подъём»"
    override val wakeRemindBody = "Если к выбранному времени день не начат — придёт " +
        "уведомление с напоминанием нажать кнопку."
    override val wakeRemindTime = "Время напоминания"
    override val wakeRemindNotifTitle = "Проснулись?"
    override val wakeRemindNotifBody = "Нажмите «Подъём», чтобы построить " +
        "расписание таблеток на день."
    override val libraryTab = "Каталог"
    override val libraryEmptyTitle = "Каталог пуст"
    override val libraryEmptyBody = "Записывайте сюда лекарства: когда пили, на что влияли, " +
        "как переносились. При добавлении таблетки название можно взять отсюда."
    override val libraryFab = "Лекарство"
    override val newLibEntry = "Новое лекарство"
    override val editLibEntry = "Изменить запись"
    override val libNameLabel = "Название"
    override val libStartLabel = "Начало приёма"
    override val libEndLabel = "Конец приёма"
    override val libEndHint = "не указан — принимаю сейчас"
    override val libEffectLabel = "На что влияет"
    override val libFeelingLabel = "Самочувствие, побочки"
    override val fromLibraryHint = "Из каталога:"
    override val fsPermWarn = "Сначала выдайте разрешение на полноэкранные уведомления"

    override val tabTrackers = "Трекеры"
    override val trackerWeight = "Вес"
    override val trackerMood = "Настроение"
    override val trackerSleep = "Сон"
    override val trackerFab = "Трекер"
    override val trackersEmptyTitle = "Трекеров пока нет"
    override val trackersEmptyBody = "Вес, настроение, сон — добавьте трекер, и приложение " +
        "будет спрашивать сами данные в нужное время."
    override val newTracker = "Новый трекер"
    override val editTracker = "Настроить трекер"
    override val trackerTypeQ = "Что отслеживаем?"
    override val heightLabel = "Рост, см"
    override val sexLabel = "Пол"
    override val sexM = "Мужской"
    override val sexF = "Женский"
    override val askEveryLabel = "Спрашивать раз в N дней"
    override val askAtLabel = "Во сколько спрашивать"
    override val remindSwitch = "Напоминать уведомлением"
    override val allTrackersExist = "Все три трекера уже созданы — настройте существующие."
    override val addValue = "Записать"
    override val windowLabel = "Записей на графике"
    override val customWindowLabel = "Своё число"
    override val minLabel = "мин"
    override val maxLabel = "макс"
    override val avgLabel = "сред"
    override val logTitle = "Журнал записей"
    override val bmiTitle = "ИМТ"
    override fun bmiValue(v: String) = "ИМТ: $v"
    override val bmiTable = "Таблица ИМТ"
    override val bmiUnder = "Недовес"
    override val bmiNormal = "Норма"
    override val bmiPre = "Предожирение"
    override val bmiOb1 = "Ожирение I"
    override val bmiOb2 = "Ожирение II"
    override val bmiOb3 = "Ожирение III"
    override val bmiNeedHeight = "Укажите рост в настройках трекера, чтобы считать ИМТ."
    override val weightLabel = "Вес, кг"
    override val moodLabel = "Настроение"
    override val sleepQualityLabel = "Как спалось?"
    override val sleepWentLabel = "Лёг"
    override val sleepWokeLabel = "Встал"
    override fun sleptFor(d: String) = "Спал $d"
    override val awakeningsLabel = "Просыпался за ночь, раз"
    override val sleepTagsLabel = "Пометки"
    override val sleepTagPresets = listOf(
        "наушники", "маска", "раньше обычного", "позже обычного", "кофе", "алкоголь",
    )
    override val customTagLabel = "Своя пометка"
    override val noteLabel = "Заметка"
    override fun trackerDoneToday(time: String) = "Сегодня записано в $time"
    override val trackerNotToday = "Сегодня данных нет"
    override fun lastEntryAgo(days: Long) = "последняя запись " + when {
        days <= 1L -> "вчера"
        else -> "$days " + ruPlural(days.toInt(), "день", "дня", "дней") + " назад"
    }
    override val neverRecorded = "записей ещё не было"
    override fun trackerNotifTitle(name: String) = "Пора записать: $name"
    override val trackerNotifBody = "Откройте приложение и внесите данные."
    override val miniPointsLabel = "Точек на мини-графике трекера"
    override val notEnoughData = "Мало данных для графика — нужно хотя бы две записи."

    override fun snoozeAction(n: Int) = "Отложить $n мин"
    override val snoozeSettingTitle = "«Отложить» в уведомлении, минут"
    override fun snoozed(n: Int) = "Отложено на $n минут"
    override val quietTitle = "Тихие часы"
    override val quietBody = "Повторы напоминаний в это время не приходят — отложатся до конца " +
        "тихих часов. Первое напоминание о приёме приходит всегда."
    override val quietFrom = "С"
    override val quietTo = "До"
    override fun takeAllBtn(n: Int) = "Выпить всё, что пора ($n)"
    override fun snackTaken(name: String) = "«$name» — выпито"
    override fun snackSkipped(name: String) = "«$name» — пропущено"

    override val backupCard = "Экспорт и бэкап"
    override val backupCardSub = "Сохранить данные в файл или восстановить из него"
    override val backupTitle = "Экспорт и бэкап"
    override val exportJson = "Полный бэкап (JSON)"
    override val exportJsonBody = "Всё: лекарства, история приёмов, заметки, визиты, каталог, " +
        "трекеры. Из этого файла можно полностью восстановиться."
    override val exportCsvDoses = "История приёмов (CSV)"
    override val exportCsvTrackers = "Записи трекеров (CSV)"
    override val exportBtn = "Сохранить файл"
    override val importJson = "Восстановить из бэкапа"
    override val importBody = "Заменит ВСЕ текущие данные содержимым файла. Отменить нельзя."
    override val importDone = "Данные восстановлены"
    override val importError = "Не удалось прочитать файл"
    override val exportDone = "Файл сохранён"

    override val reportCard = "Отчёт для врача"
    override val reportCardSub = "Сводка за период: приёмы, вес, сон, настроение, заметки"
    override val reportTitle = "Отчёт для врача"
    override val periodLabel = "Период"
    override fun periodDays(n: Int) = "$n " + ruPlural(n, "день", "дня", "дней")
    override val reportShare = "Поделиться"
    override val reportSave = "Сохранить файл"
    override val repAdherence = "Дисциплина приёма"
    override val repPlanned = "запланировано"
    override val repTaken = "выпито"
    override val repSkipped = "пропущено"
    override val repMeds = "Лекарства"
    override val repNotes = "Заметки"
    override val repVisits = "Визиты к врачу"
    override val repNoData = "нет данных"

    override val corrTitle = "Корреляции"
    override val corrButton = "Корреляции"
    override val corrSeries1 = "Серия 1"
    override val corrSeries2 = "Серия 2"
    override val seriesSleepHours = "Сон, часы"
    override val seriesAdherence = "Дисциплина, %"
    override fun corrCoef(r: String) = "Корреляция r = $r"
    override val corrNotEnough = "Мало пересекающихся дней — нужно хотя бы 3."

    override val photoPick = "Выбрать фото"
    override val photoRemove = "Убрать фото"

    override val libraryCardSub = "Лекарства, которые пили: период, эффект, побочки, фото"
    override val openLibrary = "Открыть каталог"
    override val privacyCard = "Конфиденциальность"
    override val stockCard = "Запас таблеток"
    override val chartsCard = "Графики"
    override val chartsCardSub = "Мини-графики трекеров"
    override val reportSavePdf = "Сохранить PDF"
    override val repIntakes = "Приёмы"
    override val repTrackers = "Трекеры"
    override val reportSections = "Что включить в отчёт"
    override fun visitOffsetLabel(minutes: Int): String = when {
        minutes % 1440 == 0 -> {
            val d = minutes / 1440
            if (d == 7) "За неделю" else "За $d " + ruPlural(d, "день", "дня", "дней")
        }
        minutes % 60 == 0 -> {
            val h = minutes / 60
            "За $h " + ruPlural(h, "час", "часа", "часов")
        }
        else -> "За $minutes мин"
    }
    override val customOffsetHours = "Своё: за сколько часов"
    override val addBtn = "Добавить"
    override val restoreConfirmTitle = "Восстановить из бэкапа?"
    override val restoreConfirmBody = "Все текущие таблетки, история, заметки, визиты, каталог и " +
        "трекеры будут удалены и заменены содержимым файла. Отменить это нельзя."
    override val shiftConfirmTitle = "Сдвинуть день?"
    override val shiftConfirmBody = "Все ещё не выпитые приёмы на сегодня пересчитаются от " +
        "текущего момента, как будто вы проснулись только сейчас. Уже выпитое не тронется."
    override val askTimesLabel = "Во сколько спрашивать (можно несколько раз в день)"
    override val addTime = "Добавить время"
    override val createTracker = "Создать"
    override val weightTemplateBody = "Взвешивание по расписанию, график, ИМТ с цветовой шкалой."
    override val moodTemplateBody = "Оценка настроения смайликами, заметка к каждой записи."
    override val sleepTemplateBody = "Оценка сна, время лёг/встал, пробуждения, пометки."
    override val windowAll = "все"
    override val fsOpenNow = "Открыть экран будильника"
    override val tutorialCard = "Туториал"
    override val tutorialCardSub = "Показать вводный экран ещё раз"
    override val tutorialStart = "Начать"
    override val tutorialSkip = "Пропустить"
    override val tutorialSlides = listOf(
        "Всё начинается с кнопки «Подъём»" to
            "Расписание не привязано к часам. Утром нажмите кнопку — и все приёмы " +
            "отсчитаются от этого момента. Следующий приём считается от фактического " +
            "«Выпито», а не от плана.",
        "Таблетки" to
            "Добавляйте лекарства мастером по шагам: форма, комментарий, количество, " +
            "частота, курс, промежуток. Одну таблетку можно привязать к другой — «через 2 часа " +
            "после первой». Долгое нажатие по карточке удаляет, ручка ⠿ — меняет порядок.",
        "Уведомления" to
            "Напоминание повторяется, пока не нажато «Выпито» или «Пропустить». Есть " +
            "«Отложить», тихие часы, звук будильника и полноэкранный режим. Пройдите " +
            "чек-лист «Доставка уведомлений» — иначе система может их глушить.",
        "Заметки, врачи, каталог" to
            "Заметки о самочувствии с датой, визиты к врачу с напоминаниями заранее, " +
            "каталог лекарств — что пили, как переносилось, фото упаковки.",
        "Трекеры" to
            "Вес с ИМТ, настроение, сон — приложение спросит само в назначенное время. " +
            "Графики, статистика, корреляции между сериями.",
        "История и отчёт" to
            "Журнал по дням, тепловая карта дисциплины, отчёт для врача за период " +
            "(текст или PDF), бэкап всех данных в файл.",
        "Как можно планировать приём" to
            "От пробуждения — приёмы считаются от кнопки и от фактического «Выпито». " +
            "По часам — стоят на фиксированном времени и кнопку не ждут. " +
            "После другой таблетки — связанная запускается от приёма первой. " +
            "По необходимости — без расписания, отмечается вручную. " +
            "Плюс «через N дней» и курс на N дней с автоокончанием.",
        "Что ещё внутри" to
            "Отчёт для врача и бэкап всех данных в файл, виджет на рабочий стол, " +
            "повторы напоминаний и «Отложить», тихие часы, будильник во весь экран, " +
            "режим без названий в шторке, учёт остатка в упаковке и два языка.",
        "День, сон и еда — по кнопкам" to
            "День не заканчивается в полночь: он идёт от «Подъём» и живёт до 18 часов. " +
            "Когда все приёмы отмечены, приложение предложит начать новый день, а долгое нажатие " +
            "на карточку сверху сбрасывает день вручную. Кнопки «Сон» и «Еда» " +
            "наполняют трекер сна и двигают приёмы, которые нельзя пить сразу после еды.",
    )

    override val tipsButton = "Советы"
    override val tipsTitle = "Советы"
    override val tips = listOf(
        "Фотографируйте упаковку" to
            "Одно и то же действующее вещество у разных производителей — не одно и то же " +
            "лекарство. Дженерики отличаются вспомогательными веществами, чистотой субстанции " +
            "и качеством производства, поэтому переносимость и эффект могут быть другими. Если " +
            "препарат подошёл — покупайте именно его: фото упаковки в каталоге поможет не " +
            "перепутать в аптеке.",
        "Не меняйте дозу сами" to
            "Поле «дозировка» в приложении — справочное. Любое изменение дозы или отмена — " +
            "только с врачом. Отчёт за период из настроек удобно взять с собой на приём.",
        "Пейте по расписанию, а не «когда вспомнил»" to
            "Кнопка «Подъём» строит день так, чтобы промежутки между приёмами были " +
            "ровными. Пропустили — не удваивайте следующую дозу, отметьте «Пропустить».",
        "Следите за запасом" to
            "Укажите, сколько таблеток осталось — приложение напомнит о покупке заранее, " +
            "порог настраивается.",
        "Выдайте разрешения один раз" to
            "Android любит усыплять приложения. Пройдите чек-лист «Доставка уведомлений» — " +
            "иначе напоминание может прийти с опозданием или не прийти вовсе.",
    )
    override val photoWhyHint = "Зачем фото: дженерики с тем же веществом могут переноситься " +
        "иначе — состав вспомогательных веществ и качество разные. Нашли подходящий препарат — " +
        "сфотографируйте упаковку, чтобы покупать именно его."
    override val appearanceCard = "Внешний вид"
    override val appearanceCardSub = "Главный экран, мини-графики"
    override val homeModeLabel = "Карточки таблеток на главной"
    override val homeFull = "Полные"
    override val homeCompact = "Сокращённые"

    override val doseValueLabel = "Дозировка"
    override val doseUnits = listOf("мг", "мкг", "г", "мл", "МЕ", "%", "капель", "шт")
    override val otherUnit = "другое"
    override val customUnitLabel = "Своя единица"
    override val amountSection = "Штук за приём"
    override val doseSection = "Дозировка (для справки)"
    override val stockSection = "Остаток в упаковке"
    override val earlyTitle = "Ещё рано"
    override fun earlyBody(time: String, left: String) = "Этот приём запланирован на $time (через $left). Отметить как выпитый сейчас? Следующий приём отсчитается от этого момента."
    override val earlyConfirm = "Всё равно принять"
    override val scheduleSection = "Когда напоминать"
    override val scheduleBody = "От пробуждения — приёмы отсчитываются от кнопки «Подъём» и от фактического «Выпито». " +
        "По часам — приёмы стоят на выбранных временах и не ждут кнопку."
    override val modeWake = "От пробуждения"
    override val modeClock = "По часам"
    override val clockTimesTitle = "Времена приёма"
    override val clockTimesHint = "Нажмите на время, чтобы изменить."
    override val clockNoOffset = "У расписания по часам смещение от пробуждения не используется: приёмы стоят на выбранных временах."
    override val byClockShort = "по часам"
    override val visitCustomAdd = "Добавить своё время"
    override val visitCustomTitle = "За сколько до визита"
    override val daysField = "Дней"
    override val hoursField = "Часов"
    override val snoozeOptionsTitle = "Кнопки «Отложить»"
    override val snoozeOptionsBody = "Выбранные варианты появятся кнопками на полноэкранном напоминании. Первый из них — кнопка в обычном уведомлении."
    override val askTimesHint = "Нажмите на время, чтобы изменить; крестик удаляет."
    override val later = "Позже"
    override val dayDoneTitle = "Все приёмы отмечены"
    override val dayDoneBody = "На сегодня всё. Когда проснётесь — нажмите «Подъём», и день начнётся заново."
    override val dayDoneHome = "Все приёмы на сегодня выполнены."
    override val newDayBtn = "Начать новый день"
    override val resetDayTitle = "Начать день заново?"
    override val resetDayBody = "Ожидающие приёмы перепланируются от текущего момента. Уже отмеченные останутся в истории. " +
        "Пригодится, если цикл дня получился коротким и пора пить снова."
    override val resetDayConfirm = "Начать заново"
    override val bedtimeBtn = "Сон"
    override fun bedtimeSaved(time: String) = "Записано: сон с $time"
    override val mealBtn = "Еда"
    override fun mealSaved(time: String) = "Записано: еда в $time"
    override val sleepWakeQuality = "Как проснулись"
    override val sleepRateBtn = "Оценить"
    override val sleepAutoBadge = "по кнопкам"
    override val sleepAdviceTitle = "Когда лучше ложиться"
    override fun sleepAdvice(bed: String, wake: String, duration: String) =
        "По ночам, которые вы оценили выше среднего: ложиться около $bed, вставать около $wake, спать примерно $duration."
    override fun sleepAdviceNeedMore(n: Int) =
        "Для объективной подсказки нужно больше данных: хотя бы " + ruPlural(n, "$n ночь", "$n ночи", "$n ночей") +
            " со временем сна и оценкой."
    override val sleepRateTitle = "Как спалось?"
    override val askSleepTitle = "Спрашивать про сон при пробуждении"
    override val askSleepBody = "Если вы нажали «Сон», то по кнопке «Подъём» появится запись сна и просьба оценить его."
    override val mealSection = "Связь с едой"
    override val mealSectionBody = "Кнопка «Еда» на главном двигает такие приёмы вперёд, если вы поели позже."
    override val mealNo = "Неважно"
    override fun mealAfter(duration: String) = "через $duration после еды"
    override val apartSection = "Разносить с другими таблетками"
    override val apartSectionBody = "Приём отодвинется, если рядом по времени выпита другая таблетка."
    override val apartNo = "Неважно"
    override fun apartFor(duration: String) = "не ближе $duration"
    override val corrInfoTitle = "Как читать графики и пары"
    override val corrInfoBody = "На графике каждая серия нормирована по своему диапазону: сравнивать нужно форму линий, а не высоту.\n\n" +
        "Числа под графиком — коэффициент корреляции Пирсона (r) для пары серий по дням, где есть обе величины.\n\n" +
        "• r близко к +1 — растут вместе;\n" +
        "• r близко к −1 — одно растёт, другое падает;\n" +
        "• около 0 — связи не видно.\n\n" +
        "Грубо: |r| до 0,3 — слабо, 0,3–0,7 — средне, выше 0,7 — сильно. «—» значит, что общих дней меньше трёх.\n\n" +
        "Корреляция не доказывает причину: совпадение может объясняться третьим фактором или случайностью. " +
        "Это подсказка для наблюдения и разговора с врачом, а не вывод."
    override fun durationLabelShort(days: Int) = "курс " + ruPlural(days, "$days день", "$days дня", "$days дней")
    override val formSection = "Форма выпуска"
    override val commentSection = "Заметка о приёме"
    override val freqDurationQ = "Как часто и как долго?"
    override val freqDurationBody = "Сколько раз в день, с каким промежутком и сколько дней длится курс."
    override val conditionsQ = "Когда и с чем пить"
    override val conditionsBody = "Начало дня, связь с едой и разнос с другими таблетками."
    override val summaryQ = "Проверьте перед сохранением"
    override val summaryBody = "Так таблетка будет выглядеть на главном экране."
    override val firstDoseSection = "Первый приём"
    override val apartPickLabel = "С какими таблетками разносить"
    override val apartAny = "С любыми"
    override val mealAfterSection = "После еды"
    override val mealBeforeSection = "До еды"
    override val mealNone = "Неважно"
    override val mealCustom = "Своё…"
    override val mealCustomTitle = "Своё время"
    override fun mealAfterShort(duration: String) = "через $duration после еды"
    override fun mealBeforeShort(duration: String) = "за $duration до еды"
    override val trackerRemindSection = "Напоминания"
    override val trackerTimesSection = "Когда спрашивать"
    override val trackerPresetsLabel = "Добавить быстро"
    override val trackerSelectedLabel = "Выбрано"
    override val trackerAddOwnTime = "Своё время"
    override val trackerBodySection = "Параметры тела"
    override val trackerNoTimes = "Времена не выбраны — напоминаний не будет."
    override val chartPointsLabel = "Точек"
    override val reportPeriodSection = "Период"
    override val reportSectionsTitle = "Что включить"
    override val reportExportTitle = "Сохранить и отправить"
    override val reportPreviewTitle = "Предпросмотр"
    override fun fsCloseIn(duration: String) = "Закрыть — напомнит через $duration"
    override val fsCloseNoRepeat = "Закрыть — повторов нет, напоминания не будет"
    override val changelogTitle = "Что нового"
    override val versionUnreleased = "в разработке"
    override val bedtimeDone = "Сон записан"

    override fun bedtimeShort(time: String) = "Сон с $time"
    override val newDayConfirmTitle = "Начать новый день?"
    override val newDayConfirmBody = "Текущий день закроется, а приёмы будут назначены заново от этого момента. " +
        "Уже отмеченные приёмы останутся в истории. Делайте это, только если действительно начали новый цикл — " +
        "иначе таблетки сместятся."
    override val recommendBtn = "Рекомендации"
    override val recommendTitle = "Как выбрать промежуток"
    override val recommendBody = "Это только ориентир, а не назначение: 2 приёма — примерно 12 часов, 3 — 8 часов, " +
        "4 — 5 часов, больше — равномерно по времени бодрствования.\n\n" +
        "Если врач назначил другую схему — следуйте назначению врача, а не подсказке приложения."
    override val fromMeal = "После еды"
    override val chartStyleTitle = "Вид графиков"
    override val chartStyleBody = "Сглаженная линия читается легче, ломаная точнее показывает отдельные замеры."
    override val chartSmooth = "Сглаженный"
    override val chartSharp = "Ломаный"
    override val createRemaining = "Создать оставшиеся"
    override val corrReportSection = "Связи между показателями"
    override val overlayMissing = "Разрешение не выдано — экран будильника не откроется на разблокированном телефоне"
    override val heightSubsection = "Рост"
    override val sexSubsection = "Пол"
    override val summaryHint = "Проверьте, всё ли верно. Изменить — кнопкой «Назад»."
    override val visitsCalendarHint = "Точка под числом — в этот день есть визит"
    override fun mealsAt(times: String) = "еда: $times"

    override val periodDaysLabel = "Сколько дней"
    override val periodCustomBtn = "Свой период…"
    override fun periodCustomSet(period: String) = "Свой период: $period — изменить"
    override val dndWord = "«Не беспокоить»"
    override val mealImmediately = "Сразу"
    override val sleepAdviceNeedData = "Для объективной подсказки нужно больше данных о сне."
    override val wakeRatingLine = "Пробуждение"
    override val sleepShortTitle = "Совсем мало сна"
    override fun sleepShortBody(duration: String) = "С момента «Сон» прошло всего $duration. " +
        "Взрослому нужно 7–9 часов: короткий сон бьёт по вниманию, настроению и давлению. " +
        "Начать новый день всё равно?"
    override val sleepShortConfirm = "Всё равно начать день"
    override val importFromHistoryTitle = "Подтянуть данные из истории?"
    override fun importFromHistoryBody(n: Int) = "В истории есть " + ruPlural(n, "$n ночь", "$n ночи", "$n ночей") +
        " с отметками «Сон» и «Подъём». Их можно сразу перенести в трекер — оценку поставите позже."
    override val importBtn = "Перенести"
    override val heightFieldLabel = "Сантиметров"
    override val journalFilters = "Показывать"
    override val filterDoses = "Приёмы"
    override val filterMeals = "Еда"
    override val filterSleep = "Сон"
    override val eventWokeUp = "Подъём"
    override val eventBed = "Отход ко сну"
    override val eventMeal = "Приём пищи"
    override val pickWidgetTitle = "Какой виджет добавить?"
    override val widgetNarrowName = "Компактный"
    override val widgetWideName = "С кнопкой «Выпито»"
    override val apartConflictHint = "Связанная таблетка исключена: её промежуток задаётся полем «через сколько после неё»."

    override val homeActionsTitle = "Кнопки на главном экране"
    override val homeActionsBody = "Ряд «Советы · Туториал · Отчёт · Ложусь спать · Поел» под карточкой пробуждения."
    override val hideActions = "Скрыть кнопки"
    override val quickSaveBtn = "Сохранить сейчас"
    override val quickSaveTitle = "Сохранить так?"
    override val quickSaveBody = "Остальное можно настроить позже. На главном экране карточка будет выглядеть так:"
    override val quickSaveMore = "Настроить подробнее"
    override val detailsQ = "Форма и заметка"
    override val detailsBody = "Необязательный шаг: форма выпуска и личный комментарий вроде «за 15 минут до еды»."
    override val templatesSection = "Готовые схемы"
    override val tplMorningEvening = "Утро и вечер"
    override val tplAfterMeals = "3 раза после еды"
    override val tplCourse10 = "Курс 10 дней"
    override val photoPromptTitle = "Добавить фото упаковки?"
    override val photoPromptBody = "Дженерики разных производителей отличаются, и фото помогает не перепутать в аптеке. " +
        "Это можно сделать и позже: «Записи» → «Каталог» → карточка лекарства."
    override val photoPromptAdd = "Добавить фото"
    override val duplicateBtn = "Дублировать"
    override val copySuffix = "копия"
    override val quickMoodTitle = "Как настроение?"
    override val quickWeightTitle = "Вес"
    override val savedShort = "Записано"
    override val createAllTrackers = "Создать все три"
    override fun groupNotifTitle(n: Int) = ruPlural(n, "$n таблетка", "$n таблетки", "$n таблеток")
    override val takeAllAction = "Принять все"
    override val widgetTake = "Выпито"
    override fun stockUntil(date: String) = "хватит до $date"
    override val streakTitle = "Без пропусков"
    override fun streakDays(n: Int) = ruPlural(n, "$n день", "$n дня", "$n дней")
    override val adherenceTitle = "Дисциплина по таблеткам"
    override val searchHint = "Поиск"
    override val noteTagsLabel = "Теги через запятую"
    override val noteMedLabel = "О какой таблетке"
    override val noteMedNone = "Общая"
    override val lockTitle = "Вход по отпечатку или коду"
    override val lockBody = "Спрашивать отпечаток или код устройства при каждом открытии приложения."
    override val lockPrompt = "Разблокируйте, чтобы открыть приложение"
    override val lockUnlock = "Разблокировать"
    override val lockUnavailable = "На устройстве не настроены отпечаток или код блокировки"

    override val corrPairsTitle = "Пары"

    override fun snoozeFor(minutes: String) = "Отложить на $minutes"

    override fun versionLabel(v: String) = "День приёма · версия $v"
    override val toToday = "К сегодняшней дате"
    override val intervalAutoHint = "Подставлено автоматически: 2 приёма — 12 ч, 3 — 8 ч, 4 — 5 ч, больше — равномерно по 16 ч бодрствования. Если врач назначил иначе — поправьте."
    override val intervalSection = "Промежуток между приёмами"
    override val groupToday = "Сегодня"
    override val groupYesterday = "Вчера"
    override val groupWeek = "Последние 7 дней"
    override val groupMonth = "Последние 30 дней"
    override val groupOlder = "Давно"
    override val tutorialBtn = "Туториал"
    override val thresholdShort = "Порог, штук"
    override val customOffsetTime = "Своё время до визита"
    override val windowOther = "другое…"
    override val sleepTimesSwitch = "Указать, когда лёг и встал"
    override val corrPick = "Что рисовать на графике"
    override val recordsTab = "Записи"
    override val noEntriesYet = "Записей пока нет — нажмите «Записать»."
    override val repSchedule = "схема"
}

object EN : S {
    override val locale: Locale = Locale.ENGLISH

    override val back = "Back"
    override val closeNoSave = "Close without saving"
    override val save = "Save"
    override val cancel = "Cancel"
    override val done = "Done"
    override val delete = "Delete"
    override val edit = "Edit"
    override val next = "Next"
    override val undo = "Undo"
    override fun stepOf(n: Int, total: Int) = "Step $n of $total"

    override val tabPills = "Home"
    override val tabHistory = "History"
    override val tabNotes = "Records"
    override val tabSettings = "Settings"

    override val goodMorning = "Good morning"
    override val wakeIntro = "Today's schedule isn't built yet. Tap when you wake up — " +
        "every intake will be counted from that moment."
    override val iWokeUp = "I woke up"
    override fun wokeAt(time: String) = "Woke up at $time"
    override val dayPlanned = "The day is planned"
    override val shiftDay = "Shift"
    override val emptyTitle = "Nothing here yet"
    override val emptyBody = "Add your first pill with the button below."
    override val deliveryWarnTitle = "Reminders may not arrive"
    override val deliveryWarnBody = "Some permissions are missing. Tap to fix."
    override val asNeededShort = "as needed"
    override fun takenTodayCount(n: Int) = "Taken today: $n"
    override val takeNow = "Take now"
    override fun notTodayEveryN(n: Int) = "Not today — taken every $n day" + if (n > 1) "s" else ""
    override val waitingWake = "Waiting for the \"I woke up\" button"
    override fun allDone(taken: Int, total: Int) = "All done for today · $taken of $total"
    override fun intakeOf(n: Int, total: Int) = "Intake $n of $total"
    override val took = "Taken"
    override val skip = "Skip"
    override val pillFab = "Pill"
    override fun waitsFor(name: String) = "Waiting for \"$name\""
    override fun afterMed(name: String, delay: String) = "$delay after \"$name\""
    override fun perIntake(amount: String) = "$amount per intake"

    override val newPill = "New pill"
    override val editPill = "Edit"
    override val finishCourseNow = "Finish the course now"
    override val nameQ = "What is the pill called?"
    override val nameBody = "The name is shown on the main screen and in the notification " +
        "when it's time to take it."
    override val nameLabel = "Name"
    override val namePlaceholder = "Magnesium B6"
    override val nameOptionalHint = "A name is required"
    override val formQ = "Dosage form"
    override val formBody = "What is it — a pill, an injection, drops? Doesn't affect anything, " +
        "just labels it on the main screen."
    override val otherForm = "Other"
    override val customFormLabel = "Custom form"
    override val customFormPlaceholder = "patch"
    override val commentQ = "Your note"
    override val commentBody = "Free text — it stays right under the name on the main screen " +
        "and goes into the notification. Write what's easy to forget."
    override val commentLabel = "Comment"
    override val commentPlaceholder = "Wash down with a full glass of water"
    override val commentHint = "A personal note: what to wash it down with, where to inject, " +
        "\"not together with calcium\". Food relation is a separate setting. Can be left empty."
    override val amountQ = "How much per intake?"
    override val amountBody = "Per single intake, not per day. Fractions are fine — " +
        "e.g. 0.5 if the pill needs splitting."
    override val amountLabel = "Pills per intake"
    override val doseInfoLabel = "Dosage, for reference"
    override val doseInfoPlaceholder = "500 mg"
    override val doseInfoSupport = "Doesn't affect anything, just a label"
    override val freqQ = "How often to take?"
    override val freqBody = "How many times a day — and whether to skip days."
    override val asNeededTitle = "As needed"
    override val asNeededBody = "No schedule or reminders — the main screen will show a button " +
        "that simply records an intake."
    override val perDaySection = "Times per day"
    override val otherNumber = "Other number"
    override val everyNSection = "Every N days"
    override val everyDayChip = "every day"
    override val everyOtherDayChip = "every other day"
    override val every3DaysChip = "every 3 days"
    override val otherPeriodLabel = "Other period, days"
    override fun scheduleResult(text: String) = "Result: $text"
    override val durationQ = "How long is the course?"
    override val durationBody = "When the days run out, the pill disappears from the main " +
        "screen by itself. Counted from the first day."
    override val durUnlimited = "unlimited"
    override val durWeek = "a week"
    override val dur2Weeks = "2 weeks"
    override val durMonth = "a month"
    override val durationLabel = "Course days (0 — unlimited)"
    override val durationNote = "You can also finish the course early — button below."
    override val intervalQ = "Gap between intakes"
    override val intervalBodyMulti = "How long after the previous intake to take the next one. " +
        "Counted from the moment you tapped \"Taken\", not from the plan."
    override val intervalBodySingle = "Once a day — the gap doesn't matter. Just continue."
    override val hoursLabel = "hours"
    override val minutesLabel = "minutes"
    override val firstDoseQ = "When is the first intake?"
    override val firstDoseBody = "What to count the first intake from: the \"I woke up\" button " +
        "or another pill. That's how morning pills are separated: one right away, " +
        "another 2 hours after the first."
    override val fromWake = "From waking up"
    override val afterOtherPill = "After another pill"
    override val linkPickLabel = "After which pill"
    override val linkDelayLabel = "Minutes after it"
    override val linkNoMeds = "Nothing to link to yet — add another pill first."
    override val immediately = "right away"
    override val offsetLabel = "Minutes after waking up"
    override val summaryTitle = "Summary"
    override fun summaryPreview(times: String) = "If you wake up at 8:00, intakes land on $times"
    override val forms = listOf("Pill", "Injection", "Solution", "Drops", "Inhaler", "Powder", "Suppository")

    override val tabJournal = "Journal"
    override val tabCalendar = "Calendar"
    override fun wakeAtLabel(time: String) = "Woke up at $time"
    override val wakeNotMarked = "Wake-up not marked"
    override val noIntakes = "No intakes on this day."
    override fun takenAt(time: String) = "Taken at $time"
    override fun skippedAt(time: String) = "Skipped at $time"
    override fun plannedAt(time: String) = "Planned for $time"
    override fun planLabel(time: String) = "plan $time"
    override val heatLegend = "Green — everything was taken that day. Orange — some intakes were missed: " +
        "the paler, the more misses. Red — nothing was taken. Grey — nothing was planned. " +
        "Tap a day to open its journal."
    override val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    override val weekPrev = "Previous week"
    override val weekNext = "Next week"

    override val notesTab2 = "Notes"
    override val visitsTab2 = "Visits"
    override val notesEmptyTitle = "No notes yet"
    override val notesEmptyBody = "How you feel, side effects, questions for the doctor — all here."
    override val noteFab = "Note"
    override val visitFab = "Visit"
    override val newNote = "New note"
    override val editNote = "Edit note"
    override val noteNameQ = "Name the note"
    override val noteTimeSection = "Note time"
    override val noteTimeHint = "Defaults to now. Tap to change."
    override val noteDescQ = "Short description"
    override val noteDescBody = "Shown in the list under the title. Optional."
    override val noteDescLabel = "Description"
    override val noteBodyQ = "Note text"
    override val noteBodyLabel = "Text"
    override val timeDialogTitle = "Time"
    override val visitsEmptyTitle = "No visits yet"
    override val visitsEmptyBody = "Add a doctor's appointment — the app will remind you in advance."
    override val upcomingVisits = "Upcoming"
    override val pastVisits = "Past"
    override val newVisit = "New visit"
    override val editVisit = "Edit visit"
    override val visitTitleLabel = "Doctor or clinic"
    override val visitTitlePlaceholder = "Dr. Smith"
    override val visitCommentLabel = "Comment"

    override val settingsTitle = "Settings"
    override val repeatsCard = "Reminder repeats"
    override fun repeatsOn(interval: Int, count: Int) = "Every $interval min, up to $count times"
    override val repeatsOff = "Off — reminds once"
    override val soundCard = "Sound & screen"
    override val soundAlarmShort = "Alarm sound"
    override val soundNormalShort = "Normal sound"
    override val fullScreenShort = "full screen"
    override val notifShort = "regular notification"
    override val deliveryCard = "Notification delivery"
    override val deliveryOkSub = "All permissions granted"
    override val deliveryBadSub = "Missing permissions — reminders may not arrive"
    override val visitsCard = "Doctor visit reminders"
    override fun visitsCardSub(n: Int) = "Reminders selected: $n"
    override val widgetCard = "Widget"
    override val widgetCardSub = "Next two pills right on your phone's home screen"
    override val widgetAdd = "Add widget to home screen"
    override val widgetUnsupported = "This launcher doesn't support pinning by button — " +
        "add the widget by long-pressing the home screen."
    override val languageCard = "Язык / Language"
    override val repeatTitle = "Reminder repeats"
    override val repeatBody = "Swiped the notification away and forgot — it comes back. " +
        "Repeats stop as soon as you tap \"Taken\" or \"Skip\"."
    override val repeatHowOften = "How often to repeat"
    override val repeatHowMany = "How many times"
    override val customIntervalLabel = "Custom interval, minutes"
    override fun repeatTotal(duration: String) = "In total it will nag for $duration and then give up."
    override val soundScreenTitle = "Sound & screen"
    override val alarmSoundTitle = "Alarm sound"
    override val alarmSoundBody = "Sound goes through the alarm channel, not notifications. " +
        "Silent and vibrate modes don't mute it — the phone will ring even with the ringer off. " +
        "Volume comes from the \"Alarm\" slider. \"Do not disturb\" is bypassed only with " +
        "granted access (see \"Notification delivery\")."
    override val notifSoundTitle = "Reminder melody"
    override val notifSoundBody = "Which sound to play when it's time to take a pill."
    override val pickSound = "Pick a melody"
    override val fullScreenTitle = "Full screen"
    override val fullScreenBody = "With the screen off or locked, the reminder opens full screen " +
        "with big buttons, like an incoming call. When unlocked — a regular heads-up notification."
    override val allowFullScreen = "Allow full-screen notifications"
    override val fsi14Note = "Android 14+ requires a separate permission — without it you get " +
        "a regular notification."
    override val testSection = "Test"
    override val testHint = "Lock the screen after tapping and wait 3 seconds. " +
        "Nothing arrived — check \"Notification delivery\"."
    override val testNormal = "Test regular notification"
    override val testFullScreen = "Test full screen"
    override val testScheduled = "Scheduled — arrives in 3 seconds"
    override val testFsScheduled = "Full-screen signal in 3 seconds — lock the screen"
    override val deliveryTitle = "Notification delivery"
    override val deliveryIntro = "Go through the items top to bottom. While any is yellow, " +
        "the system may delay or swallow a notification."
    override val stepNotifTitle = "Allow notifications"
    override val stepNotifBody = "Without this the app can't show anything at all."
    override val allow = "Allow"
    override val openBtn = "Open"
    override val stepAlarmTitle = "Alarms & reminders"
    override val stepAlarmBodyOk = "Granted. The app sets a real alarm clock (setAlarmClock) — " +
        "the system doesn't shift or throttle those."
    override val stepAlarmBodyBad = "Android 12+ otherwise shifts the reminder to a convenient " +
        "moment — sometimes by tens of minutes. On Samsung the item is called " +
        "\"Alarms & reminders\"."
    override val stepBatteryTitle = "Lift battery restrictions"
    override val stepBatteryBody = "In \"Battery\" for this app pick \"Unrestricted\" / " +
        "\"Don't optimize\"."
    override val configure = "Configure"
    override val stepDndTitle = "\"Do not disturb\" access"
    override val stepDndBody = "Only needed so the alarm sound cuts through \"Do not disturb\". " +
        "If you never use that mode — skip this."
    override val grantAccess = "Grant access"
    override val stepAutostartTitle = "Autostart & background work"
    override val openAppSettings = "Open app settings"
    override val extrasTitle = "A couple more things"
    override val extrasBody = "• Don't swipe the app away from recents — some launchers cancel " +
        "scheduled alarms.\n" +
        "• After a reboot the schedule restores itself — no need to open the app."
    override val checkAgain = "Check again"
    override val allAllowed = "Everything is allowed — reminders should arrive"
    override fun remaining(items: String) = "Still to allow: $items"
    override val notifWord = "notifications"
    override val alarmsWord = "alarms"
    override val batteryWord = "battery"
    override fun diag(notif: Boolean, alarms: Boolean, battery: Boolean, dnd: Boolean): String {
        fun yn(v: Boolean) = if (v) "yes" else "no"
        return "notifications: " + yn(notif) + " · exact alarms: " + yn(alarms) +
            " · battery unrestricted: " + yn(battery) + " · DND access: " + yn(dnd)
    }
    override val visitRemindersTitle = "Doctor visit reminders"
    override val visitRemindersBody = "How far in advance to remind about a visit. " +
        "Pick several — each fires its own notification."
    override val offset2d = "2 days before"
    override val offset1d = "1 day before"
    override val offset3h = "3 hours before"
    override fun vendorHint(manufacturer: String): String = when (manufacturer.lowercase()) {
        "xiaomi", "redmi", "poco" -> "Xiaomi/MIUI: Settings → Apps → Dose Day → enable " +
            "\"Autostart\", set \"Battery saver\" to \"No restrictions\", and lock the app " +
            "in recents with the padlock."
        "huawei", "honor" -> "Huawei/Honor: Settings → Apps → Dose Day → Battery → " +
            "\"App launch\" to manual, enable all three toggles."
        "samsung" -> "Samsung (One UI) is the strictest — do all four:\n" +
            "1. Settings → Apps → Dose Day → Battery → \"Unrestricted\".\n" +
            "2. Settings → Apps → Dose Day → \"Alarms & reminders\" — enable.\n" +
            "3. Device care → Battery → background limits: the app must not be in " +
            "\"Sleeping\" or \"Deep sleeping\".\n" +
            "4. There, disable \"Put unused apps to sleep\"."
        "oppo", "realme", "oneplus" -> "OPPO/realme/OnePlus: Settings → Battery → Optimization → " +
            "\"Don't optimize\", and enable autostart in the app manager."
        "vivo" -> "vivo: i Manager → App manager → Autostart — enable; lift background " +
            "battery limits."
        else -> "Find the autostart or background activity section in your phone settings " +
            "and allow this app to run in background."
    }

    override fun timeToTake(name: String) = "Time to take: $name"
    override fun reminderN(n: Int) = "Reminder $n"
    override val testTitle = "Connectivity check"
    override val testFsTitle = "Full-screen check"
    override val testBody = "If you can see this — notifications arrive."
    override val channelDefaultName = "Pill reminders"
    override val channelDefaultDesc = "Notification at the moment a pill is due"
    override val channelAlarmName = "Reminders with alarm sound"
    override val channelAlarmDesc = "Same, but the sound plays like an alarm — " +
        "audible even in silent mode"
    override val channelVisitsName = "Doctor visits"
    override val timeToTakeFallback = "Time to take a pill"
    override val fsClose = "Close — it will remind again"
    override val visitNotifTitle = "Doctor appointment"

    override val widgetTitle = "Dose Day"
    override val widgetEmpty = "All taken"
    override val widgetNoPlan = "Tap \"I woke up\" in the app"

    override fun pills(amount: Double, form: String): String {
        val n = if (amount % 1.0 == 0.0) amount.toInt().toString() else amount.toString()
        val one = amount == 1.0
        val unit = when (form) {
            "Таблетка" -> if (one) "pill" else "pills"
            "Инъекция" -> if (one) "injection" else "injections"
            "Раствор" -> if (one) "dose" else "doses"
            "Капли" -> if (one) "drop" else "drops"
            "Ингалятор" -> if (one) "puff" else "puffs"
            "Порошок" -> if (one) "sachet" else "sachets"
            "Свечи" -> if (one) "suppository" else "suppositories"
            else -> "pcs"
        }
        return "$n $unit"
    }

    override fun countdown(deltaMs: Long): String {
        val totalMinutes = deltaMs / 60_000
        if (totalMinutes in -1..0) return "now"
        val late = totalMinutes < 0
        val minutes = abs(totalMinutes)
        val h = minutes / 60
        val m = minutes % 60
        val body = when {
            h > 0 && m > 0 -> "$h h $m min"
            h > 0 -> "$h h"
            else -> "$m min"
        }
        return if (late) "$body late" else "in $body"
    }

    override fun duration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "$h h $m min"
            h > 0 -> "$h h"
            else -> "$m min"
        }
    }

    override fun schedule(timesPerDay: Int, intervalMinutes: Int, everyNDays: Int): String {
        val times = if (timesPerDay == 1) "once" else "$timesPerDay times"
        val period = if (everyNDays <= 1) "a day" else "every $everyNDays days"
        val gap = if (timesPerDay > 1) ", every " + duration(intervalMinutes) else ""
        return "$times $period$gap"
    }

    override val todayWord = "Today"
    override val yesterdayWord = "Yesterday"

    override fun notesCount(n: Int) = if (n == 1) "1 note" else "$n notes"
    override val confirmDeleteTitle = "Delete?"
    override val stockLabel = "Pills left"
    override fun visitsCount(n: Int) = if (n == 1) "1 doctor" else "$n doctors"
    override fun libraryCount(n: Int) = if (n == 1) "1 entry" else "$n entries"
    override val stockHintEmpty = "Empty — don't track."
    override val reportBtn = "Report"
    override val overlayTitle = "Display over other apps"
    override val overlayBody = "Android opens the alarm screen by itself only while the screen is locked. " +
        "With this permission the app opens it on an unlocked phone as well."
    override val overlayAllow = "Allow display over apps"
    override val overlayOk = "Permission granted"
    override val fsLockHint = "Lock the screen after tapping — on an unlocked phone Android shows a regular notification."
    override val stockSupport = "Each intake decreases the stock. The \"running out\" " +
        "threshold is in settings."
    override fun stockLeft(n: String) = "$n left"
    override val lowStockTitle = "Pills running out"
    override fun lowStockBody(name: String, left: String) = "$name: $left left"
    override val privacyTitle = "Hide pill names"
    override val privacyBody = "Notifications will just say \"time to take a pill\", " +
        "no name or comment — in case someone else sees the screen."
    override val miscCard = "Privacy & stock"
    override val miscCardSub = "Name hiding, \"running out\" threshold"
    override val thresholdLabel = "Remind to buy when the stock drops below this number"
    override val wakeRemindCard = "\"I woke up\" reminder"
    override val wakeRemindBody = "If the day isn't started by the chosen time, " +
        "a notification reminds you to press the button."
    override val wakeRemindTime = "Reminder time"
    override val wakeRemindNotifTitle = "Awake?"
    override val wakeRemindNotifBody = "Press \"I woke up\" to build today's pill schedule."
    override val libraryTab = "Catalog"
    override val libraryEmptyTitle = "Catalog is empty"
    override val libraryEmptyBody = "Keep past medications here: when you took them, what they " +
        "affected, how they felt. When adding a pill you can pick the name from here."
    override val libraryFab = "Medication"
    override val newLibEntry = "New medication"
    override val editLibEntry = "Edit entry"
    override val libNameLabel = "Name"
    override val libStartLabel = "Started taking"
    override val libEndLabel = "Stopped taking"
    override val libEndHint = "not set — still taking"
    override val libEffectLabel = "What it affects"
    override val libFeelingLabel = "How it felt, side effects"
    override val fromLibraryHint = "From catalog:"
    override val fsPermWarn = "Grant the full-screen notification permission first"

    override val tabTrackers = "Trackers"
    override val trackerWeight = "Weight"
    override val trackerMood = "Mood"
    override val trackerSleep = "Sleep"
    override val trackerFab = "Tracker"
    override val trackersEmptyTitle = "No trackers yet"
    override val trackersEmptyBody = "Weight, mood, sleep — add a tracker and the app will " +
        "ask for the data at the right time."
    override val newTracker = "New tracker"
    override val editTracker = "Tracker settings"
    override val trackerTypeQ = "What to track?"
    override val heightLabel = "Height, cm"
    override val sexLabel = "Sex"
    override val sexM = "Male"
    override val sexF = "Female"
    override val askEveryLabel = "Ask every N days"
    override val askAtLabel = "Ask at"
    override val remindSwitch = "Remind with a notification"
    override val allTrackersExist = "All three trackers already exist — configure them instead."
    override val addValue = "Record"
    override val windowLabel = "Entries on the chart"
    override val customWindowLabel = "Custom"
    override val minLabel = "min"
    override val maxLabel = "max"
    override val avgLabel = "avg"
    override val logTitle = "Entry log"
    override val bmiTitle = "BMI"
    override fun bmiValue(v: String) = "BMI: $v"
    override val bmiTable = "BMI table"
    override val bmiUnder = "Underweight"
    override val bmiNormal = "Normal"
    override val bmiPre = "Overweight"
    override val bmiOb1 = "Obesity I"
    override val bmiOb2 = "Obesity II"
    override val bmiOb3 = "Obesity III"
    override val bmiNeedHeight = "Set your height in tracker settings to compute BMI."
    override val weightLabel = "Weight, kg"
    override val moodLabel = "Mood"
    override val sleepQualityLabel = "How was the sleep?"
    override val sleepWentLabel = "Went to bed"
    override val sleepWokeLabel = "Woke up"
    override fun sleptFor(d: String) = "Slept $d"
    override val awakeningsLabel = "Woke up during the night, times"
    override val sleepTagsLabel = "Tags"
    override val sleepTagPresets = listOf(
        "earphones", "mask", "earlier than usual", "later than usual", "coffee", "alcohol",
    )
    override val customTagLabel = "Custom tag"
    override val noteLabel = "Note"
    override fun trackerDoneToday(time: String) = "Recorded today at $time"
    override val trackerNotToday = "No data today"
    override fun lastEntryAgo(days: Long) = "last entry " + when {
        days <= 1L -> "yesterday"
        else -> "$days days ago"
    }
    override val neverRecorded = "no entries yet"
    override fun trackerNotifTitle(name: String) = "Time to record: $name"
    override val trackerNotifBody = "Open the app and enter the data."
    override val miniPointsLabel = "Points on the tracker mini chart"
    override val notEnoughData = "Not enough data — at least two entries needed."

    override fun snoozeAction(n: Int) = "Snooze $n min"
    override val snoozeSettingTitle = "Notification \"Snooze\", minutes"
    override fun snoozed(n: Int) = "Snoozed for $n minutes"
    override val quietTitle = "Quiet hours"
    override val quietBody = "Reminder repeats don't fire during this time — they wait until " +
        "quiet hours end. The first reminder always arrives."
    override val quietFrom = "From"
    override val quietTo = "Until"
    override fun takeAllBtn(n: Int) = "Take everything due ($n)"
    override fun snackTaken(name: String) = "\"$name\" — taken"
    override fun snackSkipped(name: String) = "\"$name\" — skipped"

    override val backupCard = "Export & backup"
    override val backupCardSub = "Save data to a file or restore from one"
    override val backupTitle = "Export & backup"
    override val exportJson = "Full backup (JSON)"
    override val exportJsonBody = "Everything: medications, intake history, notes, visits, " +
        "catalog, trackers. Fully restorable from this file."
    override val exportCsvDoses = "Intake history (CSV)"
    override val exportCsvTrackers = "Tracker entries (CSV)"
    override val exportBtn = "Save file"
    override val importJson = "Restore from backup"
    override val importBody = "Replaces ALL current data with the file contents. Irreversible."
    override val importDone = "Data restored"
    override val importError = "Couldn't read the file"
    override val exportDone = "File saved"

    override val reportCard = "Doctor report"
    override val reportCardSub = "Period summary: intakes, weight, sleep, mood, notes"
    override val reportTitle = "Doctor report"
    override val periodLabel = "Period"
    override fun periodDays(n: Int) = "$n days"
    override val reportShare = "Share"
    override val reportSave = "Save file"
    override val repAdherence = "Intake adherence"
    override val repPlanned = "planned"
    override val repTaken = "taken"
    override val repSkipped = "skipped"
    override val repMeds = "Medications"
    override val repNotes = "Notes"
    override val repVisits = "Doctor visits"
    override val repNoData = "no data"

    override val corrTitle = "Correlations"
    override val corrButton = "Correlations"
    override val corrSeries1 = "Series 1"
    override val corrSeries2 = "Series 2"
    override val seriesSleepHours = "Sleep, hours"
    override val seriesAdherence = "Adherence, %"
    override fun corrCoef(r: String) = "Correlation r = $r"
    override val corrNotEnough = "Too few overlapping days — need at least 3."

    override val photoPick = "Pick a photo"
    override val photoRemove = "Remove photo"

    override val libraryCardSub = "Medications you've taken: period, effect, side effects, photo"
    override val openLibrary = "Open catalog"
    override val privacyCard = "Privacy"
    override val stockCard = "Pill stock"
    override val chartsCard = "Charts"
    override val chartsCardSub = "Tracker mini charts"
    override val reportSavePdf = "Save PDF"
    override val repIntakes = "Intakes"
    override val repTrackers = "Trackers"
    override val reportSections = "What to include"
    override fun visitOffsetLabel(minutes: Int): String = when {
        minutes % 1440 == 0 -> {
            val d = minutes / 1440
            if (d == 7) "1 week before" else "$d day" + (if (d > 1) "s" else "") + " before"
        }
        minutes % 60 == 0 -> {
            val h = minutes / 60
            "$h hour" + (if (h > 1) "s" else "") + " before"
        }
        else -> "$minutes min before"
    }
    override val customOffsetHours = "Custom: hours before"
    override val addBtn = "Add"
    override val restoreConfirmTitle = "Restore from backup?"
    override val restoreConfirmBody = "All current pills, history, notes, visits, catalog and " +
        "trackers will be deleted and replaced by the file contents. This can't be undone."
    override val shiftConfirmTitle = "Shift the day?"
    override val shiftConfirmBody = "All not-yet-taken intakes for today will be recalculated " +
        "from now, as if you just woke up. Already taken ones stay."
    override val askTimesLabel = "When to ask (several times a day possible)"
    override val addTime = "Add time"
    override val createTracker = "Create"
    override val weightTemplateBody = "Scheduled weigh-ins, chart, BMI with color scale."
    override val moodTemplateBody = "Mood rating with emojis, a note per entry."
    override val sleepTemplateBody = "Sleep rating, bed/wake times, awakenings, tags."
    override val windowAll = "all"
    override val fsOpenNow = "Open the alarm screen"
    override val tutorialCard = "Tutorial"
    override val tutorialCardSub = "Show the intro again"
    override val tutorialStart = "Start"
    override val tutorialSkip = "Skip"
    override val tutorialSlides = listOf(
        "It all starts with \"I woke up\"" to
            "The schedule isn't tied to the clock. Press the button in the morning — every " +
            "intake counts from that moment. The next intake counts from the actual " +
            "\"Taken\", not the plan.",
        "Pills" to
            "Add medications with a step-by-step wizard: form, comment, amount, frequency, " +
            "course, gap. Link one pill to another — \"2 hours after the first\". Long-press " +
            "a card to delete, drag the ⠿ handle to reorder.",
        "Notifications" to
            "A reminder repeats until you tap \"Taken\" or \"Skip\". There's snooze, quiet " +
            "hours, alarm sound and full-screen mode. Go through the \"Notification " +
            "delivery\" checklist — otherwise the system may mute them.",
        "Notes, doctors, catalog" to
            "Dated notes about how you feel, doctor visits with advance reminders, a " +
            "medication catalog — what you took, how it went, package photo.",
        "Trackers" to
            "Weight with BMI, mood, sleep — the app asks by itself at set times. Charts, " +
            "stats, correlations between series.",
        "History & report" to
            "Day journal, adherence heatmap, doctor report for a period (text or PDF), " +
            "full data backup to a file.",
        "Ways to plan an intake" to
            "From wake-up — intakes count from the button and from the actual \"Took it\". " +
            "By clock — they sit at fixed times and don't wait for the button. " +
            "After another pill — a linked one starts from the first one's intake. " +
            "As needed — no schedule, marked by hand. " +
            "Plus \"every N days\" and a course of N days that ends by itself.",
        "What else is inside" to
            "A doctor's report and a full data backup to a file, a home-screen widget, " +
            "repeating reminders and \"Snooze\", quiet hours, a full-screen alarm, " +
            "a mode without names in the shade, package stock tracking and two languages.",
        "Day, sleep and meals by buttons" to
            "The day does not end at midnight: it runs from \"I woke up\" and lives up to 18 hours. " +
            "When every intake is marked, the app offers to start a new day, and a long press on the " +
            "top card resets the day by hand. The \"Going to bed\" and \"Ate\" buttons fill the sleep " +
            "tracker and push intakes that must not follow a meal too closely.",
    )

    override val tipsButton = "Tips"
    override val tipsTitle = "Tips"
    override val tips = listOf(
        "Photograph the package" to
            "The same active ingredient from different manufacturers is not the same medicine. " +
            "Generics differ in excipients, substance purity and manufacturing quality, so " +
            "tolerance and effect can differ. If a product works for you — buy exactly that one: " +
            "a package photo in the catalog helps not to mix it up at the pharmacy.",
        "Don't change the dose yourself" to
            "The \"dosage\" field in the app is for reference. Any dose change or discontinuation " +
            "— only with your doctor. The period report from settings is handy for appointments.",
        "Take on schedule, not \"when remembered\"" to
            "The \"I woke up\" button builds the day so gaps between intakes are even. Missed " +
            "one — don't double the next dose, mark it \"Skip\".",
        "Watch your stock" to
            "Enter how many pills are left — the app reminds you to buy in advance; the " +
            "threshold is configurable.",
        "Grant permissions once" to
            "Android loves putting apps to sleep. Go through the \"Notification delivery\" " +
            "checklist — otherwise a reminder may be late or never arrive.",
    )
    override val photoWhyHint = "Why a photo: generics with the same substance may feel " +
        "different — excipients and quality vary. Found a product that works — photograph " +
        "the package to buy exactly it."
    override val appearanceCard = "Appearance"
    override val appearanceCardSub = "Home screen, mini charts"
    override val homeModeLabel = "Pill cards on Home"
    override val homeFull = "Full"
    override val homeCompact = "Compact"

    override val doseValueLabel = "Dosage"
    override val doseUnits = listOf("mg", "mcg", "g", "ml", "IU", "%", "drops", "pcs")
    override val otherUnit = "other"
    override val customUnitLabel = "Custom unit"
    override val amountSection = "Pills per intake"
    override val doseSection = "Dosage (for reference)"
    override val stockSection = "Stock in the package"
    override val earlyTitle = "Too early"
    override fun earlyBody(time: String, left: String) = "This intake is planned for $time (in $left). Mark it as taken now? The next intake will count from this moment."
    override val earlyConfirm = "Took it anyway"
    override val scheduleSection = "When to remind"
    override val scheduleBody = "From wake-up — intakes count from the \"I woke up\" button and from the actual \"Took it\". " +
        "By clock — intakes sit at the chosen times and don't wait for the button."
    override val modeWake = "From wake-up"
    override val modeClock = "By clock"
    override val clockTimesTitle = "Intake times"
    override val clockTimesHint = "Tap a time to change it."
    override val clockNoOffset = "A by-clock schedule ignores the wake-up offset: intakes stay at the chosen times."
    override val byClockShort = "by clock"
    override val visitCustomAdd = "Add your own lead time"
    override val visitCustomTitle = "How long before the visit"
    override val daysField = "Days"
    override val hoursField = "Hours"
    override val snoozeOptionsTitle = "\"Snooze\" buttons"
    override val snoozeOptionsBody = "The chosen options appear as buttons on the full-screen reminder. The first one is the button in the regular notification."
    override val askTimesHint = "Tap a time to change it; the cross removes it."
    override val later = "Later"
    override val dayDoneTitle = "Everything is marked"
    override val dayDoneBody = "That's all for today. When you wake up, tap \"I woke up\" and the day starts over."
    override val dayDoneHome = "All intakes for today are done."
    override val newDayBtn = "Start a new day"
    override val resetDayTitle = "Start the day over?"
    override val resetDayBody = "Pending intakes will be re-planned from now. Already marked ones stay in history. " +
        "Useful when your day cycle turned out short and it's time to take pills again."
    override val resetDayConfirm = "Start over"
    override val bedtimeBtn = "Sleep"
    override fun bedtimeSaved(time: String) = "Saved: went to bed at $time"
    override val mealBtn = "Ate"
    override fun mealSaved(time: String) = "Saved: meal at $time"
    override val sleepWakeQuality = "How you woke up"
    override val sleepRateBtn = "Rate"
    override val sleepAutoBadge = "by buttons"
    override val sleepAdviceTitle = "Best time to sleep"
    override fun sleepAdvice(bed: String, wake: String, duration: String) =
        "Based on the nights you rated above average: go to bed around $bed, get up around $wake, sleep about $duration."
    override fun sleepAdviceNeedMore(n: Int) =
        "More data is needed for a meaningful suggestion: at least $n more night(s) with sleep times and a rating."
    override val sleepRateTitle = "How did you sleep?"
    override val askSleepTitle = "Ask about sleep on wake-up"
    override val askSleepBody = "If you tapped \"Going to bed\", the \"I woke up\" button creates a sleep entry and asks you to rate it."
    override val mealSection = "Relation to food"
    override val mealSectionBody = "The \"Ate\" button on the home screen pushes such intakes forward if you ate later."
    override val mealNo = "Doesn't matter"
    override fun mealAfter(duration: String) = "$duration after a meal"
    override val apartSection = "Keep apart from other pills"
    override val apartSectionBody = "The intake moves later if another pill was taken close to it."
    override val apartNo = "Doesn't matter"
    override fun apartFor(duration: String) = "no closer than $duration"
    override val corrInfoTitle = "How to read the chart and pairs"
    override val corrInfoBody = "On the chart every series is normalized to its own range: compare the shape of the lines, not their height.\n\n" +
        "The numbers below the chart are Pearson's correlation coefficient (r) for a pair of series, over days where both values exist.\n\n" +
        "• r near +1 — they grow together;\n" +
        "• r near −1 — one grows while the other falls;\n" +
        "• near 0 — no visible link.\n\n" +
        "Roughly: |r| up to 0.3 is weak, 0.3–0.7 moderate, above 0.7 strong. \"—\" means fewer than three shared days.\n\n" +
        "Correlation does not prove cause: a match can come from a third factor or from chance. " +
        "Treat it as a hint for observation and for a talk with your doctor, not as a conclusion."
    override fun durationLabelShort(days: Int) = if (days == 1) "1-day course" else "$days-day course"
    override val formSection = "Dosage form"
    override val commentSection = "Intake note"
    override val freqDurationQ = "How often and how long?"
    override val freqDurationBody = "Times per day, the gap between them and the length of the course."
    override val conditionsQ = "When and with what to take it"
    override val conditionsBody = "Start of the day, relation to food and spacing from other pills."
    override val summaryQ = "Check before saving"
    override val summaryBody = "This is how the pill will look on the home screen."
    override val firstDoseSection = "First intake"
    override val apartPickLabel = "Keep apart from"
    override val apartAny = "Any pill"
    override val mealAfterSection = "After a meal"
    override val mealBeforeSection = "Before a meal"
    override val mealNone = "Doesn't matter"
    override val mealCustom = "Custom…"
    override val mealCustomTitle = "Custom time"
    override fun mealAfterShort(duration: String) = "$duration after a meal"
    override fun mealBeforeShort(duration: String) = "$duration before a meal"
    override val trackerRemindSection = "Reminders"
    override val trackerTimesSection = "When to ask"
    override val trackerPresetsLabel = "Quick add"
    override val trackerSelectedLabel = "Selected"
    override val trackerAddOwnTime = "Custom time"
    override val trackerBodySection = "Body parameters"
    override val trackerNoTimes = "No times selected — there will be no reminders."
    override val chartPointsLabel = "Points"
    override val reportPeriodSection = "Period"
    override val reportSectionsTitle = "What to include"
    override val reportExportTitle = "Save and share"
    override val reportPreviewTitle = "Preview"
    override fun fsCloseIn(duration: String) = "Close — will remind in $duration"
    override val fsCloseNoRepeat = "Close — repeats are off, there will be no reminder"
    override val changelogTitle = "What's new"
    override val versionUnreleased = "in development"
    override val bedtimeDone = "Sleep saved"

    override fun bedtimeShort(time: String) = "Sleep from $time"
    override val newDayConfirmTitle = "Start a new day?"
    override val newDayConfirmBody = "The current day closes and intakes are planned again from this moment. " +
        "Already marked intakes stay in history. Do this only if a new cycle really started — " +
        "otherwise the pills will shift."
    override val recommendBtn = "Guidance"
    override val recommendTitle = "How to choose the gap"
    override val recommendBody = "This is a hint, not a prescription: 2 intakes — about 12 hours, 3 — 8 hours, " +
        "4 — 5 hours, more — spread evenly over waking hours.\n\n" +
        "If your doctor prescribed a different schedule — follow the doctor, not the app."
    override val fromMeal = "After a meal"
    override val chartStyleTitle = "Chart style"
    override val chartStyleBody = "A smooth line is easier to read, a sharp one shows every single measurement."
    override val chartSmooth = "Smooth"
    override val chartSharp = "Sharp"
    override val createRemaining = "Create the rest"
    override val corrReportSection = "Links between metrics"
    override val overlayMissing = "Permission not granted — the alarm screen won't open on an unlocked phone"
    override val heightSubsection = "Height"
    override val sexSubsection = "Sex"
    override val summaryHint = "Check everything. To change something, use \"Back\"."
    override val visitsCalendarHint = "A dot under the date means there is a visit that day"
    override fun mealsAt(times: String) = "meals: $times"

    override val periodDaysLabel = "How many days"
    override val periodCustomBtn = "Custom period…"
    override fun periodCustomSet(period: String) = "Custom period: $period — change"
    override val dndWord = "Do not disturb"
    override val mealImmediately = "Right away"
    override val sleepAdviceNeedData = "More sleep data is needed for a meaningful suggestion."
    override val wakeRatingLine = "Wake-up"
    override val sleepShortTitle = "Very little sleep"
    override fun sleepShortBody(duration: String) = "Only $duration passed since \"Going to bed\". " +
        "An adult needs 7–9 hours: short sleep hits attention, mood and blood pressure. " +
        "Start a new day anyway?"
    override val sleepShortConfirm = "Woke up anyway"
    override val importFromHistoryTitle = "Import data from history?"
    override fun importFromHistoryBody(n: Int) = "History has $n night(s) with \"Going to bed\" and \"I woke up\" marks. " +
        "They can be moved into the tracker right away — you can rate them later."
    override val importBtn = "Import"
    override val heightFieldLabel = "Centimetres"
    override val journalFilters = "Show"
    override val filterDoses = "Intakes"
    override val filterMeals = "Meals"
    override val filterSleep = "Sleep"
    override val eventWokeUp = "Woke up"
    override val eventBed = "Went to bed"
    override val eventMeal = "Ate"
    override val pickWidgetTitle = "Which widget to add?"
    override val widgetNarrowName = "Compact"
    override val widgetWideName = "With a \"Took it\" button"
    override val apartConflictHint = "The linked pill is excluded: its gap is set by the \"how long after it\" field."

    override val homeActionsTitle = "Buttons on the home screen"
    override val homeActionsBody = "The row \"Tips · Tutorial · Report · Going to bed · Ate\" under the wake-up card."
    override val hideActions = "Hide the buttons"
    override val quickSaveBtn = "Save now"
    override val quickSaveTitle = "Save it like this?"
    override val quickSaveBody = "You can adjust the rest later. The home screen card will look like this:"
    override val quickSaveMore = "Set up in detail"
    override val detailsQ = "Form and note"
    override val detailsBody = "Optional step: dosage form and a personal comment like \"15 minutes before a meal\"."
    override val templatesSection = "Ready-made schedules"
    override val tplMorningEvening = "Morning and evening"
    override val tplAfterMeals = "3 times after meals"
    override val tplCourse10 = "10-day course"
    override val photoPromptTitle = "Add a package photo?"
    override val photoPromptBody = "Generics from different makers differ, and a photo helps not to mix them up at the pharmacy. " +
        "You can also do it later: \"Records\" → \"Catalog\" → the medication card."
    override val photoPromptAdd = "Add a photo"
    override val duplicateBtn = "Duplicate"
    override val copySuffix = "copy"
    override val quickMoodTitle = "How do you feel?"
    override val quickWeightTitle = "Weight"
    override val savedShort = "Saved"
    override val createAllTrackers = "Create all three"
    override fun groupNotifTitle(n: Int) = if (n == 1) "1 pill" else "$n pills"
    override val takeAllAction = "Took all"
    override val widgetTake = "Took it"
    override fun stockUntil(date: String) = "lasts until $date"
    override val streakTitle = "No misses"
    override fun streakDays(n: Int) = if (n == 1) "1 day" else "$n days"
    override val adherenceTitle = "Adherence per pill"
    override val searchHint = "Search"
    override val noteTagsLabel = "Tags, comma separated"
    override val noteMedLabel = "About which pill"
    override val noteMedNone = "General"
    override val lockTitle = "Unlock with fingerprint or PIN"
    override val lockBody = "Ask for a fingerprint or the device PIN every time the app opens."
    override val lockPrompt = "Unlock to open the app"
    override val lockUnlock = "Unlock"
    override val lockUnavailable = "No fingerprint or screen lock is set up on this device"

    override val corrPairsTitle = "Pairs"

    override fun snoozeFor(minutes: String) = "Snooze for $minutes"

    override fun versionLabel(v: String) = "Dose Day · version $v"
    override val toToday = "Go to today"
    override val intervalAutoHint = "Filled in automatically: 2 intakes — 12 h, 3 — 8 h, 4 — 5 h, more — evenly over 16 waking hours. Adjust if your doctor prescribed otherwise."
    override val intervalSection = "Gap between intakes"
    override val groupToday = "Today"
    override val groupYesterday = "Yesterday"
    override val groupWeek = "Last 7 days"
    override val groupMonth = "Last 30 days"
    override val groupOlder = "Older"
    override val tutorialBtn = "Tutorial"
    override val thresholdShort = "Threshold, pcs"
    override val customOffsetTime = "Custom time before the visit"
    override val windowOther = "other…"
    override val sleepTimesSwitch = "Set bed and wake times"
    override val corrPick = "What to draw on the chart"
    override val recordsTab = "Records"
    override val noEntriesYet = "No entries yet — tap \"Record\"."
    override val repSchedule = "schedule"
}
