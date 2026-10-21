package com.utilityapp.soundschedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fired by the alarm at each boundary, calendar poll or fade step. */
class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = SoundScheduler.sync(context)
}

/** Reboot, clock/timezone change, app update or exact-alarm permission change: re-apply and re-arm. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = SoundScheduler.sync(context, force = true)
}
