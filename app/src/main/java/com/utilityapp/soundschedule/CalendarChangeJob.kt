package com.utilityapp.soundschedule

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract

/**
 * Wakes the scheduler only when the calendar actually changes, instead of polling it.
 * Content-triggered jobs fire once, so each run re-arms the next one.
 */
class CalendarChangeJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            SoundScheduler.sync(this)
            jobFinished(params, false)
            // This run counted as pending until now, so arm the next one afterwards.
            val schedule = ScheduleRepository(this).load()
            update(this, schedule.enabled && schedule.calendar.enabled)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters) = false

    companion object {
        private const val JOB_ID = 4202

        /** Watches the calendar while calendar mode is on; stops watching when it's off. */
        fun update(context: Context, watch: Boolean) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (!watch) {
                scheduler.cancel(JOB_ID)
                return
            }
            if (scheduler.getPendingJob(JOB_ID) != null) return
            scheduler.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, CalendarChangeJob::class.java))
                    .addTriggerContentUri(
                        JobInfo.TriggerContentUri(CalendarContract.Events.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS),
                    )
                    // Batch bursts of edits (e.g. a sync) into one run.
                    .setTriggerContentUpdateDelay(5_000)
                    .build(),
            )
        }
    }
}
