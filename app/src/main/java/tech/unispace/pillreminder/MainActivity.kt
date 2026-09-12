package tech.unispace.pillreminder

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import tech.unispace.pillreminder.data.Settings
import tech.unispace.pillreminder.ui.AdherenceState
import tech.unispace.pillreminder.ui.BackupScreen
import tech.unispace.pillreminder.ui.ChartSettingsScreen
import tech.unispace.pillreminder.ui.CorrelationScreen
import tech.unispace.pillreminder.ui.DeliverySettingsScreen
import tech.unispace.pillreminder.ui.EditLibraryScreen
import tech.unispace.pillreminder.ui.EditMedScreen
import tech.unispace.pillreminder.ui.EditNoteScreen
import tech.unispace.pillreminder.ui.EditTrackerScreen
import tech.unispace.pillreminder.ui.EditVisitScreen
import tech.unispace.pillreminder.ui.HomeScreen
import tech.unispace.pillreminder.ui.Lang
import tech.unispace.pillreminder.ui.LibraryScreen
import tech.unispace.pillreminder.ui.MainViewModel
import tech.unispace.pillreminder.ui.NoteViewScreen
import tech.unispace.pillreminder.ui.OnboardingScreen
import tech.unispace.pillreminder.ui.PrivacySettingsScreen
import tech.unispace.pillreminder.ui.RecordsScreen
import tech.unispace.pillreminder.ui.RepeatSettingsScreen
import tech.unispace.pillreminder.ui.ReportScreen
import tech.unispace.pillreminder.ui.SettingsMenuScreen
import tech.unispace.pillreminder.ui.SoundSettingsScreen
import tech.unispace.pillreminder.ui.StatsScreen
import tech.unispace.pillreminder.ui.StockSettingsScreen
import tech.unispace.pillreminder.ui.TipsScreen
import tech.unispace.pillreminder.ui.TrackerDetailScreen
import tech.unispace.pillreminder.ui.TrackersScreen
import tech.unispace.pillreminder.ui.VisitReminderSettingsScreen
import tech.unispace.pillreminder.ui.theme.PillTheme

class MainActivity : FragmentActivity() {

    /** Системный запрос отпечатка или кода устройства. */
    private fun askUnlock(onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(Lang.s.lockPrompt)
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
                .build(),
        )
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val settings = Settings(this)
        setContent {
            PillTheme {
                // Заливка на всё окно: иначе при открытой клавиатуре внизу видна полоса фона окна.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Медицинские данные: при включённом замке экран открывается только после проверки.
                    var unlocked by rememberSaveable { mutableStateOf(!settings.appLockEnabled) }
                    if (unlocked) {
                        AppRoot()
                    } else {
                        LockScreen(onUnlock = { askUnlock { unlocked = true } })
                        LaunchedEffect(Unit) { askUnlock { unlocked = true } }
                    }
                }
            }
        }
    }
}

/** Заглушка вместо содержимого, пока приложение заблокировано. */
@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    val s = Lang.s
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(s.lockPrompt, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onUnlock) { Text(s.lockUnlock) }
    }
}

private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_HOME = "home"
private const val ROUTE_RECORDS = "records"
private const val ROUTE_TRACKERS = "trackers"
private const val ROUTE_STATS = "stats"
private const val ROUTE_SETUP = "setup"
private const val ROUTE_TIPS = "tips"
private const val ROUTE_EDIT = "edit/{medId}"
private const val ROUTE_NOTE_VIEW = "note/{noteId}"
private const val ROUTE_NOTE_EDIT = "noteEdit/{noteId}"
private const val ROUTE_VISIT_EDIT = "visitEdit/{visitId}"
private const val ROUTE_LIBRARY = "library"
private const val ROUTE_LIB_EDIT = "libEdit/{entryId}"
private const val ROUTE_TRACKER_DETAIL = "tracker/{trackerId}"
private const val ROUTE_TRACKER_EDIT = "trackerEdit/{trackerId}/{type}"
private const val ROUTE_CORRELATIONS = "correlations"
private const val ROUTE_SETUP_REPEATS = "setup/repeats"
private const val ROUTE_SETUP_SOUND = "setup/sound"
private const val ROUTE_SETUP_DELIVERY = "setup/delivery"
private const val ROUTE_SETUP_VISITS = "setup/visits"
private const val ROUTE_SETUP_PRIVACY = "setup/privacy"
private const val ROUTE_SETUP_STOCK = "setup/stock"
private const val ROUTE_SETUP_CHARTS = "setup/charts"
private const val ROUTE_SETUP_BACKUP = "setup/backup"
private const val ROUTE_SETUP_REPORT = "setup/report"

