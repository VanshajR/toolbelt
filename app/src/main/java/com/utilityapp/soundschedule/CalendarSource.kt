package com.utilityapp.soundschedule

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Instances
import android.util.Log

/** Reads busy, timed events (not all-day ones) from the device calendars. */
object CalendarSource {
    data class Event(val title: String, val beginMillis: Long, val endMillis: Long)

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun busyEvents(context: Context, fromMillis: Long, toMillis: Long): List<Event> {
        if (!hasPermission(context)) return emptyList()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, fromMillis)
            ContentUris.appendId(it, toMillis)
        }.build()
        val projection = arrayOf(Instances.TITLE, Instances.BEGIN, Instances.END)
        val selection = "${Instances.AVAILABILITY} = ${Instances.AVAILABILITY_BUSY} AND ${Instances.ALL_DAY} = 0"
        return try {
            context.contentResolver.query(uri, projection, selection, null, "${Instances.BEGIN} ASC")?.use { c ->
                buildList {
                    while (c.moveToNext()) add(Event(c.getString(0).orEmpty(), c.getLong(1), c.getLong(2)))
                }
            }.orEmpty()
        } catch (e: SecurityException) {
            Log.w("CalendarSource", "Calendar permission revoked", e)
            emptyList()
        }
    }
}
