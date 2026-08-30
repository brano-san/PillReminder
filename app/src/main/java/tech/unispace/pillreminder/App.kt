package tech.unispace.pillreminder

import android.app.Application
import android.content.Context
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
    }
}

val Context.container: AppContainer
    get() = (applicationContext as App).container
