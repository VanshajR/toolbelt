package com.utilityapp.soundschedule

import android.app.NotificationManager
import android.app.NotificationManager.Policy
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlin.math.roundToInt

object ProfileApplier {
    private const val TAG = "ProfileApplier"

    /** Applies [profile]; [volumeScale] (0..1) shrinks ring and notification volumes during a fade-in. */
    fun apply(context: Context, profile: SoundProfile, volumeScale: Float = 1f) {
        val audio = context.getSystemService(AudioManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        val repo = ScheduleRepository(context)

        when (profile.ringerMode) {
            RingerMode.SILENT -> enterSilent(audio, notifications, repo, profile.letThrough)
            RingerMode.VIBRATE -> {
                restoreDnd(notifications, repo)
                setRingerMode(audio, AudioManager.RINGER_MODE_VIBRATE)
            }
            RingerMode.NORMAL -> {
                restoreDnd(notifications, repo)
                setRingerMode(audio, AudioManager.RINGER_MODE_NORMAL)
                profile.ringVolume?.let { setVolume(audio, AudioManager.STREAM_RING, (it * volumeScale).roundToInt()) }
                profile.notificationVolume?.let {
                    setVolume(audio, AudioManager.STREAM_NOTIFICATION, (it * volumeScale).roundToInt())
                }
            }
        }
        profile.mediaVolume?.let { setVolume(audio, AudioManager.STREAM_MUSIC, it) }
        profile.alarmVolume?.let { setVolume(audio, AudioManager.STREAM_ALARM, it) }
    }

    /** Called when the schedule is switched off: undo the Do Not Disturb change this app made, if any. */
    fun release(context: Context) {
        restoreDnd(context.getSystemService(NotificationManager::class.java), ScheduleRepository(context))
    }

    /** Maps a percentage onto the stream's discrete steps, keeping any non-zero percentage audible. */
    fun percentToIndex(percent: Int, max: Int): Int =
        if (percent <= 0) 0 else (percent * max / 100.0).roundToInt().coerceIn(1, max)

    // Silent is Do Not Disturb in priority mode, so starred contacts, repeat callers and alarms can
    // still ring. Without DND access the best Android allows is plain silent mode.
    private fun enterSilent(audio: AudioManager, notifications: NotificationManager, repo: ScheduleRepository, letThrough: LetThrough) {
        if (!notifications.isNotificationPolicyAccessGranted) {
            setRingerMode(audio, AudioManager.RINGER_MODE_SILENT)
            return
        }
        if (repo.savedDnd == null) {
            val current = notifications.notificationPolicy
            repo.savedDnd = ScheduleRepository.SavedDnd(
                filter = notifications.currentInterruptionFilter,
                categories = current.priorityCategories,
                callSenders = current.priorityCallSenders,
                messageSenders = current.priorityMessageSenders,
                visualEffects = current.suppressedVisualEffects,
            )
        }
        var categories = 0
        if (letThrough.starredContacts) {
            categories = categories or Policy.PRIORITY_CATEGORY_CALLS or Policy.PRIORITY_CATEGORY_MESSAGES
        }
        if (letThrough.repeatCallers) categories = categories or Policy.PRIORITY_CATEGORY_REPEAT_CALLERS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Media volume is managed separately by the profile, so don't let DND mute it.
            categories = categories or Policy.PRIORITY_CATEGORY_MEDIA
            if (letThrough.alarms) categories = categories or Policy.PRIORITY_CATEGORY_ALARMS
        }
        try {
            notifications.notificationPolicy = Policy(
                categories,
                Policy.PRIORITY_SENDERS_STARRED,
                Policy.PRIORITY_SENDERS_STARRED,
                notifications.notificationPolicy.suppressedVisualEffects,
            )
            notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            // Calls that are let through should actually ring.
            setRingerMode(audio, AudioManager.RINGER_MODE_NORMAL)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not set Do Not Disturb policy", e)
        }
    }

    /** Puts back the user's own Do Not Disturb setup if this app changed it. */
    private fun restoreDnd(notifications: NotificationManager, repo: ScheduleRepository) {
        val saved = repo.savedDnd ?: return
        if (notifications.isNotificationPolicyAccessGranted) {
            try {
                notifications.notificationPolicy =
                    Policy(saved.categories, saved.callSenders, saved.messageSenders, saved.visualEffects)
                notifications.setInterruptionFilter(saved.filter)
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not restore Do Not Disturb", e)
            }
        }
        repo.savedDnd = null
    }

    private fun setRingerMode(audio: AudioManager, mode: Int) {
        try {
            audio.ringerMode = mode
        } catch (e: SecurityException) {
            Log.w(TAG, "No Do Not Disturb access; cannot set ringer mode $mode", e)
        }
    }

    private fun setVolume(audio: AudioManager, stream: Int, percent: Int) {
        val index = percentToIndex(percent, audio.getStreamMaxVolume(stream))
        try {
            audio.setStreamVolume(stream, index, 0)
        } catch (e: SecurityException) {
            Log.w(TAG, "No Do Not Disturb access; cannot set volume of stream $stream", e)
        }
    }
}
