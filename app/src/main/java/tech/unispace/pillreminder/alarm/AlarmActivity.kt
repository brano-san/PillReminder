package tech.unispace.pillreminder.alarm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
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
import tech.unispace.pillreminder.data.Settings
import tech.unispace.pillreminder.ui.Lang
import tech.unispace.pillreminder.ui.formatClock
import tech.unispace.pillreminder.ui.theme.PillTheme

/**
 * Полноэкранное напоминание: система открывает его через full-screen intent уведомления,
 * когда экран погашен или заблокирован. Показывается поверх экрана блокировки.
 */
@OptIn(ExperimentalLayoutApi::class)
class AlarmActivity : ComponentActivity() {

    /**
     * Активность `singleTop`: второй приём приходит сюда же. Без этого экран показывал бы первую
     * таблетку, а большая кнопка «Выпито» отмечала бы её, а не ту, о которой звонит будильник.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val doseId = intent.getLongExtra(Notifications.EXTRA_DOSE_ID, -1L)
        // Группа «3 таблетки»: большие кнопки отмечают все приёмы, а не только старший.
        val ids = intent.getLongArrayExtra(ActionReceiver.EXTRA_DOSE_IDS)?.toList()?.takeIf { it.isNotEmpty() } ?: listOf(doseId)
        val attempt = intent.getIntExtra(EXTRA_ATTEMPT, 0)
        val plannedAt = intent.getLongExtra(EXTRA_PLANNED_AT, 0L)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: Lang.s.timeToTakeFallback
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        // Варианты «Отложить» настраиваются в «Повторах».
        val snoozeOptions = Settings(this).snoozeOptions
        // Честная подпись: повтор придёт через интервал повторов; на последнем повторе или при
        // выключенных повторах — не придёт; в тихие часы — придёт по их окончании.
        val settings = Settings(this)
        val now = System.currentTimeMillis()
        val nextRepeat = now + settings.repeatIntervalMinutes * 60_000L
        val plannedText = if (plannedAt > 0) Lang.s.plannedAtShort(formatClock(plannedAt)) else ""
        val closeText = when {
            !settings.repeatEnabled || attempt + 1 >= settings.repeatCount -> Lang.s.fsCloseNoRepeat
            isQuiet(settings, nextRepeat) -> Lang.s.fsCloseAt(formatClock(quietEndMillis(settings, nextRepeat)))
            else -> Lang.s.fsCloseIn(Lang.s.duration(settings.repeatIntervalMinutes))
        }

        fun act(action: suspend (Long) -> Unit) {
            lifecycleScope.launch {
                ids.filter { it >= 0 }.forEach { id ->
                    action(id)
                    Notifications.dismiss(this@AlarmActivity, id)
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
                            .verticalScroll(rememberScrollState())
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            // Плановое время приёма, а не момент срабатывания: часы на экране не тикают,
                            // и через двадцать минут человек читал бы старое время как текущее.
                            plannedText.ifBlank { formatClock(System.currentTimeMillis()) },
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
                            Text(if (ids.size > 1) Lang.s.takeAllAction else Lang.s.took, style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
                        }
                        Spacer(Modifier.height(16.dp))
                        // Три одинаковые широкие кнопки «Отложить на …» занимали пол-экрана и
                        // спорили с «Выпито». Теперь это подпись и ряд коротких вариантов.
                        Text(
                            Lang.s.snoozeRowLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            snoozeOptions.forEach { minutes ->
                                OutlinedButton(
                                    onClick = {
                                        lifecycleScope.launch {
                                            // Момент «отложить» живёт в приёме — пересборка будильников его уважает.
                                            // Вся группа, как и «Выпито» рядом: иначе через три минуты
                                            // звонили остальные приёмы той же группы.
                                            container.planner.snoozeAll(ids.filter { it >= 0 }, minutes)
                                            finish()
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                ) {
                                    Text(Lang.s.duration(minutes), maxLines = 1, softWrap = false)
                                }
                            }
                        }
                        // Разрушительное действие — внизу, красным и с большим отступом,
                        // чтобы спросонья не попасть в него вместо «Выпил».
                        Spacer(Modifier.height(36.dp))
                        OutlinedButton(
                            onClick = { act { container.planner.markSkipped(it) } },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        ) {
                            Text(if (ids.size > 1) Lang.s.skipAllAction else Lang.s.skip, maxLines = 1, softWrap = false)
                        }
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                            Text(closeText, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_ATTEMPT = "attempt"
        const val EXTRA_PLANNED_AT = "plannedAt"
    }
}
