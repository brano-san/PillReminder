package tech.unispace.pillreminder

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import tech.unispace.pillreminder.ui.BackupScreen
import tech.unispace.pillreminder.ui.CorrelationScreen
import tech.unispace.pillreminder.ui.DeliverySettingsScreen
import tech.unispace.pillreminder.ui.EditLibraryScreen
import tech.unispace.pillreminder.ui.EditMedScreen
import tech.unispace.pillreminder.ui.EditNoteScreen
import tech.unispace.pillreminder.ui.EditVisitScreen
import tech.unispace.pillreminder.ui.HomeScreen
import tech.unispace.pillreminder.ui.Lang
import tech.unispace.pillreminder.ui.MainViewModel
import tech.unispace.pillreminder.ui.MiscSettingsScreen
import tech.unispace.pillreminder.ui.NoteViewScreen
import tech.unispace.pillreminder.ui.NotesScreen
import tech.unispace.pillreminder.ui.RepeatSettingsScreen
import tech.unispace.pillreminder.ui.ReportScreen
import tech.unispace.pillreminder.ui.SettingsMenuScreen
import tech.unispace.pillreminder.ui.SoundSettingsScreen
import tech.unispace.pillreminder.ui.StatsScreen
import tech.unispace.pillreminder.ui.TrackerDetailScreen
import tech.unispace.pillreminder.ui.TrackersScreen
import tech.unispace.pillreminder.ui.EditTrackerScreen
import tech.unispace.pillreminder.ui.VisitReminderSettingsScreen
import tech.unispace.pillreminder.ui.theme.PillTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            PillTheme {
                AppRoot()
            }
        }
    }
}

private const val ROUTE_HOME = "home"
private const val ROUTE_NOTES = "notes"
private const val ROUTE_STATS = "stats"
private const val ROUTE_SETUP = "setup"
private const val ROUTE_EDIT = "edit/{medId}"
private const val ROUTE_NOTE_VIEW = "note/{noteId}"
private const val ROUTE_NOTE_EDIT = "noteEdit/{noteId}"
private const val ROUTE_VISIT_EDIT = "visitEdit/{visitId}"
private const val ROUTE_LIB_EDIT = "libEdit/{entryId}"
private const val ROUTE_SETUP_REPEATS = "setup/repeats"
private const val ROUTE_SETUP_SOUND = "setup/sound"
private const val ROUTE_SETUP_DELIVERY = "setup/delivery"
private const val ROUTE_SETUP_VISITS = "setup/visits"
private const val ROUTE_SETUP_MISC = "setup/misc"
private const val ROUTE_TRACKERS = "trackers"
private const val ROUTE_TRACKER_DETAIL = "tracker/{trackerId}"
private const val ROUTE_TRACKER_EDIT = "trackerEdit/{trackerId}"
private const val ROUTE_SETUP_BACKUP = "setup/backup"
private const val ROUTE_SETUP_REPORT = "setup/report"
private const val ROUTE_CORRELATIONS = "correlations"

