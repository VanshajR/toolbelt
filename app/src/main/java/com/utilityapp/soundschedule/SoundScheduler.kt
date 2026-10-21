package com.utilityapp.soundschedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/** What is in charge of the phone's sound right now, highest priority first. */
sealed interface Active {
    /** When this stops applying, if known. */
    val until: ZonedDateTime?
    val key: String

    data class Paused(override val until: ZonedDateTime?) : Active {
        override val key get() = "paused:${until?.toEpochSecond()}"
    }

    data class Manual(val mode: RingerMode, override val until: ZonedDateTime?) : Active {
        override val key get() = "manual:$mode:${until?.toEpochSecond()}"
    }

    data class Meeting(val title: String, val mode: RingerMode, override val until: ZonedDateTime) : Active {
        override val key get() = "calendar:$mode:${until.toEpochSecond()}"
    }

    data class Window(val occurrence: Occurrence) : Active {
        override val until get() = occurrence.end
        override val key get() = "window:${occurrence.window.id}:${occurrence.begin.toEpochSecond()}"
    }

    data class Default(override val until: ZonedDateTime?) : Active {
        override val key get() = "default"
    }
}

/**
 * Keeps one alarm armed for the next moment anything changes. Each run works out what should be
 * active "now", applies it if it changed, and arms the following boundary, so a late or missed
 * alarm corrects itself.
 */
object SoundScheduler {
    private const val CALENDAR_POLL_MINUTES = 30L

    fun resolve(context: Context, schedule: SoundSchedule, now: ZonedDateTime = ZonedDateTime.now()): Active {
        val override = schedule.override?.takeIf { it.untilMillis == null || it.untilMillis > now.toMillis() }
        val overrideUntil = override?.untilMillis?.let { at(it, now) }
        if (override != null) {
            return if (override.mode == null) Active.Paused(overrideUntil) else Active.Manual(override.mode, overrideUntil)
        }
        val events = calendarEvents(context, schedule, now)
        events.firstOrNull { it.beginMillis <= now.toMillis() && now.toMillis() < it.endMillis }?.let {
            return Active.Meeting(it.title, schedule.calendar.mode, at(it.endMillis, now))
        }
        val occurrences = schedule.occurrencesAround(now)
        occurrences.firstOrNull { now in it }?.let { return Active.Window(it) }
        val nextStart = listOfNotNull(
            occurrences.firstOrNull { it.begin.isAfter(now) }?.begin,
            events.firstOrNull { it.beginMillis > now.toMillis() }?.let { at(it.beginMillis, now) },
        ).minByOrNull { it.toInstant() }
        return Active.Default(nextStart)
    }

    fun profileFor(active: Active, schedule: SoundSchedule): SoundProfile? = when (active) {
        is Active.Paused -> null
        is Active.Manual -> profileForMode(active.mode, schedule)
        is Active.Meeting -> profileForMode(active.mode, schedule)
        is Active.Window -> active.occurrence.window.profile
        is Active.Default -> schedule.defaultProfile
    }

    private fun profileForMode(mode: RingerMode, schedule: SoundSchedule) = when (mode) {
        RingerMode.NORMAL -> schedule.defaultProfile.takeIf { it.ringerMode == RingerMode.NORMAL }
            ?: SoundProfile(RingerMode.NORMAL, ringVolume = 80, notificationVolume = 80)
        else -> SoundProfile(mode)
    }

    /** Starts (or with null, ends) a manual override and applies the result straight away. */
    fun setOverride(context: Context, override: Override?) {
        val repo = ScheduleRepository(context)
        repo.save(repo.load().copy(override = override))
        sync(context, force = true)
    }

    /**
     * Re-evaluates the schedule. [force] re-applies the current profile even if nothing changed,
     * for when the user edited it; otherwise unchanged state is left alone so manual tweaks stick.
     */
    fun sync(context: Context, force: Boolean = false) {
        val repo = ScheduleRepository(context)
        var schedule = repo.load()
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context)
        alarms.cancel(pending)

        if (!schedule.enabled) {
            if (repo.lastAppliedKey != null) ProfileApplier.release(context)
            repo.lastAppliedKey = null
            repo.fadeMinutes = 0
            notifySurfaces(context)
            return
        }

        val now = ZonedDateTime.now()
        val nowMillis = now.toMillis()
        schedule.override?.untilMillis?.let { until ->
            if (until <= nowMillis) {
                schedule = schedule.copy(override = null)
                repo.save(schedule)
            }
        }

        val active = resolve(context, schedule, now)
        val profile = profileFor(active, schedule)
        val changed = active.key != repo.lastAppliedKey
        if (changed) {
            // Leaving a window that asked for a fade, into a profile with sound: ease the volume in.
            val fade = repo.lastFadeMinutes
            if (repo.lastAppliedKey != null && fade > 0 && profile?.ringerMode == RingerMode.NORMAL) {
                repo.fadeStartMillis = nowMillis
                repo.fadeMinutes = fade
            } else {
                repo.fadeMinutes = 0
            }
            repo.lastAppliedKey = active.key
            repo.lastFadeMinutes = (active as? Active.Window)?.occurrence?.window?.fadeMinutes ?: 0
        }

        var fadeProgress: Float? = null
        if (repo.fadeMinutes > 0) {
            val progress = (nowMillis - repo.fadeStartMillis).toFloat() / Duration.ofMinutes(repo.fadeMinutes.toLong()).toMillis()
            if (progress >= 1f) repo.fadeMinutes = 0 else fadeProgress = progress.coerceAtLeast(0.1f)
        }

        if (profile != null && (changed || force || fadeProgress != null)) {
            ProfileApplier.apply(context, profile, fadeProgress ?: 1f)
        }

        val candidates = mutableListOf<Long>()
        active.until?.let { candidates += it.toMillis() }
        schedule.occurrencesAround(now)
            .flatMap { listOf(it.begin, it.end) }
            .firstOrNull { it.isAfter(now) }
            ?.let { candidates += it.toMillis() }
        calendarEvents(context, schedule, now)
            .flatMap { listOf(it.beginMillis, it.endMillis) }
            .filter { it > nowMillis }
            .minOrNull()
            ?.let { candidates += it }
        // Calendars change without telling us, so look again every so often.
        if (schedule.calendar.enabled) candidates += nowMillis + Duration.ofMinutes(CALENDAR_POLL_MINUTES).toMillis()
        if (fadeProgress != null) {
            candidates += nowMillis + (repo.fadeMinutes * 60_000L / 8).coerceAtLeast(30_000L)
        }

        candidates.minOrNull()?.let { next ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
            }
        }
        notifySurfaces(context)
    }

    private fun calendarEvents(context: Context, schedule: SoundSchedule, now: ZonedDateTime): List<CalendarSource.Event> =
        if (!schedule.calendar.enabled) emptyList()
        else CalendarSource.busyEvents(context, now.minusDays(1).toMillis(), now.plusDays(2).toMillis())

    /** Refreshes the home-screen widget and Quick Settings tile. */
    private fun notifySurfaces(context: Context) {
        ScheduleWidget.updateAll(context)
        // Throws if the tile is switched off in Settings; nothing to refresh then.
        runCatching { TileService.requestListeningState(context, ComponentName(context, ScheduleTileService::class.java)) }
    }

    private fun ZonedDateTime.toMillis() = toInstant().toEpochMilli()

    private fun at(millis: Long, now: ZonedDateTime): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(now.zone)

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ScheduleAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
