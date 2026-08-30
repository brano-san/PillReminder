@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package tech.unispace.pillreminder.ui

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

// ---------- Вкладка «Заметки / Врачи / Каталог» ----------

@Composable
fun NotesScreen(
    notes: List<Note>,
    visits: List<DoctorVisit>,
    library: List<MedLibraryEntry>,
    onOpenNote: (Long) -> Unit,
    onAddNote: () -> Unit,
    onEditVisit: (Long) -> Unit,
    onAddVisit: () -> Unit,
    onEditLibrary: (Long) -> Unit,
    onAddLibrary: () -> Unit,
    onDeleteNote: (Long) -> Unit,
    onDeleteVisit: (Long) -> Unit,
    onDeleteLibrary: (Long) -> Unit,
    contentPadding: PaddingValues,
) {
    val s = Lang.s
    var tab by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
            // Шапка: название вкладки и количество записей серым.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when (tab) {
                        0 -> s.notesTab2
                        1 -> s.visitsTab2
                        else -> s.libraryTab
                    },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when (tab) {
                        0 -> s.notesCount(notes.size)
                        1 -> visits.size.toString()
                        else -> library.size.toString()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(s.notesTab2) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(s.visitsTab2) })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(s.libraryTab) })
            }
            when (tab) {
                0 -> NotesList(notes, onOpenNote, onDeleteNote, contentPadding)
                1 -> VisitsList(visits, onEditVisit, onDeleteVisit, contentPadding)
                else -> LibraryList(library, onEditLibrary, onDeleteLibrary, contentPadding)
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                when (tab) {
                    0 -> onAddNote()
                    1 -> onAddVisit()
                    else -> onAddLibrary()
                }
            },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = {
                Text(
                    when (tab) {
                        0 -> s.noteFab
                        1 -> s.visitFab
                        else -> s.libraryFab
                    },
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        )
    }
}

