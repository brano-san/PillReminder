package tech.unispace.pillreminder.alarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import tech.unispace.pillreminder.container
import tech.unispace.pillreminder.ui.Lang
import tech.unispace.pillreminder.ui.formatClock
import tech.unispace.pillreminder.ui.theme.PillTheme

/**
 * Полноэкранное напоминание: система открывает его через full-screen intent уведомления,
 * когда экран погашен или заблокирован. Показывается поверх экрана блокировки.
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val doseId = intent.getLongExtra(Notifications.EXTRA_DOSE_ID, -1L)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: Lang.s.timeToTakeFallback
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()

        fun act(action: suspend (Long) -> Unit) {
            lifecycleScope.launch {
                if (doseId >= 0) {
                    action(doseId)
                    Notifications.dismiss(this@AlarmActivity, doseId)
                }
                finish()
            }
        }

        setContent {
            PillTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            formatClock(System.currentTimeMillis()),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                        if (text.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Spacer(Modifier.height(40.dp))
                        Button(
                            onClick = { act { container.planner.markTaken(it) } },
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                        ) {
                            Text(Lang.s.took, style = MaterialTheme.typography.titleLarge)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { act { container.planner.markSkipped(it) } },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Text(Lang.s.skip)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                lifecycleScope.launch {
                                    if (doseId >= 0) {
                                        val dose = container.db.doseDao().getById(doseId)
                                        if (dose != null) {
                                            val minutes = tech.unispace.pillreminder.data
                                                .Settings(this@AlarmActivity).snoozeMinutes
                                            AlarmScheduler(this@AlarmActivity).schedule(
                                                dose = dose,
                                                triggerAt = System.currentTimeMillis() + minutes * 60_000L,
                                            )
                                        }
                                        Notifications.dismiss(this@AlarmActivity, doseId)
                                    }
                                    finish()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Text(
                                Lang.s.snoozeAction(
                                    tech.unispace.pillreminder.data.Settings(this@AlarmActivity).snoozeMinutes,
                                ),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { finish() }) {
                            Text(Lang.s.fsClose)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
    }
}
