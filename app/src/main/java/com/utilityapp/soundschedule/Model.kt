package com.utilityapp.soundschedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

const val MAX_WINDOWS = 5
const val MINUTES_PER_DAY = 24 * 60
const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY

enum class RingerMode(val label: String) {
    NORMAL("Sound"),
    VIBRATE("Vibrate"),
    SILENT("Silent"),
}

/** What still gets through while a profile is Silent. */
data class LetThrough(
    val starredContacts: Boolean = true,
    val repeatCallers: Boolean = true,
    val alarms: Boolean = true,
)

/** Volumes are percentages (0..100); null means "leave this stream as it is". */
data class SoundProfile(
    val ringerMode: RingerMode,
    val ringVolume: Int? = null,
    val notificationVolume: Int? = null,
    val mediaVolume: Int? = null,
    val alarmVolume: Int? = null,
    val letThrough: LetThrough = LetThrough(),
)

/** A named stretch of time with its own profile, repeating on the chosen days. */
data class TimeWindow(
    val id: Long,
    val name: String,
    /** Minutes after midnight; start is inclusive, end is exclusive. */
    val start: Int,
    val end: Int,
    val profile: SoundProfile,
    /** Days the window starts on. An overnight window (e.g. 23:00 to 08:00) runs into the next morning. */
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val enabled: Boolean = true,
    /** When the window ends, raise the volume over this many minutes instead of all at once. 0 = off. */
    val fadeMinutes: Int = 0,
    /** The start date of a single occurrence to skip. */
    val skipDate: LocalDate? = null,
) {
    val isOvernight: Boolean get() = start > end

    val lengthMinutes: Int get() = if (start < end) end - start else MINUTES_PER_DAY - start + end

    fun overlaps(other: TimeWindow): Boolean =
        weekSegments().any { a -> other.weekSegments().any { b -> a.first <= b.last && b.first <= a.last } }

    /** Where this window sits in the week, as ranges of minutes counted from Monday 00:00. */
    private fun weekSegments(): List<IntRange> {
        if (start == end) return emptyList()
        return days.flatMap { day ->
            val s = (day.value - 1) * MINUTES_PER_DAY + start
            val e = s + lengthMinutes
            if (e <= MINUTES_PER_WEEK) listOf(s until e)
            else listOf(s until MINUTES_PER_WEEK, 0 until e - MINUTES_PER_WEEK)
        }
    }

    /** Concrete occurrences starting on dates in [from]..[to], minus a skipped one. */
    fun occurrences(from: LocalDate, to: LocalDate, zone: ZoneId): List<Occurrence> {
        if (start == end) return emptyList()
        return generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .filter { it.dayOfWeek in days && it != skipDate }
            .map { date ->
                val begin = date.atTime(start / 60, start % 60).atZone(zone)
                val endDate = if (start < end) date else date.plusDays(1)
                Occurrence(this, begin, endDate.atTime(end / 60, end % 60).atZone(zone))
            }
            .toList()
    }

    /** The next occurrence that hasn't ended yet, ignoring any skip. */
    fun nextOccurrence(now: ZonedDateTime): Occurrence? =
        copy(skipDate = null)
            .occurrences(now.toLocalDate().minusDays(1), now.toLocalDate().plusDays(7), now.zone)
            .firstOrNull { it.end.isAfter(now) }
}

data class Occurrence(val window: TimeWindow, val begin: ZonedDateTime, val end: ZonedDateTime) {
    operator fun contains(time: ZonedDateTime) = !time.isBefore(begin) && time.isBefore(end)
}

/** A manual takeover. [mode] null means the schedule is paused; [untilMillis] null means until turned off. */
data class Override(val mode: RingerMode?, val untilMillis: Long?)

data class CalendarMode(val enabled: Boolean = false, val mode: RingerMode = RingerMode.SILENT)

data class SoundSchedule(
    val enabled: Boolean = false,
    /** Used whenever nothing else applies. */
    val defaultProfile: SoundProfile = SoundProfile(RingerMode.NORMAL, ringVolume = 80, notificationVolume = 80),
    val windows: List<TimeWindow> = listOf(
        TimeWindow(1, "Night", 23 * 60, 8 * 60, SoundProfile(RingerMode.SILENT)),
    ),
    val override: Override? = null,
    val calendar: CalendarMode = CalendarMode(),
    /** Use alarm-clock alarms, which Android never delays, at the cost of a status-bar alarm icon. */
    val strictTiming: Boolean = false,
) {
    /** Occurrences of enabled windows around [now], earliest first. */
    fun occurrencesAround(now: ZonedDateTime, daysAhead: Long = 8): List<Occurrence> =
        windows.filter { it.enabled }
            .flatMap { it.occurrences(now.toLocalDate().minusDays(1), now.toLocalDate().plusDays(daysAhead), now.zone) }
            .sortedBy { it.begin.toInstant() }

    fun activeOccurrence(now: ZonedDateTime): Occurrence? = occurrencesAround(now, daysAhead = 0).firstOrNull { now in it }
}