@Composable
private fun AppRoot() {
    val vm: MainViewModel = viewModel()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route in setOf(ROUTE_HOME, ROUTE_RECORDS, ROUTE_TRACKERS, ROUTE_STATS, ROUTE_SETUP)
    val s = Lang.s
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val startRoute = remember { if (settings.tutorialSeen) ROUTE_HOME else ROUTE_ONBOARDING }

    Scaffold(
        bottomBar = {
            if (showBar) {
                // Цвет панели = цвет поверхности: без отдельной серой полосы снизу.
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    TabItem(nav, route, ROUTE_HOME, Icons.Default.Home, s.tabPills)
                    TabItem(nav, route, ROUTE_RECORDS, Icons.AutoMirrored.Filled.Notes, s.tabNotes)
                    TabItem(nav, route, ROUTE_TRACKERS, Icons.AutoMirrored.Filled.TrendingUp, s.tabTrackers)
                    TabItem(nav, route, ROUTE_STATS, Icons.Default.History, s.tabHistory)
                    TabItem(nav, route, ROUTE_SETUP, Icons.Default.Settings, s.tabSettings)
                }
            }
        },
    ) { padding ->
        val tabPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
        NavHost(
            navController = nav,
            startDestination = startRoute,
            modifier = Modifier.fillMaxSize(),
            // Без fade между вкладками: иначе нажатие «проглатывается» на время анимации.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(ROUTE_ONBOARDING) {
                OnboardingScreen(
                    onFinish = {
                        settings.tutorialSeen = true
                        if (nav.previousBackStackEntry == null) {
                            nav.navigate(ROUTE_HOME) { popUpTo(ROUTE_ONBOARDING) { inclusive = true } }
                        } else {
                            nav.popBackStack()
                        }
                    },
                )
            }
            composable(ROUTE_HOME) {
                val state by vm.home.collectAsState()
                val sleepToRate by vm.sleepToRate.collectAsState()
                val trackerRows by vm.trackerRows.collectAsState()
                HomeScreen(
                    state = state,
                    trackerRows = trackerRows,
                    contentPadding = tabPadding,
                    onWakeUp = { vm.wakeUp() },
                    onRestartDay = { vm.restartDay() },
                    onTake = { vm.take(it) },
                    onTakeNow = { medId, done -> vm.takeNow(medId, done) },
                    onDeleteIntake = { vm.deleteIntake(it) },
                    onSkip = { vm.skip(it) },
                    onEdit = { nav.navigate("edit/" + it) },
                    onAdd = { nav.navigate("edit/0") },
                    onDelete = { vm.delete(it) },
                    onUndo = { vm.undo(it) },
                    onTakeAll = { done -> vm.takeAllDue(done) },
                    onReorder = { vm.saveMedOrder(it) },
                    onOpenSettings = { nav.navigate(ROUTE_SETUP_DELIVERY) },
                    onOpenTracker = { nav.navigate("tracker/" + it) },
                    onOpenTips = { nav.navigate(ROUTE_TIPS) },
                    onOpenTutorial = { nav.navigate(ROUTE_ONBOARDING) },
                    onOpenReport = { nav.navigate(ROUTE_SETUP_REPORT) },
                    onBedtime = { vm.goToBed() },
                    onMeal = { vm.recordMeal() },
                    sleepToRate = sleepToRate,
                    onRateSleep = { entry, sleep, wake -> vm.rateSleep(entry, sleep, wake) },
                    onDismissSleepRating = { vm.dismissSleepRating() },
                    onDuplicate = { vm.duplicateMed(it) },
                    onQuickEntry = { vm.addTrackerEntry(it) },
                )
            }
            composable(ROUTE_RECORDS) {
                val notes by vm.notes.collectAsState()
                val visits by vm.visits.collectAsState()
                val library by vm.library.collectAsState()
                RecordsScreen(
                    notes = notes,
                    visits = visits,
                    library = library,
                    onOpenNote = { nav.navigate("note/" + it) },
                    onAddNote = { nav.navigate("noteEdit/0") },
                    onDeleteNote = { vm.deleteNote(it) },
                    onEditVisit = { nav.navigate("visitEdit/" + it) },
                    onAddVisit = { nav.navigate("visitEdit/0") },
                    onDeleteVisit = { vm.deleteVisit(it) },
                    onEditLibrary = { nav.navigate("libEdit/" + it) },
                    onAddLibrary = { nav.navigate("libEdit/0") },
                    onDeleteLibrary = { vm.deleteLibraryEntry(it) },
                    contentPadding = tabPadding,
                )
            }
            composable(ROUTE_TRACKERS) {
                val trackerRows by vm.trackerRows.collectAsState()
                TrackersScreen(
                    rows = trackerRows,
                    onOpen = { nav.navigate("tracker/" + it) },
                    onCreate = { type -> nav.navigate("trackerEdit/0/" + type) },
                    onCreateAll = { types -> vm.createTrackers(types) },
                    onAddEntry = { vm.addTrackerEntry(it) },
                    onOpenCorrelations = { nav.navigate(ROUTE_CORRELATIONS) },
                    contentPadding = tabPadding,
                )
            }
            composable(ROUTE_STATS) {
                val journal by vm.journal.collectAsState()
                val heatmap by vm.heatmap.collectAsState()
                // Дисциплина пересчитывается при каждом изменении журнала — запрос дешёвый.
                var adherence by remember { mutableStateOf(AdherenceState()) }
                LaunchedEffect(journal) { adherence = vm.adherence() }
                StatsScreen(
                    adherence = adherence,
                    journal = journal,
                    heatmap = heatmap,
                    onSelectDay = { vm.selectedDay.value = it },
                    onMonthShift = { delta -> vm.heatMonthStart.value = vm.heatMonthStart.value.plusMonths(delta) },
                    onUndo = { vm.undo(it) },
                    onTakeAt = { doseId, at -> vm.takeAt(doseId, at) },
                    onSkip = { vm.skip(it) },
                    onDeleteMeal = { vm.deleteMeal(it) },
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
                    onOpenPrivacy = { nav.navigate(ROUTE_SETUP_PRIVACY) },
                    onOpenStock = { nav.navigate(ROUTE_SETUP_STOCK) },
                    onOpenCharts = { nav.navigate(ROUTE_SETUP_CHARTS) },
                    onOpenBackup = { nav.navigate(ROUTE_SETUP_BACKUP) },
                    onOpenReport = { nav.navigate(ROUTE_SETUP_REPORT) },
                    onOpenTutorial = { nav.navigate(ROUTE_ONBOARDING) },
                )
            }
            composable(ROUTE_SETUP_REPEATS) { RepeatSettingsScreen(onBack = { nav.popBackStack() }, vm = vm) }
            composable(ROUTE_SETUP_SOUND) { SoundSettingsScreen(onBack = { nav.popBackStack() }) }
            composable(ROUTE_SETUP_DELIVERY) { DeliverySettingsScreen(onBack = { nav.popBackStack() }) }
            composable(ROUTE_SETUP_VISITS) { VisitReminderSettingsScreen(onBack = { nav.popBackStack() }, vm = vm) }
            composable(ROUTE_SETUP_PRIVACY) { PrivacySettingsScreen(onBack = { nav.popBackStack() }) }
            composable(ROUTE_SETUP_STOCK) { StockSettingsScreen(onBack = { nav.popBackStack() }) }
            composable(ROUTE_SETUP_CHARTS) { ChartSettingsScreen(onBack = { nav.popBackStack() }) }
            composable(ROUTE_SETUP_BACKUP) { BackupScreen(vm = vm, onBack = { nav.popBackStack() }) }
            composable(ROUTE_SETUP_REPORT) { ReportScreen(vm = vm, onBack = { nav.popBackStack() }) }
            composable(ROUTE_CORRELATIONS) { CorrelationScreen(vm = vm, onBack = { nav.popBackStack() }) }
            composable(ROUTE_TIPS) { TipsScreen(onBack = { nav.popBackStack() }) }

            composable(ROUTE_LIBRARY) {
                val library by vm.library.collectAsState()
                LibraryScreen(
                    library = library,
                    onEdit = { nav.navigate("libEdit/" + it) },
                    onAdd = { nav.navigate("libEdit/0") },
                    onDelete = { vm.deleteLibraryEntry(it) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(ROUTE_EDIT, arguments = listOf(navArgument("medId") { type = NavType.LongType })) { entry ->
                EditMedScreen(
                    vm = vm,
                    medId = entry.arguments?.getLong("medId") ?: 0L,
                    onOpenLibrary = { nav.navigate(ROUTE_LIBRARY) },
                    onOpenLibraryEntry = { nav.navigate("libEdit/" + it) },
                    onDone = { nav.popBackStack() },
                )
            }
            composable(ROUTE_NOTE_VIEW, arguments = listOf(navArgument("noteId") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("noteId") ?: 0L
                NoteViewScreen(vm = vm, noteId = id, onEdit = { nav.navigate("noteEdit/" + id) }, onDone = { nav.popBackStack() })
            }
            composable(ROUTE_NOTE_EDIT, arguments = listOf(navArgument("noteId") { type = NavType.LongType })) { entry ->
                EditNoteScreen(vm = vm, noteId = entry.arguments?.getLong("noteId") ?: 0L, onDone = { nav.popBackStack() })
            }
            composable(ROUTE_VISIT_EDIT, arguments = listOf(navArgument("visitId") { type = NavType.LongType })) { entry ->
                EditVisitScreen(vm = vm, visitId = entry.arguments?.getLong("visitId") ?: 0L, onDone = { nav.popBackStack() })
            }
            composable(ROUTE_LIB_EDIT, arguments = listOf(navArgument("entryId") { type = NavType.LongType })) { entry ->
                EditLibraryScreen(vm = vm, entryId = entry.arguments?.getLong("entryId") ?: 0L, onDone = { nav.popBackStack() })
            }
            composable(ROUTE_TRACKER_DETAIL, arguments = listOf(navArgument("trackerId") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("trackerId") ?: 0L
                val trackerRows by vm.trackerRows.collectAsState()
                TrackerDetailScreen(
                    vm = vm,
                    trackerId = id,
                    rows = trackerRows,
                    onEditTracker = { nav.navigate("trackerEdit/" + id + "/-") },
                    onDone = { nav.popBackStack() },
                )
            }
            composable(
                ROUTE_TRACKER_EDIT,
                arguments = listOf(
                    navArgument("trackerId") { type = NavType.LongType },
                    navArgument("type") { type = NavType.StringType },
                ),
            ) { entry ->
                EditTrackerScreen(
                    vm = vm,
                    trackerId = entry.arguments?.getLong("trackerId") ?: 0L,
                    presetType = entry.arguments?.getString("type")?.takeIf { it != "-" } ?: "",
                    onDone = { nav.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(nav: NavHostController, current: String?, route: String, icon: ImageVector, label: String) {
    NavigationBarItem(
        selected = current == route,
        onClick = { navigateTab(nav, route) },
        icon = { Icon(icon, contentDescription = null) },
        // Одна строка без переносов: «Настройки» не должно ломаться на «и».
        label = {
            Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        },
    )
}

private fun navigateTab(nav: NavHostController, route: String) {
    // Повторный тап по активной вкладке — ничего не делаем, иначе экран мигает.
    if (nav.currentDestination?.route == route) return
    nav.navigate(route) {
        popUpTo(ROUTE_HOME) { inclusive = false }
        launchSingleTop = true
    }
}