@Composable
private fun EmptyTabHint(title: String, body: String) {
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
    var deleteTarget by remember { mutableStateOf<Note?>(null) }
    deleteTarget?.let { note ->
        ConfirmDeleteDialog(
            title = note.title,
            onConfirm = { onDelete(note.id) },
            onDismiss = { deleteTarget = null },
        )
    }

    if (notes.isEmpty()) {
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
        items(notes, key = { it.id }) { note ->
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

@Composable
private fun VisitsList(
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

    if (visits.isEmpty()) {
        EmptyTabHint(s.visitsEmptyTitle, s.visitsEmptyBody)
        return
    }
    val now = System.currentTimeMillis()
    val upcoming = visits.filter { it.atMillis >= now }.sortedBy { it.atMillis }
    val past = visits.filter { it.atMillis < now }.sortedByDescending { it.atMillis }

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
private fun LibraryList(
    entries: List<MedLibraryEntry>,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    contentPadding: PaddingValues,
) {
    val s = Lang.s
    var deleteTarget by remember { mutableStateOf<MedLibraryEntry?>(null) }
    deleteTarget?.let { entry ->
        ConfirmDeleteDialog(
            title = entry.name,
            onConfirm = { onDelete(entry.id) },
            onDismiss = { deleteTarget = null },
        )
    }

    if (entries.isEmpty()) {
        EmptyTabHint(s.libraryEmptyTitle, s.libraryEmptyBody)
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
        items(entries, key = { it.id }) { entry ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onEdit(entry.id) },
                        onLongClick = { deleteTarget = entry },
                    ),
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        entry.photoUri?.let { uri ->
                            UriImage(
                                uri = uri,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(entry.name, fontWeight = FontWeight.SemiBold)
                    }
                    if (entry.period.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            entry.period,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (entry.effect.isNotBlank() || entry.feeling.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            listOf(entry.effect, entry.feeling)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
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
    LaunchedEffect(noteId) { note = vm.loadNote(noteId) }

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
                    TextButton(onClick = onEdit) { Text(s.edit) }
                    TextButton(onClick = { vm.deleteNote(noteId) { onDone() } }) { Text(s.delete) }
                },
            )
        },
    ) { padding ->
        val n = note ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
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
            Spacer(Modifier.height(4.dp))
            Text(n.body, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------- Мастер заметки ----------

private const val NOTE_PAGES = 3

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
    var page by remember { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(noteId) {
        if (!isNew) {
            vm.loadNote(noteId)?.let { n ->
                title = n.title
                description = n.description
                body = n.body
                val dt = n.atMillis.toLocalDateTime()
                date = dt.toLocalDate()
                time = dt.toLocalTime()
            }
            loaded = true
        }
    }

    fun save() {
        vm.saveNote(
            Note(
                id = noteId,
                title = title.trim().ifBlank { if (Lang.code == "en") "Untitled" else "Без названия" },
                description = description.trim(),
                body = body.trim(),
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
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (isNew) s.newNote else s.editNote)
                            Text(
                                s.stepOf(page + 1, NOTE_PAGES),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (page == 0) onDone() else page-- }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                        }
                    },
                    actions = {
                        IconButton(onClick = onDone) {
                            Icon(Icons.Default.Close, contentDescription = s.closeNoSave)
                        }
                    },
                )
                LinearProgressIndicator(
                    progress = { (page + 1f) / NOTE_PAGES },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        bottomBar = {
            // imePadding поднимает кнопки над клавиатурой.
            Row(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (page > 0) {
                    OutlinedButton(onClick = { page-- }, modifier = Modifier.height(48.dp)) {
                        Text(s.back)
                    }
                }
                Button(
                    onClick = { if (page == NOTE_PAGES - 1) save() else page++ },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text(if (page == NOTE_PAGES - 1) s.save else s.next)
                }
            }
        },
    ) { padding ->
        if (!loaded) return@Scaffold

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            when (page) {
                0 -> {
                    Text(s.noteNameQ, style = MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(s.nameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        s.noteTimeSection,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.weight(1.4f).height(52.dp),
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(date.format(noteDateFmt))
                        }
                        OutlinedButton(
                            onClick = { showTimePicker = true },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(time.format(noteTimeFmt))
                        }
                    }
                    Text(
                        s.noteTimeHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                1 -> {
                    Text(s.noteDescQ, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        s.noteDescBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(s.noteDescLabel) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                2 -> {
                    Text(s.noteBodyQ, style = MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text(s.noteBodyLabel) },
                        minLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
    onDone: () -> Unit,
) {
    val s = Lang.s
    val isNew = visitId == 0L
    var loaded by remember { mutableStateOf(isNew) }
    var title by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    var time by remember { mutableStateOf(LocalTime.of(12, 0)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(visitId) {
        if (!isNew) {
            vm.loadVisit(visitId)?.let { v ->
                title = v.title
                comment = v.comment
                val dt = v.atMillis.toLocalDateTime()
                date = dt.toLocalDate()
                time = dt.toLocalTime()
            }
            loaded = true
        }
    }

    fun save() {
        vm.saveVisit(
            DoctorVisit(
                id = visitId,
                title = title.trim().ifBlank { s.visitNotifTitle },
                comment = comment.trim(),
                atMillis = LocalDateTime.of(date, time)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            ),
        ) { onDone() }
    }

    if (showDatePicker) {
        DateWheelDialog(initial = date, onPick = { date = it }, onDismiss = { showDatePicker = false })
    }
    if (showTimePicker) {
        TimeWheelDialog(initial = time, onPick = { time = it }, onDismiss = { showTimePicker = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) s.newVisit else s.editVisit) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                },
                actions = {
                    if (!isNew) {
                        TextButton(onClick = { vm.deleteVisit(visitId) { onDone() } }) {
                            Text(s.delete)
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
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(s.visitTitleLabel) },
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
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text(s.visitCommentLabel) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------- Запись каталога ----------

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
    var period by remember { mutableStateOf("") }
    var effect by remember { mutableStateOf("") }
    var feeling by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(entryId) {
        if (!isNew) {
            vm.loadLibraryEntry(entryId)?.let { e ->
                name = e.name
                period = e.period
                effect = e.effect
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
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                    }
                },
                actions = {
                    if (!isNew) {
                        TextButton(onClick = { vm.deleteLibraryEntry(entryId) { onDone() } }) {
                            Text(s.delete)
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
                                period = period.trim(),
                                effect = effect.trim(),
                                feeling = feeling.trim(),
                                photoUri = photoUri,
                            ),
                        ) { onDone() }
                    },
                    enabled = name.isNotBlank(),
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
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(s.libNameLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = period,
                onValueChange = { period = it },
                label = { Text(s.libPeriodLabel) },
                placeholder = { Text(s.libPeriodPlaceholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = effect,
                onValueChange = { effect = it },
                label = { Text(s.libEffectLabel) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = feeling,
                onValueChange = { feeling = it },
                label = { Text(s.libFeelingLabel) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            photoUri?.let { uri ->
                UriImage(
                    uri = uri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { photoLauncher.launch(arrayOf("image/*")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(s.photoPick)
                }
                if (photoUri != null) {
                    OutlinedButton(
                        onClick = { photoUri = null },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(s.photoRemove)
                    }
                }
            }
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

/** Превью изображения по SAF-URI; тихо ничего не рисует, если файл недоступен. */
@Composable
fun UriImage(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uri) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(uri) {
        bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
                    android.graphics.BitmapFactory.decodeStream(it)
                }?.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let {
        androidx.compose.foundation.Image(
            bitmap = it,
            contentDescription = null,
            modifier = modifier,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
    }
}
