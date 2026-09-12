package tech.unispace.pillreminder.ui

/**
 * История версий. Показывается по нажатию на строку версии внизу настроек —
 * специально без отдельного пункта меню, чтобы не занимать место.
 *
 * Новая версия = запись сверху. Дата пустая, пока версия не выпущена.
 */
data class ReleaseNotes(
    val version: String,
    /** Дата выпуска в формате «30.08.2026»; пусто — ещё не выпущено. */
    val date: String,
    val ru: List<String>,
    val en: List<String>,
)

val CHANGELOG: List<ReleaseNotes> = listOf(
    ReleaseNotes(
        version = "1.2",
        date = "13.09.2026",
        ru = listOf(
            "Приём «после еды» ждёт кнопку «Еда»: до еды будильник не звонит, карточка показывает «ждёт „Еда“», после — напоминает через выбранное время. Страховка — напоминание через 3 часа.",
            "Связь с едой видна на карточке, в итоге мастера, в шторке и на виджете; «сразу после еды» — словами, а не «1 мин».",
            "Схема дня в карточке пробуждения: подъём → таблетки → еда → сон с промежутками между ними; выключается в «Внешнем виде».",
            "Первый приём: пресеты «Днём (+6 ч)» и «Вечером (+12 ч)», смещение в часах и минутах.",
            "Необязательная пометка «минимум калорий» в связи с едой — видна на карточке, в шторке и на виджете.",
            "Карточка: название своей строкой, время крупнее; «Всё выпито · 1 из 1» вместо «0 из 1».",
            "Мастер: «Через промежуток / По часам» вместо двойного «От пробуждения», стрелка вместо «Назад», кнопки внизу помещаются.",
            "Виджет: дозировка в строке приёма, условия приёма под каждой таблеткой (в широком — и комментарий); цвет, прозрачность и цвет текста настраиваются.",
            "Ревью: «Отложить» и повторы не сбиваются действиями в приложении; «Сон» снимает оставшиеся приёмы дня; удаление таблетки отвязывает связанные; бэкап хранит привязку заметок и восстанавливается одной транзакцией.",
            "Ревью: отчёт и серия «без пропусков» не считают будущие приёмы пропусками; «Принять все» и «Пропустить все» для группы; подтверждения удаления везде; приёмы прошлых дней отмечаются из журнала; ошибочную «Еду» можно убрать долгим нажатием.",
        ),
        en = listOf(
            "An \"after a meal\" intake waits for the \"Ate\" button: no alarm before the meal, the card says \"waits for Ate\", then it reminds after the chosen delay. Safety net: a reminder 3 hours later.",
            "Food relation is shown on the card, in the wizard summary, in the shade and on the widget; \"right after a meal\" in words, not \"1 min\".",
            "Day timeline in the wake-up card: wake-up → pills → meals → bed with the gaps between them; can be turned off in \"Appearance\".",
            "First intake: \"Afternoon (+6 h)\" and \"Evening (+12 h)\" presets, offset in hours and minutes.",
            "Optional \"minimum meal calories\" note in the food relation — shown on the card, in the shade and on the widget.",
            "Card: the name on its own line, larger time; \"All done · 1 of 1\" instead of \"0 of 1\".",
            "Wizard: \"By interval / By clock\" instead of a doubled \"From wake-up\", an arrow instead of \"Back\", bottom buttons fit.",
            "Widget: dosage in the intake line, intake conditions under each pill (the wide one also shows the comment); background color, transparency and text color are adjustable.",
            "Review: snooze and repeats survive any action in the app; \"Sleep\" removes the day's remaining intakes; deleting a pill unlinks the linked ones; backup keeps note-to-pill links and restores in one transaction.",
            "Review: the report and the streak no longer count future intakes as missed; \"Take all\" and \"Skip all\" for a group; delete confirmations everywhere; past days' intakes can be marked from the journal; a mistaken \"Ate\" can be removed with a long press.",
        ),
    ),
    ReleaseNotes(
        version = "1.1",
        date = "03.09.2026",
        ru = listOf(
            "Расписание «по часам»: приёмы больше не ждут кнопку «Я проснулся».",
            "Кнопки «Сон» и «Еда»: сон сам попадает в трекер, а еда двигает приёмы, которые нельзя пить сразу после неё.",
            "Связь с едой у таблетки: «через N после еды» и «за N до еды», разнос с конкретными таблетками.",
            "Уведомления о приёмах в одну минуту объединяются, есть кнопка «Принять все».",
            "Полноэкранное напоминание: несколько кнопок «Отложить», выделенная кнопка «Пропустить».",
            "Быстрое сохранение таблетки после ввода названия и дублирование схемы.",
            "Трекер сна: отдельные оценки сна и пробуждения, совет по времени отбоя, автозаписи по кнопкам.",
            "Запись веса и настроения в один тап с главной.",
            "Поиск и теги в заметках, привязка заметки к таблетке, поиск по каталогу.",
            "Серия дней без пропусков и дисциплина по каждой таблетке.",
            "Прогноз «на сколько хватит упаковки» и два виджета: компактный и с кнопкой «Выпито».",
            "Вход по отпечатку или коду устройства.",
            "Пересчёт расписания при смене часового пояса.",
            "День длится максимум 18 часов и закрывается кнопкой «Сон».",
            "Еда и сон видны в истории дня; тепловая карта различает пропуски оранжевым.",
            "Сглаженные графики с фиксированной шкалой и настройкой вида.",
            "Мини-календарь визитов, связи между показателями в отчёте врача.",
            "Журнал дня — единая лента событий с фильтрами.",
            "Предупреждение при слишком коротком сне и перенос ночей из истории в трекер.",
            "Правила приоритета: сдвиги считаются от исходного времени и ограничены.",
            "Формулировки без рода: «Подъём», «Выпито», «Приём пищи».",
        ),
        en = listOf(
            "Clock-based schedule: intakes no longer wait for the \"I woke up\" button.",
            "\"Sleep\" and \"Ate\" buttons: sleep lands in the tracker by itself, meals push intakes that must not follow food.",
            "Food relation per pill: \"N after a meal\" and \"N before a meal\", plus keeping apart from chosen pills.",
            "Intakes due in the same minute are merged into one notification with \"Take all\".",
            "Full-screen reminder: several snooze buttons, a highlighted \"Skip\".",
            "Quick save of a pill right after the name and schedule duplication.",
            "Sleep tracker: separate sleep and wake-up ratings, bedtime advice, entries collected by buttons.",
            "One-tap weight and mood entries from the home screen.",
            "Search and tags in notes, linking a note to a pill, catalog search.",
            "Streak of days without misses and adherence per pill.",
            "\"Package lasts until\" forecast and two widgets: compact and with a \"Taken\" button.",
            "Unlock with fingerprint or device PIN.",
            "Schedule recalculation after a time zone change.",
            "The day lasts at most 18 hours and closes with the \"Sleep\" button.",
            "Meals and sleep show up in the day history; the heatmap marks misses in orange.",
            "Smooth charts with a fixed scale and a style setting.",
            "A mini calendar of visits and metric links in the doctor report.",
            "The day journal is a single timeline of events with filters.",
            "A warning about too little sleep and import of nights from history into the tracker.",
            "Priority rules: shifts are counted from the original time and are capped.",
            "Gender-neutral wording throughout the interface.",
        ),
    ),
    ReleaseNotes(
        version = "1.0",
        date = "30.08.2026",
        ru = listOf(
            "Первая версия: расписание от кнопки «Я проснулся», следующий приём — от фактического «Выпил».",
            "Мастер добавления таблетки: форма, дозировка, количество, курс, промежуток, связанные таблетки, приём по необходимости.",
            "Напоминания с повторами, «Отложить», тихими часами, звуком будильника и полноэкранным режимом.",
            "Заметки, визиты к врачу с напоминаниями, каталог лекарств с фото.",
            "Трекеры веса с ИМТ, настроения и сна: графики, статистика, корреляции.",
            "История: журнал по дням, тепловая карта, отчёт для врача (TXT и PDF), бэкап в файл.",
            "Виджет на рабочий стол, русский и английский язык, светлая и тёмная темы.",
        ),
        en = listOf(
            "First release: the schedule starts from the \"I woke up\" button, the next intake counts from the actual \"Taken\".",
            "Pill wizard: form, dosage, amount, course, gap, linked pills, as-needed intake.",
            "Reminders with repeats, snooze, quiet hours, alarm sound and full-screen mode.",
            "Notes, doctor visits with reminders, medication catalog with photos.",
            "Weight (with BMI), mood and sleep trackers: charts, stats, correlations.",
            "History: day journal, heatmap, doctor's report (TXT and PDF), backup to a file.",
            "Home-screen widget, Russian and English, light and dark themes.",
        ),
    ),
)
