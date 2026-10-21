package com.utilityapp.soundschedule

import android.content.Context
import android.content.SharedPreferences
import java.time.DayOfWeek
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

class ScheduleRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("sound_schedule", Context.MODE_PRIVATE)

    fun load(): SoundSchedule {
        val json = prefs.getString(KEY_SCHEDULE, null) ?: return SoundSchedule()
        return runCatching { scheduleFrom(JSONObject(json)) }.getOrElse { SoundSchedule() }
    }

    fun save(schedule: SoundSchedule) {
        prefs.edit().putString(KEY_SCHEDULE, toJson(schedule).toString()).apply()
    }

    // Runtime state the scheduler keeps between runs.

    /** Identifies what was last applied, so unchanged state isn't re-applied over manual tweaks. */
    var lastAppliedKey: String?
        get() = prefs.getString("last_key", null)
        set(value) = prefs.edit().putString("last_key", value).apply()

    /** Fade minutes of the window that was last applied (0 if none). */
    var lastFadeMinutes: Int
        get() = prefs.getInt("last_fade", 0)
        set(value) = prefs.edit().putInt("last_fade", value).apply()

    /** Fade in progress: start time and length. */
    var fadeStartMillis: Long
        get() = prefs.getLong("fade_start", 0)
        set(value) = prefs.edit().putLong("fade_start", value).apply()

    var fadeMinutes: Int
        get() = prefs.getInt("fade_minutes", 0)
        set(value) = prefs.edit().putInt("fade_minutes", value).apply()

    /** The user's Do Not Disturb setup from before this app turned it on, or null if the app hasn't. */
    var savedDnd: SavedDnd?
        get() = prefs.getString("saved_dnd", null)?.let { raw ->
            runCatching {
                val o = JSONObject(raw)
                SavedDnd(o.getInt("filter"), o.getInt("categories"), o.getInt("calls"), o.getInt("messages"), o.getInt("visual"))
            }.getOrNull()
        }
        set(value) = prefs.edit().putString(
            "saved_dnd",
            value?.let {
                JSONObject().put("filter", it.filter).put("categories", it.categories).put("calls", it.callSenders)
                    .put("messages", it.messageSenders).put("visual", it.visualEffects).toString()
            },
        ).apply()

    data class SavedDnd(val filter: Int, val categories: Int, val callSenders: Int, val messageSenders: Int, val visualEffects: Int)

    // JSON

    private fun toJson(schedule: SoundSchedule) = JSONObject().apply {
        put("enabled", schedule.enabled)
        put("default", toJson(schedule.defaultProfile))
        put("windows", JSONArray().apply {
            schedule.windows.forEach { window ->
                put(JSONObject().apply {
                    put("id", window.id)
                    put("name", window.name)
                    put("start", window.start)
                    put("end", window.end)
                    put("profile", toJson(window.profile))
                    put("days", JSONArray(window.days.map { it.value }))
                    put("enabled", window.enabled)
                    put("fade", window.fadeMinutes)
                    window.skipDate?.let { put("skip", it.toString()) }
                })
            }
        })
        schedule.override?.let { o ->
            put("override", JSONObject().apply {
                o.mode?.let { put("mode", it.name) }
                o.untilMillis?.let { put("until", it) }
            })
        }
        put("calendar", JSONObject().put("enabled", schedule.calendar.enabled).put("mode", schedule.calendar.mode.name))
    }

    private fun toJson(profile: SoundProfile) = JSONObject().apply {
        put("mode", profile.ringerMode.name)
        profile.ringVolume?.let { put("ring", it) }
        profile.notificationVolume?.let { put("notification", it) }
        profile.mediaVolume?.let { put("media", it) }
        profile.alarmVolume?.let { put("alarm", it) }
        put("starred", profile.letThrough.starredContacts)
        put("repeat", profile.letThrough.repeatCallers)
        put("alarms", profile.letThrough.alarms)
    }

    private fun scheduleFrom(json: JSONObject): SoundSchedule {
        val windows = json.getJSONArray("windows")
        return SoundSchedule(
            enabled = json.getBoolean("enabled"),
            defaultProfile = profileFrom(json.getJSONObject("default")),
            windows = List(windows.length()) { i ->
                val w = windows.getJSONObject(i)
                TimeWindow(
                    id = w.getLong("id"),
                    name = w.getString("name"),
                    start = w.getInt("start"),
                    end = w.getInt("end"),
                    profile = profileFrom(w.getJSONObject("profile")),
                    days = w.optJSONArray("days")
                        ?.let { days -> List(days.length()) { DayOfWeek.of(days.getInt(it)) }.toSet() }
                        ?: DayOfWeek.entries.toSet(),
                    enabled = w.optBoolean("enabled", true),
                    fadeMinutes = w.optInt("fade", 0),
                    skipDate = w.optString("skip").takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                )
            },
            override = json.optJSONObject("override")?.let { o ->
                Override(
                    mode = o.optString("mode").takeIf { it.isNotEmpty() }?.let(RingerMode::valueOf),
                    untilMillis = if (o.has("until")) o.getLong("until") else null,
                )
            },
            calendar = json.optJSONObject("calendar")?.let {
                CalendarMode(it.getBoolean("enabled"), RingerMode.valueOf(it.getString("mode")))
            } ?: CalendarMode(),
        )
    }

    private fun profileFrom(json: JSONObject) = SoundProfile(
        ringerMode = RingerMode.valueOf(json.getString("mode")),
        ringVolume = json.optVolume("ring"),
        notificationVolume = json.optVolume("notification"),
        mediaVolume = json.optVolume("media"),
        alarmVolume = json.optVolume("alarm"),
        letThrough = LetThrough(
            starredContacts = json.optBoolean("starred", true),
            repeatCallers = json.optBoolean("repeat", true),
            alarms = json.optBoolean("alarms", true),
        ),
    )

    private fun JSONObject.optVolume(key: String): Int? = if (has(key)) getInt(key) else null

    private companion object {
        const val KEY_SCHEDULE = "schedule"
    }
}
