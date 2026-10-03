package tech.unispace.pillreminder.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.unispace.pillreminder.alarm.Notifications
import tech.unispace.pillreminder.data.Settings

/**
 * Суточная проверка без открытия приложения (будильник ставит [Updater.schedule]).
 * О каждой новой версии — одно тихое уведомление; нет сети — просто ждём следующих суток.
 */
class UpdateCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val settings = Settings(app)
        if (!settings.autoUpdate) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val release = Updater.check(app) ?: return@launch
                if (release.version != settings.updateNotifiedVersion) {
                    Notifications.showUpdate(app, release.version)
                    settings.updateNotifiedVersion = release.version
                }
            } finally {
                pending.finish()
            }
        }
    }
}
