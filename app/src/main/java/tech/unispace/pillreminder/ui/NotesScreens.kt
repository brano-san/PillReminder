@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package tech.unispace.pillreminder.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import tech.unispace.pillreminder.data.epochDayOf
import tech.unispace.pillreminder.data.today
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Switch
import tech.unispace.pillreminder.alarm.visitReminderMoments
import tech.unispace.pillreminder.data.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import tech.unispace.pillreminder.data.Medication
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tech.unispace.pillreminder.data.DoctorVisit
import tech.unispace.pillreminder.data.MedLibraryEntry
import tech.unispace.pillreminder.data.Note
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val noteDateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)
private val noteTimeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

private fun Long.toLocalDateTime(): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

fun formatNoteTime(millis: Long): String =
    millis.toLocalDateTime().format(
        DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Lang.s.locale),
    )

/** Диалог подтверждения удаления по долгому нажатию. */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Lang.s.confirmDeleteTitle) },
        text = { Text(title) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) {
                Text(Lang.s.delete, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Lang.s.cancel) }
        },
    )
}

// ---------- Вкладка «Записи»: заметки · врачи · каталог ----------

@Composable
fun RecordsScreen(
    notes: List<Note>,
    visits: List<DoctorVisit>,
    library: List<MedLibraryEntry>,
    onOpenNote: (Long) -> Unit,
    onAddNote: () -> Unit,
    /** Второй аргумент — «как вернуть»: экран показывает снекбар и зовёт его при нажатии «Вернуть». */
    onDeleteNote: (Long, ((() -> Unit)) -> Unit) -> Unit,
    onEditVisit: (Long) -> Unit,
    onAddVisit: () -> Unit,
    onDeleteVisit: (Long, ((() -> Unit)) -> Unit) -> Unit,
    onEditLibrary: (Long) -> Unit,
    onAddLibrary: () -> Unit,
    onDeleteLibrary: (Long, ((() -> Unit)) -> Unit) -> Unit,
    contentPadding: PaddingValues,
) {
    val s = Lang.s
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val titles = listOf(s.notesTab2, s.visitsTab2, s.libraryTab)
    val snackbars = remember { SnackbarHostState() }
    // Удаление записи — обратимо: снекбар «Вернуть» висит, пока пользователь не ушёл с экрана.
    val undoable: (String, () -> Unit) -> Unit = { message, undo ->
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            if (snackbars.showSnackbar(message, actionLabel = s.undo) == SnackbarResult.ActionPerformed) undo()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(titles[pager.currentPage], style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    when (pager.currentPage) {
                        0 -> s.notesCount(notes.size)
                        1 -> s.visitsCount(visits.size)
                        else -> s.libraryCount(library.size)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TabRow(selectedTabIndex = pager.currentPage) {
                titles.forEachIndexed { i, t ->
                    Tab(
                        selected = pager.currentPage == i,
                        onClick = { scope.launch { pager.animateScrollToPage(i) } },
                        text = { Text(t, maxLines = 1, softWrap = false) },
                    )
                }
            }
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> NotesList(notes, onOpenNote, { id -> onDeleteNote(id) { undoable(s.noteDeleted) { it() } } }, contentPadding)
                    1 -> VisitsList(visits, onEditVisit, { id -> onDeleteVisit(id) { undoable(s.visitDeleted) { it() } } }, contentPadding)
                    else -> LibraryList(library, onEditLibrary, { id -> onDeleteLibrary(id) { undoable(s.entryDeleted) { it() } } }, contentPadding)
                }
            }
        }
        SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).padding(bottom = contentPadding.calculateBottomPadding() + 80.dp))
        ExtendedFloatingActionButton(
            onClick = {
                when (pager.currentPage) {
                    0 -> onAddNote()
                    1 -> onAddVisit()
                    else -> onAddLibrary()
                }
            },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(listOf(s.noteFab, s.visitFab, s.libraryFab)[pager.currentPage]) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        )
    }
}

/** Заголовок группы по давности записи. */
fun ageGroup(atMillis: Long, s: S): String {
    val days = today() - epochDayOf(atMillis)
    return when {
        days <= 0 -> s.groupToday
        days == 1L -> s.groupYesterday
        days <= 7 -> s.groupWeek
        days <= 30 -> s.groupMonth
        else -> s.groupOlder
    }
}
/**
 * Уход с формы с несохранёнными правками — через вопрос: мастер таблетки так делает, а заметка,
 * визит и запись каталога теряли набранное от одного касания стрелки «назад».
 */
@Composable
fun DiscardChangesDialog(onDiscard: () -> Unit, onDismiss: () -> Unit) {
    val s = Lang.s
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.discardTitle) },
        text = { Text(s.discardBody) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(s.closeNoSave) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}

