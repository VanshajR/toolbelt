package com.utilityapp.soundschedule

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.widget.RemoteViews
import com.utilityapp.MainActivity
import com.utilityapp.R
import java.time.Duration
import java.time.ZonedDateTime

/** Home-screen widget: what's active now, today's timeline and a one-tap "Silent 1 h". */
class ScheduleWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateAll(context)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SILENT_HOUR -> SoundScheduler.setOverride(
                context,
                Override(RingerMode.SILENT, System.currentTimeMillis() + Duration.ofHours(1).toMillis()),
            )
            ACTION_RESUME -> SoundScheduler.setOverride(context, null)
            ACTION_ENABLE -> {
                val repo = ScheduleRepository(context)
                repo.save(repo.load().copy(enabled = true))
                SoundScheduler.sync(context, force = true)
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        private const val ACTION_SILENT_HOUR = "com.utilityapp.action.SILENT_HOUR"
        private const val ACTION_RESUME = "com.utilityapp.action.RESUME"
        private const val ACTION_ENABLE = "com.utilityapp.action.ENABLE"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ScheduleWidget::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, render(context))
        }

        private fun render(context: Context): RemoteViews {
            val schedule = ScheduleRepository(context).load()
            val now = ZonedDateTime.now()
            val views = RemoteViews(context.packageName, R.layout.widget_schedule)

            val (mode, headline, detail, action) = if (!schedule.enabled) {
                Quad(null, "Schedule off", "Tap to turn it on", "Turn on" to ACTION_ENABLE)
            } else {
                val active = SoundScheduler.resolve(context, schedule, now)
                val text = statusText(context, active, schedule, now)
                val button = if (active is Active.Manual || active is Active.Paused) "Resume" to ACTION_RESUME
                else "Silent 1 h" to ACTION_SILENT_HOUR
                Quad(text.mode, text.headline, text.detail, button)
            }

            val background = ModeColors.background(mode)
            val content = ModeColors.content(mode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorStateList(R.id.widget_root, "setBackgroundTintList", ColorStateList.valueOf(background))
            } else {
                views.setInt(R.id.widget_root, "setBackgroundColor", background)
            }
            views.setImageViewResource(R.id.widget_icon, mode.icon())
            views.setInt(R.id.widget_icon, "setColorFilter", content)
            views.setTextViewText(R.id.widget_headline, headline)
            views.setTextColor(R.id.widget_headline, content)
            views.setTextViewText(R.id.widget_detail, detail)
            views.setTextColor(R.id.widget_detail, content)
            views.setTextViewText(R.id.widget_action, action.first)
            views.setImageViewBitmap(R.id.widget_timeline, timelineBitmap(schedule, now))

            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
            views.setOnClickPendingIntent(
                R.id.widget_action,
                PendingIntent.getBroadcast(
                    context,
                    1,
                    Intent(context, ScheduleWidget::class.java).setAction(action.second),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            return views
        }

        private fun timelineBitmap(schedule: SoundSchedule, now: ZonedDateTime): Bitmap {
            val width = 600f
            val height = 20f
            val bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, width, height), height / 2, height / 2, Path.Direction.CW) })
            paint.color = ModeColors.background(schedule.defaultProfile.ringerMode)
            canvas.drawRect(0f, 0f, width, height, paint)
            daySegments(schedule, now.toLocalDate(), now).forEach {
                paint.color = ModeColors.background(it.mode)
                canvas.drawRect(it.start * width, 0f, it.end * width, height, paint)
            }
            paint.color = ModeColors.INK
            val x = dayFraction(now) * width
            canvas.drawRect(x - 2f, 0f, x + 2f, height, paint)
            return bitmap
        }

        private data class Quad(val mode: RingerMode?, val headline: String, val detail: String, val action: Pair<String, String>)
    }
}