@Composable
private fun AppRoot() {
    val vm: MainViewModel = viewModel()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route in setOf(ROUTE_HOME, ROUTE_NOTES, ROUTE_STATS, ROUTE_TRACKERS, ROUTE_SETUP)
    val s = Lang.s

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = route == ROUTE_HOME,
                        onClick = { navigateTab(nav, ROUTE_HOME) },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text(s.tabPills) },
                    )
                    NavigationBarItem(
                        selected = route == ROUTE_NOTES,
                        onClick = { navigateTab(nav, ROUTE_NOTES) },
                        icon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
                        label = { Text(s.tabNotes) },
                    )
                    NavigationBarItem(
                        selected = route == ROUTE_STATS,
                        onClick = { navigateTab(nav, ROUTE_STATS) },
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        label = { Text(s.tabHistory) },
                    )
                    NavigationBarItem(
                        selected = route == ROUTE_TRACKERS,
                        onClick = { navigateTab(nav, ROUTE_TRACKERS) },
                        icon = { Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null) },
                        label = { Text(s.tabTrackers) },
                    )
                    NavigationBarItem(
                        selected = route == ROUTE_SETUP,
                        onClick = { navigateTab(nav, ROUTE_SETUP) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(s.tabSettings) },
                    )
                }
            }
        },
    ) { padding ->
        val tabPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding(),
        )
        NavHost(
            navController = nav,
            startDestination = ROUTE_HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(ROUTE_HOME) {
                val state by vm.home.collectAsState()
                val trackerRows by vm.trackerRows.collectAsState()
                HomeScreen(
                    state = state,
                    trackerRows = trackerRows,
                    contentPadding = tabPadding,
                    onWakeUp = { vm.wakeUp() },
                    onTake = { vm.take(it) },
                    onTakeNow = { vm.takeNow(it) },
                    onSkip = { vm.skip(it) },
                    onEdit = { nav.navigate("edit/" + it) },
                    onAdd = { nav.navigate("edit/0") },
                    onDelete = { vm.delete(it) },
                    onUndo = { vm.undo(it) },
                    onTakeAll = { vm.takeAllDue() },
                    onReorder = { vm.saveMedOrder(it) },
                    onOpenSettings = { nav.navigate(ROUTE_SETUP_DELIVERY) },
                    onOpenTracker = { nav.navigate("tracker/" + it) },
                )
            }
            composable(ROUTE_TRACKERS) {
                val trackerRows by vm.trackerRows.collectAsState()
                TrackersScreen(
                    rows = trackerRows,
                    onOpen = { nav.navigate("tracker/" + it) },
                    onAdd = { nav.navigate("trackerEdit/0") },
                    onOpenCorrelations = { nav.navigate(ROUTE_CORRELATIONS) },
                    contentPadding = tabPadding,
                )
            }
            composable(
                ROUTE_TRACKER_DETAIL,
                arguments = listOf(navArgument("trackerId") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("trackerId") ?: 0L
                val trackerRows by vm.trackerRows.collectAsState()
                TrackerDetailScreen(
                    vm = vm,
                    trackerId = id,
                    rows = trackerRows,
                    onEditTracker = { nav.navigate("trackerEdit/" + id) },
                    onDone = { nav.popBackStack() },
                )
            }
            composable(
                ROUTE_TRACKER_EDIT,
                arguments = listOf(navArgument("trackerId") { type = NavType.LongType }),
            ) { entry ->
                val trackerRows by vm.trackerRows.collectAsState()
                EditTrackerScreen(
                    vm = vm,
                    trackerId = entry.arguments?.getLong("trackerId") ?: 0L,
                    existingRows = trackerRows,
                    onDone = { nav.popBackStack() },
                )
            }
            composable(ROUTE_NOTES) {
                val notes by vm.notes.collectAsState()
                val visits by vm.visits.collectAsState()
                val library by vm.library.collectAsState()
                NotesScreen(
                    notes = notes,
                    visits = visits,
                    library = library,
                    onOpenNote = { nav.navigate("note/" + it) },
                    onAddNote = { nav.navigate("noteEdit/0") },
                    onEditVisit = { nav.navigate("visitEdit/" + it) },
                    onAddVisit = { nav.navigate("visitEdit/0") },
                    onEditLibrary = { nav.navigate("libEdit/" + it) },
                    onAddLibrary = { nav.navigate("libEdit/0") },
                    onDeleteNote = { vm.deleteNote(it) },
                    onDeleteVisit = { vm.deleteVisit(it) },
                    onDeleteLibrary = { vm.deleteLibraryEntry(it) },
                    contentPadding = tabPadding,
                )
            }
            composable(ROUTE_STATS) {
                val journal by vm.journal.collectAsState()
                val heatmap by vm.heatmap.collectAsState()
                StatsScreen(
                    journal = journal,
                    heatmap = heatmap,
                    onSelectDay = { vm.selectedDay.value = it },
                    onMonthShift = { delta ->
                        vm.heatMonthStart.value = vm.heatMonthStart.value.plusMonths(delta)
                    },
                    onUndo = { vm.undo(it) },
                    contentPadding = tabPadding,
                )
            }
            composable(ROUTE_SETUP) {
                SettingsMenuScreen(
                    contentPadding = tabPadding,
                    onOpenRepeats = { nav.navigate(ROUTE_SETUP_REPEATS) },
                    onOpenSound = { nav.navigate(ROUTE_SETUP_SOUND) },
                    onOpenDelivery = { nav.navigate(ROUTE_SETUP_DELIVERY) },
                    onOpenVisits = { nav.navigate(ROUTE_SETUP_VISITS) },
                    onOpenMisc = { nav.navigate(ROUTE_SETUP_MISC) },
                    onOpenBackup = { nav.navigate(ROUTE_SETUP_BACKUP) },
                    onOpenReport = { nav.navigate(ROUTE_SETUP_REPORT) },
                    onLanguageChanged = { },
                )
            }
            composable(ROUTE_SETUP_REPEATS) {
                RepeatSettingsScreen(onBack = { nav.popBackStack() }, vm = vm)
            }
            composable(ROUTE_SETUP_SOUND) {
                SoundSettingsScreen(onBack = { nav.popBackStack() })
            }
            composable(ROUTE_SETUP_DELIVERY) {
                DeliverySettingsScreen(onBack = { nav.popBackStack() })
            }
            composable(ROUTE_SETUP_VISITS) {
                VisitReminderSettingsScreen(onBack = { nav.popBackStack() }, vm = vm)
            }
            composable(ROUTE_SETUP_MISC) {
                MiscSettingsScreen(onBack = { nav.popBackStack() })
            }
            composable(ROUTE_SETUP_BACKUP) {
                BackupScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable(ROUTE_SETUP_REPORT) {
                ReportScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable(ROUTE_CORRELATIONS) {
                CorrelationScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable(
                ROUTE_EDIT,
                arguments = listOf(navArgument("medId") { type = NavType.LongType }),
            ) { entry ->
                EditMedScreen(
                    vm = vm,
                    medId = entry.arguments?.getLong("medId") ?: 0L,
                    onDone = { nav.popBackStack() },
                )
            }
            composable(
                ROUTE_NOTE_VIEW,
                arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("noteId") ?: 0L
                NoteViewScreen(
                    vm = vm,
                    noteId = id,
                    onEdit = { nav.navigate("noteEdit/" + id) },
                    onDone = { nav.popBackStack() },
                )
            }
            composable(
                ROUTE_NOTE_EDIT,
                arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
            ) { entry ->
                EditNoteScreen(
                    vm = vm,
                    noteId = entry.arguments?.getLong("noteId") ?: 0L,
                    onDone = { nav.popBackStack() },
                )
            }
            composable(
                ROUTE_VISIT_EDIT,
                arguments = listOf(navArgument("visitId") { type = NavType.LongType }),
            ) { entry ->
                EditVisitScreen(
                    vm = vm,
                    visitId = entry.arguments?.getLong("visitId") ?: 0L,
                    onDone = { nav.popBackStack() },
                )
            }
            composable(
                ROUTE_LIB_EDIT,
                arguments = listOf(navArgument("entryId") { type = NavType.LongType }),
            ) { entry ->
                EditLibraryScreen(
                    vm = vm,
                    entryId = entry.arguments?.getLong("entryId") ?: 0L,
                    onDone = { nav.popBackStack() },
                )
            }
        }
    }
}

private fun navigateTab(nav: androidx.navigation.NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(ROUTE_HOME) { inclusive = false }
        launchSingleTop = true
    }
}
