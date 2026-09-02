package tech.unispace.pillreminder

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.unispace.pillreminder.alarm.Notifications
import tech.unispace.pillreminder.data.AppDatabase
import tech.unispace.pillreminder.data.Settings
import tech.unispace.pillreminder.ui.Lang
import tech.unispace.pillreminder.data.Planner

/** Мини-контейнер вместо DI-фреймворка: зависимостей немного. */
class AppContainer(context: Context) {
    val db: AppDatabase = AppDatabase.get(context)
    val planner: Planner = Planner(context.applicationContext, db)
}

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Lang.code = Settings(this).language
        container = AppContainer(this)
        Notifications.createChannels(this)
        // Ð Ð°ÑÐ¿Ð¸ÑÐ°Ð½Ð¸Ðµ Â«Ð¿Ð¾ ÑÐ°ÑÐ°Ð¼Â» Ð¸ Ð±ÑÐ´Ð¸Ð»ÑÐ½Ð¸ÐºÐ¸ Ð½Ðµ Ð´Ð¾Ð»Ð¶Ð½Ñ Ð¶Ð´Ð°ÑÑ, Ð¿Ð¾ÐºÐ° Ð¿Ð¾Ð»ÑÐ·Ð¾Ð²Ð°ÑÐµÐ»Ñ ÑÑÐ¾-ÑÐ¾ Ð½Ð°Ð¶Ð¼ÑÑ.
        CoroutineScope(Dispatchers.IO).launch { container.planner.rescheduleAlarms() }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as App).container
