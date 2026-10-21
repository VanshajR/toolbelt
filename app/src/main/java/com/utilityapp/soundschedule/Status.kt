package com.utilityapp.soundschedule

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import com.utilityapp.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The fixed mode colours: darker means quieter. Shared by the app, widget and tile. */
object ModeColors {
    const val SILENT = 0xFF2B2766.toInt()
    const val SILENT_DARK = 0xFF423D9E.toInt()
    const val VIBRATE = 0xFF6A61C8.toInt()
    const val SOUND = 0xFFE9B66E.toInt()
    const val INK = 0xFF17171C.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()

    fun background(mode: RingerMode?, dark: Boolean = false) = when (mode) {
        RingerMode.SILENT -> if (dark) SILENT_DARK else SILENT
        RingerMode.VIBRATE -> VIBRATE
        RingerMode.NORMAL -> SOUND
        null -> if (dark) 0xFF2C2C33.toInt() else 0xFFE6E5E0.toInt()
    }

    fun content(mode: RingerMode?, dark: Boolean = false) = when (mode) {
        RingerMode.SILENT, RingerMode.VIBRATE -> WHITE
        RingerMode.NORMAL -> INK
        null -> if (dark) WHITE else INK
    }
}

@DrawableRes
fun RingerMode?.icon(): Int = when (this) {
    RingerMode.SILENT -> R.drawable.ic_silent
    RingerMode.VIBRATE -> R.drawable.ic_vibrate
    RingerMode.NORMAL -> R.drawable.ic_sound
    null -> R.drawable.ic_pause
}

/** A headline ("Vibrate") and detail ("until 18:00 · Work") for what's active. */
data class StatusText(val mode: RingerMode?, val headline: String, val detail: String)

fun statusText(context: Context, active: Active, schedule: SoundSchedule, now: ZonedDateTime = ZonedDateTime.now()): StatusText {
    val until = active.until?.let { "until ${formatMoment(context, it, now)}" }
    fun join(vararg parts: String?) = parts.filterNotNull().joinToString(" · ")
    return when (active) {
        is Active.Paused -> StatusText(null, "Paused", join(until, "Schedule paused"))
        is Active.Manual -> StatusText(active.mode, active.mode.label, join(until, "Override"))
        is Active.Meeting -> StatusText(active.mode, active.mode.label, join(until, active.title.ifBlank { "Calendar event" }))
        is Active.Window -> {
            val profile = active.occurrence.window.profile
            StatusText(profile.ringerMode, profile.ringerMode.label, join(until, active.occurrence.window.name))
        }
        is Active.Default -> StatusText(schedule.defaultProfile.ringerMode, schedule.defaultProfile.ringerMode.label, join(until, "Default"))
    }
}

/** A coloured stretch of one day's timeline, as fractions of the day. */
data class Segment(val start: Float, val end: Float, val mode: RingerMode)

/** Enabled windows that fall on [date], clipped to that day. Gaps belong to the default profile. */
fun daySegments(schedule: SoundSchedule, date: LocalDate, now: ZonedDateTime): List<Segment> {
    val dayStart = date.atStartOfDay(now.zone)
    val dayEnd = date.plusDays(1).atStartOfDay(now.zone)
    val dayMillis = (dayEnd.toInstant().toEpochMilli() - dayStart.toInstant().toEpochMilli()).toFloat()
    fun fraction(t: ZonedDateTime) =
        ((t.toInstant().toEpochMilli() - dayStart.toInstant().toEpochMilli()) / dayMillis).coerceIn(0f, 1f)
    return schedule.windows.filter { it.enabled }
        .flatMap { it.occurrences(date.minusDays(1), date, now.zone) }
        .filter { it.end.isAfter(dayStart) && it.begin.isBefore(dayEnd) }
        .map { Segment(fraction(it.begin), fraction(it.end), it.window.profile.ringerMode) }
}

fun dayFraction(now: ZonedDateTime): Float = (now.toLocalTime().toSecondOfDay() / 86_400f)

fun formatMinutes(context: Context, minutes: Int): String =
    LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofPattern(clockPattern(context)))

/** "22:30", "tomorrow 08:00" or "Saturday 09:00", relative to [now]. */
fun formatMoment(context: Context, time: ZonedDateTime, now: ZonedDateTime): String {
    val clock = time.format(DateTimeFormatter.ofPattern(clockPattern(context)))
    return when (ChronoUnit.DAYS.between(now.toLocalDate(), time.toLocalDate())) {
        0L -> clock
        1L -> "tomorrow $clock"
        else -> "${time.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())} $clock"
    }
}

private fun clockPattern(context: Context) = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