/** Строка поиска над списком: одинаковая для заметок и каталога. */
@Composable
fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(Lang.s.searchHint) },
        singleLine = true,
        colors = fieldColors(),
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, contentDescription = Lang.s.cancel) }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Фильтр заметок по заголовку, описанию, тексту и тегам. */
fun filterNotes(notes: List<Note>, query: String): List<Note> {
    val words = normalizeSearch(query).split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return notes
    return notes.filter { n ->
        val hay = normalizeSearch(listOf(n.title, n.description, n.body, n.tags).joinToString(" "))
        words.all { it in hay }
    }
}

fun normalizeSearch(text: String): String =
    text.lowercase().replace('ё', 'е').replace(Regex("\\s+"), " ").trim()

/** Каталог лекарств — отдельный экран (открывается из настроек и из мастера таблетки). */
@Composable
fun LibraryScreen(
    library: List<MedLibraryEntry>,
    onEdit: (Long) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val s = Lang.s
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.libraryTab + "  ·  " + library.size) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(s.libraryFab) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LibraryList(library, onEdit, onDelete, PaddingValues(0.dp))
        }
    }
}

@Composable
fun EmptyTabHint(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NotesList(
    notes: List<Note>,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    contentPadding: PaddingValues,
) {
    // Поиск по заголовку, тексту и тегам: листать три десятка заметок неудобно.
    var query by rememberSaveable { mutableStateOf("") }
    val notes = remember(notes, query) { filterNotes(notes, query) }
    var deleteTarget by remember { mutableStateOf<Note?>(null) }
    deleteTarget?.let { note ->
        ConfirmDeleteDialog(
            title = note.title,
            onConfirm = { onDelete(note.id) },
            onDismiss = { deleteTarget = null },
        )
    }

    if (notes.isEmpty() && query.isBlank()) {
        EmptyTabHint(Lang.s.notesEmptyTitle, Lang.s.notesEmptyBody)
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "search") { SearchField(query) { query = it } }
        // Пустой результат поиска — не пустой экран: иначе он читается как «ещё грузится».
        if (notes.isEmpty()) {
            item(key = "nothing") {
                Text(
                    Lang.s.searchNothingFound,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        val grouped = notes.groupBy { ageGroup(it.atMillis, Lang.s) }
        grouped.forEach { (group, list) ->
            item(key = "g-" + group) {
                Text(
                    group,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
            }
            items(list, key = { it.id }) { note ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onOpen(note.id) },
                        onLongClick = { deleteTarget = note },
                    ),
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text(
                        note.title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        formatNoteTime(note.atMillis),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (note.tags.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(note.tags, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (note.description.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            note.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
fun VisitsList(
    visits: List<DoctorVisit>,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    contentPadding: PaddingValues,
) {
    val s = Lang.s
    var deleteTarget by remember { mutableStateOf<DoctorVisit?>(null) }
    deleteTarget?.let { visit ->
        ConfirmDeleteDialog(
            title = visit.title,
            onConfirm = { onDelete(visit.id) },
            onDismiss = { deleteTarget = null },
        )
    }

    // Мини-календарь: видно, в какие дни есть визиты, и можно отфильтровать список одним тапом.
    // Пока месяц не листали руками, открываем его на ближайшем визите.
    var month by remember { mutableStateOf<LocalDate?>(null) }
    var pickedDay by remember { mutableStateOf<Long?>(null) }
    val visitDays = remember(visits) { visits.map { epochDayOf(it.atMillis) }.toSet() }
    val shownMonth = month ?: LocalDate.ofEpochDay(visitStartMonth(visitDays, LocalDate.now().toEpochDay()))

    if (visits.isEmpty()) {
        EmptyTabHint(s.visitsEmptyTitle, s.visitsEmptyBody)
        return
    }
    val now = System.currentTimeMillis()
    // Визит выбранного дня удалили или перенесли — фильтр снимаем сам, иначе список пуст без причины.
    LaunchedEffect(visits) {
        val picked = pickedDay
        if (picked != null && visits.none { epochDayOf(it.atMillis) == picked }) pickedDay = null
    }
    val shown = pickedDay?.let { day -> visits.filter { epochDayOf(it.atMillis) == day } } ?: visits
    val upcoming = shown.filter { it.atMillis >= now }.sortedBy { it.atMillis }
    val past = shown.filter { it.atMillis < now }.sortedByDescending { it.atMillis }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "calendar") {
            VisitsCalendar(
                month = shownMonth,
                visitDays = visitDays,
                picked = pickedDay,
                onMonth = { month = shownMonth.plusMonths(it) },
                onPick = { day -> pickedDay = if (pickedDay == day) null else day },
            )
        }
        if (upcoming.isEmpty() && past.isEmpty()) {
            item(key = "empty-day") {
                Text(
                    s.noEntriesYet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                )
            }
        }
        if (upcoming.isNotEmpty()) {
            item(key = "up") { SectionLabel(s.upcomingVisits) }
            items(upcoming, key = { it.id }) { visit ->
                VisitCard(visit, past = false, onEdit = onEdit, onLong = { deleteTarget = it })
            }
        }
        if (past.isNotEmpty()) {
            item(key = "past") { SectionLabel(s.pastVisits) }
            items(past, key = { it.id }) { visit ->
                VisitCard(visit, past = true, onEdit = onEdit, onLong = { deleteTarget = it })
            }
        }
    }
}

/** Компактный месяц над списком визитов: точка под числом — в этот день есть визит. */
/**
 * Месяц, на котором открывается календарь визитов: ближайший будущий визит, а если таких нет —
 * последний прошедший. Открываться всегда на текущем месяце неудобно: запись к врачу обычно
 * на другой месяц, и календарь встречал пустой сеткой.
 */
fun visitStartMonth(visitDays: Collection<Long>, todayDay: Long): Long {
    val day = visitDays.filter { it >= todayDay }.minOrNull() ?: visitDays.maxOrNull() ?: todayDay
    return LocalDate.ofEpochDay(day).withDayOfMonth(1).toEpochDay()
}

@Composable
private fun VisitsCalendar(
    month: LocalDate,
    visitDays: Set<Long>,
    picked: Long?,
    onMonth: (Long) -> Unit,
    onPick: (Long) -> Unit,
) {
    val s = Lang.s
    val daysInMonth = month.lengthOfMonth()
    val firstCellOffset = month.dayOfWeek.value - 1
    val today = LocalDate.now().toEpochDay()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonth(-1) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = s.weekPrev)
                }
                Text(
                    s.monthNames[month.monthValue - 1] + " " + month.year,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onMonth(1) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = s.weekNext)
                }
            }
            val rows = ((firstCellOffset + daysInMonth + 6) / 7)
            for (row in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val index = row * 7 + col - firstCellOffset
                        if (index < 0 || index >= daysInMonth) {
                            Spacer(Modifier.weight(1f))
                            continue
                        }
                        val date = month.plusDays(index.toLong())
                        val day = date.toEpochDay()
                        val hasVisit = day in visitDays
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(1.dp)
                                .background(
                                    if (picked == day) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    RoundedCornerShape(6.dp),
                                )
                                // Выбранный день кликабелен всегда — иначе после удаления визита фильтр не снять.
                                .clickable(enabled = hasVisit || picked == day) { onPick(day) }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                                color = if (hasVisit) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Box(
                                Modifier
                                    .padding(top = 2.dp)
                                    .size(4.dp)
                                    .background(
                                        if (hasVisit) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        RoundedCornerShape(2.dp),
                                    ),
                            )
                        }
                    }
                }
            }
            Text(s.visitsCalendarHint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun VisitCard(
    visit: DoctorVisit,
    past: Boolean,
    onEdit: (Long) -> Unit,
    onLong: (DoctorVisit) -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onEdit(visit.id) },
                onLongClick = { onLong(visit) },
            ),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                visit.title,
                fontWeight = FontWeight.SemiBold,
                color = if (past) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                formatNoteTime(visit.atMillis),
                style = MaterialTheme.typography.labelMedium,
                color = if (past) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            if (visit.place.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(visit.place, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (visit.comment.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    visit.comment,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun LibraryList(
    entries: List<MedLibraryEntry>,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    contentPadding: PaddingValues,
) {
    val s = Lang.s
    var query by rememberSaveable { mutableStateOf("") }
    val entries = remember(entries, query) {
        // Тот же поиск, что по заметкам: все слова запроса, без учёта регистра и «ё/е».
        val words = normalizeSearch(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) {
            entries
        } else {
            entries.filter { e ->
                val hay = normalizeSearch(listOf(e.name, e.effect, e.feeling).joinToString(" "))
                words.all { it in hay }
            }
        }
    }
    var deleteTarget by remember { mutableStateOf<MedLibraryEntry?>(null) }
    deleteTarget?.let { entry ->
        ConfirmDeleteDialog(title = entry.name, onConfirm = { onDelete(entry.id) }, onDismiss = { deleteTarget = null })
    }
    if (entries.isEmpty() && query.isBlank()) {
        EmptyTabHint(s.libraryEmptyTitle, s.libraryEmptyBody)
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "search") { SearchField(query) { query = it } }
        if (entries.isEmpty()) {
            item(key = "nothing") {
                Text(
                    s.searchNothingFound,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        items(entries, key = { it.id }) { entry ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { onEdit(entry.id) }, onLongClick = { deleteTarget = entry }),
            ) {
                Column {
                    // Фото — во всю ширину карточки, чтобы упаковку было видно без открытия.
                    entry.photoUri?.let { uri ->
                        UriImage(uri = uri, modifier = Modifier.fillMaxWidth().height(180.dp), contentDescription = Lang.s.photoDesc)
                    }
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        MarqueeText(entry.name, fontWeight = FontWeight.SemiBold)
                        val period = libraryPeriodText(entry, s)
                        if (period.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(period, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        if (entry.effect.isNotBlank() || entry.feeling.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                listOf(entry.effect, entry.feeling).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** «12.03.2026 — 20.05.2026» или «12.03.2026 — не указан». */
fun libraryPeriodText(entry: MedLibraryEntry, s: S): String {
    val start = entry.startEpochDay?.let { LocalDate.ofEpochDay(it).format(noteDateFmt) } ?: return ""
    val end = entry.endEpochDay?.let { LocalDate.ofEpochDay(it).format(noteDateFmt) }
    return if (end != null) "$start — $end" else "$start — " + s.libEndHint
}

// ---------- Просмотр заметки ----------

@Composable
fun NoteViewScreen(
    vm: MainViewModel,
    noteId: Long,
    onEdit: () -> Unit,
    onDone: () -> Unit,
) {
    val s = Lang.s
    var note by remember { mutableStateOf<Note?>(null) }
    // Привязка к таблетке — иначе поле «О какой таблетке» было бы только на запись, без чтения.
    var medName by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(noteId) {
        note = vm.loadNote(noteId)
        medName = note?.medId?.let { vm.medName(it) }
        loaded = true
    }
    // Заметку могли удалить из списка: пустой экран с рабочими кнопками читается как поломка.
    if (loaded && note == null) {
        LaunchedEffect(Unit) { onDone() }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = note?.title.orEmpty(),
            onConfirm = { vm.deleteNote(noteId) { onDone() } },
            onDismiss = { confirmDelete = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(note?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                },
                actions = {
                    TextButton(onClick = onEdit) { Text(s.edit, maxLines = 1, softWrap = false) }
                    TextButton(onClick = { confirmDelete = true }) { Text(s.delete, maxLines = 1, softWrap = false) }
                },
            )
        },
    ) { padding ->
        val n = note ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                formatNoteTime(n.atMillis),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (n.description.isNotBlank()) {
                Text(
                    n.description,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (medName != null || n.tags.isNotBlank()) {
                Text(
                    listOfNotNull(medName?.let { s.noteAboutMed(it) }, n.tags.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(n.body, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------- Заметка: один экран ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditNoteScreen(
    vm: MainViewModel,
    noteId: Long,
    onDone: () -> Unit,
) {
    val s = Lang.s
    val isNew = noteId == 0L
    var loaded by remember { mutableStateOf(isNew) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }
    var tags by remember { mutableStateOf("") }
    // Заметку можно привязать к таблетке: «от этой тошнит».
    var medId by remember { mutableStateOf<Long?>(null) }
    var meds by remember { mutableStateOf<List<Medication>>(emptyList()) }
    // Описание, теги и таблетка нужны не каждой заметке — спрятаны под «Ещё», пока их не заполняли.
    var more by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(noteId) {
        if (!isNew) {
            vm.loadNote(noteId)?.let { n ->
                title = n.title
                description = n.description
                body = n.body
                tags = n.tags
                medId = n.medId
                more = n.description.isNotBlank() || n.tags.isNotBlank() || n.medId != null
                val dt = n.atMillis.toLocalDateTime()
                date = dt.toLocalDate()
                time = dt.toLocalTime()
            }
            loaded = true
        }
        meds = vm.activeMeds()
    }

    val canSave = title.isNotBlank() || body.isNotBlank()
    // Что было при открытии: с этим сравниваем, чтобы не спрашивать зря.
    var snapshot by remember { mutableStateOf<String?>(null) }
    val current = listOf(title, description, body, tags, medId?.toString().orEmpty(), date.toString(), time.toString()).joinToString("|")
    LaunchedEffect(loaded) { if (loaded && snapshot == null) snapshot = current }
    val dirty = snapshot != null && snapshot != current
    var confirmDiscard by remember { mutableStateOf(false) }
    fun requestClose() { if (dirty) confirmDiscard = true else onDone() }
    if (confirmDiscard) {
        DiscardChangesDialog(onDiscard = { confirmDiscard = false; onDone() }, onDismiss = { confirmDiscard = false })
    }
    BackHandler { requestClose() }

    fun save() {
        vm.saveNote(
            Note(
                id = noteId,
                // Без заголовка — первая строка текста: список заметок не должен пестрить «Без названия».
                title = title.trim().ifBlank { body.trim().lineSequence().firstOrNull()?.take(60)?.trim().orEmpty() }.ifBlank { s.untitledNote },
                description = description.trim(),
                body = body.trim(),
                tags = tags.split(',').map { it.trim() }.filter { it.isNotBlank() }.joinToString(", "),
                medId = medId,
                atMillis = LocalDateTime.of(date, time)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            ),
        ) { onDone() }
    }

    if (showDatePicker) {
        DateWheelDialog(
            initial = date,
            onPick = { date = it },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showTimePicker) {
        TimeWheelDialog(
            initial = time,
            onPick = { time = it },
            onDismiss = { showTimePicker = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) s.newNote else s.editNote) },
                navigationIcon = {
                    IconButton(onClick = { requestClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                },
                actions = {
                    // Галочка в шапке: короткая мысль записывается в два касания.
                    IconButton(onClick = { save() }, enabled = canSave) {
                        Icon(Icons.Default.Check, contentDescription = s.save)
                    }
                },
            )
        },
        bottomBar = {
            // imePadding поднимает кнопку над клавиатурой.
            Row(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = { save() },
                    enabled = canSave,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text(s.save, maxLines = 1, softWrap = false)
                }
            }
        },
    ) { padding ->
        if (!loaded) return@Scaffold

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                colors = fieldColors(),
                value = title,
                onValueChange = { title = it },
                label = { Text(s.nameLabel) },
                placeholder = { Text(s.noteTitlePlaceholder) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                colors = fieldColors(),
                value = body,
                onValueChange = { body = it },
                label = { Text(s.noteBodyLabel) },
                placeholder = { Text(s.noteBodyPlaceholder) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1.4f).height(52.dp),
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(date.format(noteDateFmt))
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(time.format(noteTimeFmt))
                }
            }
            TextButton(onClick = { more = !more }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Icon(if (more) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.noteMoreBtn, maxLines = 1, softWrap = false)
            }
            if (more) {
                OutlinedTextField(
                    colors = fieldColors(),
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(s.noteDescLabel) },
                    supportingText = { Text(s.noteDescBody) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(s.noteTagsLabel) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (meds.isNotEmpty()) {
                    Text(s.noteMedLabel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(selected = medId == null, onClick = { medId = null }, label = { Text(s.noteMedNone, maxLines = 1, softWrap = false) })
                        meds.forEach { med ->
                            FilterChip(
                                selected = medId == med.id,
                                onClick = { medId = med.id },
                                label = { MarqueeText(med.name) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------- Визит к врачу ----------

@Composable
fun EditVisitScreen(
    vm: MainViewModel,
    visitId: Long,
    /** «Изменить сроки»: за сколько напоминать — общая настройка, живёт в «Настройках → Визиты». */
    onOpenReminderSettings: () -> Unit,
    onDone: () -> Unit,
) {
    val s = Lang.s
    val context = LocalContext.current
    val isNew = visitId == 0L
    // rememberSaveable, а не remember: «Изменить сроки» уводит на экран настроек, и по возврату
    // форма теряла всё набранное. Дата и время — примитивами: Bundle кладёт их без отдельного Saver.
    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var title by rememberSaveable { mutableStateOf("") }
    var place by rememberSaveable { mutableStateOf("") }
    var comment by rememberSaveable { mutableStateOf("") }
    var remind by rememberSaveable { mutableStateOf(true) }
    var dateDay by rememberSaveable { mutableLongStateOf(LocalDate.now().plusDays(1).toEpochDay()) }
    var timeMinutes by rememberSaveable { mutableIntStateOf(12 * 60) }
    val date: LocalDate = LocalDate.ofEpochDay(dateDay)
    val time: LocalTime = LocalTime.of(timeMinutes / 60, timeMinutes % 60)
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    // Без remember: вернувшись с «Изменить сроки», человек должен увидеть новые смещения.
    val offsets = Settings(context).visitOffsetsMinutes

    LaunchedEffect(visitId) {
        if (!isNew) {
            vm.loadVisit(visitId)?.let { v ->
                title = v.title
                place = v.place
                comment = v.comment
                remind = v.remind
                val dt = v.atMillis.toLocalDateTime()
                dateDay = dt.toLocalDate().toEpochDay()
                timeMinutes = dt.toLocalTime().let { it.hour * 60 + it.minute }
            }
            loaded = true
        }
    }

    val atMillis = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    var snapshot by rememberSaveable { mutableStateOf<String?>(null) }
    val current = listOf(title, place, comment, remind.toString(), date.toString(), time.toString()).joinToString("|")
    LaunchedEffect(loaded) { if (loaded && snapshot == null) snapshot = current }
    val dirty = snapshot != null && snapshot != current
    var confirmDiscard by remember { mutableStateOf(false) }
    fun requestClose() { if (dirty) confirmDiscard = true else onDone() }
    if (confirmDiscard) {
        DiscardChangesDialog(onDiscard = { confirmDiscard = false; onDone() }, onDismiss = { confirmDiscard = false })
    }
    BackHandler { requestClose() }

    fun save() {
        vm.saveVisit(
            DoctorVisit(
                id = visitId,
                title = title.trim().ifBlank { s.visitNotifTitle },
                comment = comment.trim(),
                atMillis = atMillis,
                place = place.trim(),
                remind = remind,
            ),
        ) { onDone() }
    }

    if (showDatePicker) {
        DateWheelDialog(initial = date, onPick = { dateDay = it.toEpochDay() }, onDismiss = { showDatePicker = false })
    }
    if (showTimePicker) {
        TimeWheelDialog(initial = time, onPick = { timeMinutes = it.hour * 60 + it.minute }, onDismiss = { showTimePicker = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) s.newVisit else s.editVisit) },
                navigationIcon = {
                    IconButton(onClick = { requestClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                },
                actions = {
                    if (!isNew) {
                        // Удаление — только через подтверждение, как и везде в приложении.
                        var confirm by remember { mutableStateOf(false) }
                        if (confirm) {
                            ConfirmDeleteDialog(title = title, onConfirm = { vm.deleteVisit(visitId) { onDone() } }, onDismiss = { confirm = false })
                        }
                        TextButton(onClick = { confirm = true }) {
                            Text(s.delete, maxLines = 1, softWrap = false)
                        }
                    }
                },
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = { save() },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text(s.save)
                }
            }
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                colors = fieldColors(),
                value = title,
                onValueChange = { title = it },
                label = { Text(s.visitTitleLabel) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                placeholder = { Text(s.visitTitlePlaceholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1.4f).height(52.dp),
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(date.format(noteDateFmt))
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(time.format(noteTimeFmt))
                }
            }
            // Адрес и кабинет: перед выходом это нужнее всего, поэтому они и на карточке, и в уведомлении.
            OutlinedTextField(
                colors = fieldColors(),
                value = place,
                onValueChange = { place = it },
                label = { Text(s.visitPlaceLabel) },
                placeholder = { Text(s.visitPlacePlaceholder) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                colors = fieldColors(),
                value = comment,
                onValueChange = { comment = it },
                label = { Text(s.visitCommentLabel) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            // Напоминание: переключатель и честный список моментов — иначе непонятно, когда именно придёт уведомление.
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(s.visitRemindTitle, fontWeight = FontWeight.SemiBold)
                            Text(s.visitRemindBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = remind, onCheckedChange = { remind = it })
                    }
                    if (remind) {
                        val moments = visitReminderMoments(atMillis, offsets, System.currentTimeMillis())
                        Text(
                            if (moments.isEmpty()) s.visitRemindNone else s.visitRemindAt(moments.joinToString(" · ") { formatNoteTime(it) }),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (moments.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        TextButton(onClick = onOpenReminderSettings, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Text(s.visitRemindChange, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------- Запись каталога ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditLibraryScreen(
    vm: MainViewModel,
    entryId: Long,
    onDone: () -> Unit,
) {
    val s = Lang.s
    val context = LocalContext.current
    val isNew = entryId == 0L
    var loaded by remember { mutableStateOf(isNew) }
    var name by remember { mutableStateOf("") }
    var startDay by remember { mutableStateOf<Long?>(null) }
    var endDay by remember { mutableStateOf<Long?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var effect by remember { mutableStateOf("") }
    var feeling by remember { mutableStateOf("") }
    var libForm by remember { mutableStateOf("") }
    var libDose by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<String?>(null) }
    var snapshot by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val current = listOf(name, effect, feeling, libForm, libDose, photoUri.orEmpty(), startDay?.toString().orEmpty(), endDay?.toString().orEmpty()).joinToString("|")
    LaunchedEffect(loaded) { if (loaded && snapshot == null) snapshot = current }
    val dirty = snapshot != null && snapshot != current
    fun requestClose() { if (dirty) confirmDiscard = true else onDone() }
    if (confirmDiscard) {
        DiscardChangesDialog(onDiscard = { confirmDiscard = false; onDone() }, onDismiss = { confirmDiscard = false })
    }
    BackHandler { requestClose() }

    // Снимок камерой: файл заводим заранее и отдаём камере — она пишет прямо в него.
    var pendingPhoto by remember { mutableStateOf<android.net.Uri?>(null) }
    var cameraMissing by remember { mutableStateOf(false) }
    var fullscreenPhoto by remember { mutableStateOf<String?>(null) }
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
    ) { ok ->
        val target = pendingPhoto
        pendingPhoto = null
        if (ok && target != null) photoUri = target.toString()
    }
    if (cameraMissing) {
        AlertDialog(
            onDismissRequest = { cameraMissing = false },
            title = { Text(s.cameraMissing) },
            confirmButton = { TextButton(onClick = { cameraMissing = false }) { Text(s.done) } },
        )
    }
    fullscreenPhoto?.let { FullscreenPhotoDialog(it) { fullscreenPhoto = null } }

    val photoLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            // Persistable-разрешение, иначе после перезапуска фото станет недоступно.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            photoUri = uri.toString()
        }
    }

    if (showStartPicker) {
        DateWheelDialog(
            initial = startDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now(),
            // Конец не может быть раньше начала — подтягиваем, а не показываем «20.05 — 12.03».
            onPick = { picked ->
                startDay = picked.toEpochDay()
                endDay = endDay?.let { maxOf(it, picked.toEpochDay()) }
            },
            onDismiss = { showStartPicker = false },
        )
    }
    if (showEndPicker) {
        DateWheelDialog(
            initial = endDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now(),
            // Выбранную дату не подменяем: если она раньше начала, экран покажет ошибку и не даст сохранить.
            onPick = { picked -> endDay = picked.toEpochDay() },
            onDismiss = { showEndPicker = false },
        )
    }

    LaunchedEffect(entryId) {
        if (!isNew) {
            vm.loadLibraryEntry(entryId)
?.let { e ->
                name = e.name
                startDay = e.startEpochDay
                endDay = e.endEpochDay
                effect = e.effect
                libForm = e.form
                libDose = e.doseInfo
                feeling = e.feeling
                photoUri = e.photoUri
            }
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) s.newLibEntry else s.editLibEntry) },
                navigationIcon = {
                    IconButton(onClick = { requestClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                },
                actions = {
                    if (!isNew) {
                        var confirm by remember { mutableStateOf(false) }
                        if (confirm) {
                            ConfirmDeleteDialog(title = name, onConfirm = { vm.deleteLibraryEntry(entryId) { onDone() } }, onDismiss = { confirm = false })
                        }
                        TextButton(onClick = { confirm = true }) {
                            Text(s.delete, maxLines = 1, softWrap = false)
                        }
                    }
                },
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = {
                        vm.saveLibraryEntry(
                            MedLibraryEntry(
                                id = entryId,
                                name = name.trim(),
                                startEpochDay = startDay,
                                endEpochDay = endDay,
                                form = libForm.trim(),
                                doseInfo = libDose.trim(),
                                effect = effect.trim(),
                                feeling = feeling.trim(),
                                photoUri = photoUri,
                            ),
                        ) { onDone() }
                    },
                    // Даты «конец раньше начала» больше не подменяются молча — сохранить с ними нельзя.
                    enabled = name.isNotBlank() && !(startDay != null && endDay != null && endDay!! < startDay!!),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text(s.save)
                }
            }
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                colors = fieldColors(),
                value = name,
                onValueChange = { name = it },
                label = { Text(s.libNameLabel) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // Период приёма — две даты через календарь; конец необязателен.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Короткие подписи: «Начало приёма» в половину ширины экрана переносилось на две строки.
                OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        startDay?.let { LocalDate.ofEpochDay(it).format(noteDateFmt) } ?: s.libStartShort,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        endDay?.let { LocalDate.ofEpochDay(it).format(noteDateFmt) } ?: s.libEndShort,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val endBeforeStart = startDay != null && endDay != null && endDay!! < startDay!!
            Text(
                if (endBeforeStart) s.libEndBeforeStart else s.libStartLabel + " · " + s.libEndLabel + ": " + s.libEndHint,
                style = MaterialTheme.typography.bodySmall,
                color = if (endBeforeStart) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Форму и дозировку подставит мастер таблетки при выборе из каталога.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    colors = fieldColors(),
                    value = libForm,
                    onValueChange = { libForm = it },
                    label = { Text(Lang.s.formQ) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    colors = fieldColors(),
                    value = libDose,
                    onValueChange = { libDose = it },
                    label = { Text(Lang.s.doseSection) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                colors = fieldColors(),
                value = effect,
                onValueChange = { effect = it },
                label = { Text(s.libEffectLabel) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                colors = fieldColors(),
                value = feeling,
                onValueChange = { feeling = it },
                label = { Text(s.libFeelingLabel) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            photoUri?.let { uri ->
                // Тап по фото — просмотр во весь экран: в рамке 220 dp упаковку не разглядеть.
                UriImage(
                    uri = uri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clickable { fullscreenPhoto = uri },
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { photoLauncher.launch(arrayOf("image/*")) }) {
                    Text(s.photoPick, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = {
                        // Камеры может не быть вовсе — тогда честно говорим об этом, а не падаем.
                        val target = runCatching { newPhotoTarget(context) }.getOrNull()
                        if (target == null) {
                            cameraMissing = true
                        } else {
                            pendingPhoto = target
                            if (runCatching { cameraLauncher.launch(target) }.isFailure) {
                                pendingPhoto = null
                                cameraMissing = true
                            }
                        }
                    },
                ) {
                    Text(s.photoCamera, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
                if (photoUri != null) {
                    OutlinedButton(onClick = { photoUri = null }) {
                        Text(s.photoRemove, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // Пояснение «зачем фото» — под кнопкой, к которой относится, а не над ней.
            Text(s.photoWhyHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------- Диалоги выбора даты и времени ----------

@Composable
fun DateWheelDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // selectedDateMillis у DatePicker — полночь UTC, поэтому переводим через epochDay.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toEpochDay() * 86_400_000L,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(it / 86_400_000L)) }
                    onDismiss()
                },
            ) {
                Text(Lang.s.done)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Lang.s.cancel) }
        },
    ) {
        DatePicker(state = state, showModeToggle = true)
    }
}

@Composable
fun TimeWheelDialog(
    initial: LocalTime,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Lang.s.timeDialogTitle) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(
                onClick = {
                    onPick(LocalTime.of(state.hour, state.minute))
                    onDismiss()
                },
            ) {
                Text(Lang.s.done)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Lang.s.cancel) }
        },
    )
}

/** Длинная сторона превью в пикселях: фото с камеры на 12 Мп в списке каталога не нужно. */
private const val IMAGE_MAX_SIDE_PX = 1280

/** Во весь экран нужна деталь на упаковке, поэтому уменьшаем меньше. */
private const val FULLSCREEN_MAX_SIDE_PX = 2560

/** Файл для снимка упаковки: своя папка внутри приложения, наружу отдаётся content-ссылка. */
private fun newPhotoTarget(context: android.content.Context): android.net.Uri {
    val dir = java.io.File(context.filesDir, "photos").apply { mkdirs() }
    val file = java.io.File(dir, "pack-" + System.currentTimeMillis() + ".jpg")
    return androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", file)
}

/**
 * Превью изображения по SAF-URI; тихо ничего не рисует, если файл недоступен.
 * Картинка уменьшается при декодировании: несколько полноразмерных фото в списке — это OutOfMemory.
 */
@Composable
fun UriImage(
    uri: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    /** Во весь экран фото показывается целиком, а не обрезанным по рамке превью. */
    fit: Boolean = false,
    maxSidePx: Int = IMAGE_MAX_SIDE_PX,
) {
    val context = LocalContext.current
    var bitmap by remember(uri, maxSidePx) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(uri, maxSidePx) {
        bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val parsed = android.net.Uri.parse(uri)
                // Первый проход — только размеры, второй — с подобранным шагом уменьшения.
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(parsed)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSidePx) sample *= 2
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(parsed)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, opts)
                }?.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let {
        androidx.compose.foundation.Image(
            bitmap = it,
            // Без подписи незрячий оператор не узнаёт, что у записи вообще есть фото упаковки.
            contentDescription = contentDescription ?: Lang.s.photoDesc,
            modifier = modifier,
            contentScale = if (fit) {
                androidx.compose.ui.layout.ContentScale.Fit
            } else {
                androidx.compose.ui.layout.ContentScale.Crop
            },
        )
    }
}

/**
 * Фото упаковки во весь экран. В форме и в списке оно обрезано рамкой, а разобрать надо
 * мелкую надпись на коробке — для этого и нужен полный размер.
 */
@Composable
fun FullscreenPhotoDialog(uri: String, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            UriImage(
                uri = uri,
                modifier = Modifier.fillMaxWidth(),
                fit = true,
                maxSidePx = FULLSCREEN_MAX_SIDE_PX,
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Icon(Icons.Default.Close, contentDescription = Lang.s.photoClose, tint = Color.White)
            }
        }
    }
}
