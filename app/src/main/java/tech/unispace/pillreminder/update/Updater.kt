package tech.unispace.pillreminder.update

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tech.unispace.pillreminder.data.Settings
import java.net.HttpURLConnection
import java.net.URL

/**
 * Обновление из релизов GitHub. В сеть уходят только два запроса: номер последней версии и сам APK.
 * APK пишется прямо в сессию системного установщика — файла на диске нет, а подпись проверяет система:
 * чужой ключ или меньший versionCode она не поставит.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/brano-san/PillReminder/releases/latest"
    /** requestCode PendingIntent'ов (диапазоны — в CLAUDE.md). */
    private const val REQUEST_INSTALL = 990_001
    private const val REQUEST_DAILY = 990_002

    /**
     * Суточная проверка в фоне — неточным будильником: система сама сдвигает его к другим пробуждениям,
     * поэтому батарея не тратится отдельно. Выключена автопроверка — будильник снят, в фоне ничего нет.
     * Уже стоящий будильник не переставляется, иначе каждый запуск процесса откладывал бы проверку на сутки.
     */
    fun schedule(context: Context) {
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java)
        val intent = Intent(app, UpdateCheckReceiver::class.java)
        if (!Settings(app).autoUpdate) {
            PendingIntent.getBroadcast(app, REQUEST_DAILY, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                ?.let { alarms.cancel(it); it.cancel() }
            return
        }
        if (PendingIntent.getBroadcast(app, REQUEST_DAILY, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) != null) return
        val pending = PendingIntent.getBroadcast(app, REQUEST_DAILY, intent, PendingIntent.FLAG_IMMUTABLE)
        alarms.setInexactRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR,
            AlarmManager.INTERVAL_DAY,
            pending,
        )
    }

    data class Release(val version: String, val notes: String, val apkUrl: String, val apkSize: Long)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val percent: Int) : State
        data object Installing : State
        data class Failed(val message: String) : State
    }

    /** Общее состояние для настроек, диалога на старте и ресивера статуса установки. */
    val state = MutableStateFlow<State>(State.Idle)

    /** Сравнение «v1.3.1» с «1.3.0» по числам: «1.10» новее «1.9», недостающие части — нули. */
    fun isNewer(tag: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(tag)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    /** Ответ `releases/latest` → релиз; null, если к релизу не приложен release-APK. */
    fun parseRelease(json: String): Release? {
        val o = JSONObject(json)
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            if (name.endsWith("-release.apk")) {
                return Release(
                    version = o.optString("tag_name").removePrefix("v"),
                    notes = o.optString("body").replace("\r\n", "\n").trim(),
                    apkUrl = a.getString("browser_download_url"),
                    apkSize = a.optLong("size", -1),
                )
            }
        }
        return null
    }

    fun currentVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""

    /** Спрашивает GitHub и обновляет [state]. Возвращает релиз, только если он новее установленного. */
    suspend fun check(context: Context): Release? {
        state.value = State.Checking
        return try {
            val release = withContext(Dispatchers.IO) {
                val conn = URL(LATEST).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                try {
                    if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
                    parseRelease(conn.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    conn.disconnect()
                }
            }
            val newer = release?.takeIf { isNewer(it.version, currentVersion(context)) }
            state.value = if (newer != null) State.Available(newer) else State.UpToDate
            newer
        } catch (e: Exception) {
            state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            null
        }
    }

    /**
     * Качает APK в сессию установщика и отдаёт её системе. Дальше — [UpdateReceiver]: он показывает
     * системное окно подтверждения или ошибку. На Android 12+ после первого обновления из приложения
     * система ставит следующие без вопроса (приложение становится своим «установщиком»).
     */
    suspend fun install(context: Context, release: Release) {
        val app = context.applicationContext
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            if (release.apkSize > 0) setSize(release.apkSize)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        state.value = State.Downloading(0)
        var sessionId = -1
        try {
            withContext(Dispatchers.IO) {
                sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 30_000
                    try {
                        if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
                        val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
                        conn.inputStream.use { input ->
                            session.openWrite("base.apk", 0, total).use { out ->
                                val buf = ByteArray(64 * 1024)
                                var done = 0L
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    done += n
                                    if (total > 0) state.value = State.Downloading((done * 100 / total).toInt())
                                }
                                session.fsync(out)
                            }
                        }
                    } finally {
                        conn.disconnect()
                    }
                    state.value = State.Installing
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                    val status = PendingIntent.getBroadcast(app, REQUEST_INSTALL, Intent(app, UpdateReceiver::class.java), flags)
                    session.commit(status.intentSender)
                }
            }
        } catch (e: Exception) {
            if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
            state.value = State.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
}
