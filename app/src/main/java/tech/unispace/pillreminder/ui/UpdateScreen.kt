package tech.unispace.pillreminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tech.unispace.pillreminder.data.Settings as AppSettings
import tech.unispace.pillreminder.update.Updater

@Composable
fun UpdateSettingsScreen(onBack: () -> Unit) {
    val s = Lang.s
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var auto by remember { mutableStateOf(settings.autoUpdate) }
    val state by Updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }
    val current = remember { Updater.currentVersion(context) }

    SettingsSubScreen(s.updatesCard, onBack, snackbars) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SwitchRow(s.updateAutoTitle, s.updateAutoBody, auto) {
                    auto = it
                    settings.autoUpdate = it
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                UpdateStatus(state, current)
                val busy = state is Updater.State.Checking || state is Updater.State.Downloading || state is Updater.State.Installing
                when (val st = state) {
                    is Updater.State.Available -> Button(
                        onClick = { scope.launch { Updater.install(context, st.release) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(s.updateInstall, maxLines = 1, softWrap = false) }
                    else -> OutlinedButton(
                        onClick = { scope.launch { Updater.check(context) } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(s.updateCheckNow, maxLines = 1, softWrap = false) }
                }
            }
        }
    }
}

/** Строка состояния, прогресс и список изменений — общие для экрана настроек и диалога на старте. */
@Composable
private fun UpdateStatus(state: Updater.State, current: String) {
    val s = Lang.s
    when (state) {
        Updater.State.Idle -> Text(s.versionLabel(current), style = MaterialTheme.typography.bodyMedium)
        Updater.State.Checking -> Text(s.updateChecking)
        Updater.State.UpToDate -> Text(s.updateUpToDate(current))
        is Updater.State.Available -> {
            Text(s.updateAvailable(state.release.version), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                state.release.notes,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
            )
        }
        is Updater.State.Downloading -> {
            Text(s.updateDownloading(state.percent))
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
        Updater.State.Installing -> Text(s.updateInstalling)
        is Updater.State.Failed -> Text(s.updateFailed(state.message), color = MaterialTheme.colorScheme.error)
    }
}

/** Предложение обновиться после автопроверки на старте. Закрытие = «позже»: завтра спросим снова. */
@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val s = Lang.s
    val context = LocalContext.current
    val state by Updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    val current = remember { Updater.currentVersion(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.updatesCard) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { UpdateStatus(state, current) } },
        confirmButton = {
            val st = state
            if (st is Updater.State.Available) {
                TextButton(onClick = { scope.launch { Updater.install(context, st.release) } }) { Text(s.updateInstall) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.updateLater) } },
    )
}
