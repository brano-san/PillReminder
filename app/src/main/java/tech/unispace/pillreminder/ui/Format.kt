package tech.unispace.pillreminder.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val clockFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

fun formatClock(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(clockFmt)

fun formatDay(epochDay: Long): String {
    val date = LocalDate.ofEpochDay(epochDay)
    val todayDate = LocalDate.now()
    return when (date) {
        todayDate -> Lang.s.todayWord
        todayDate.minusDays(1) -> Lang.s.yesterdayWord
        else -> date.format(DateTimeFormatter.ofPattern("d MMMM", Lang.s.locale))
    }
}

/** «через 2 ч 15 мин» / "in 2 h 15 min". */
fun formatCountdown(deltaMs: Long): String = Lang.s.countdown(deltaMs)

fun formatDuration(minutes: Int): String = Lang.s.duration(minutes)
