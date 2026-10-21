package com.utilityapp.soundschedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.utilityapp.ui.IconCircle
import com.utilityapp.ui.LocalDarkTheme
import com.utilityapp.ui.rememberNow
import java.time.ZonedDateTime

@Composable
fun RingerMode?.background(): Color = Color(ModeColors.background(this, LocalDarkTheme.current))

@Composable
fun RingerMode?.content(): Color = Color(ModeColors.content(this, LocalDarkTheme.current))

/** One day as a coloured bar: windows in their mode colour, gaps in the default profile's colour. */
@Composable
fun DayBar(schedule: SoundSchedule, now: ZonedDateTime, date: java.time.LocalDate, height: Dp, modifier: Modifier = Modifier) {
    val base = schedule.defaultProfile.ringerMode.background()
    val segments = remember(schedule, date) { daySegments(schedule, date, now) }
    val colors = RingerMode.entries.associateWith { it.background() }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 3)),
    ) {
        drawRect(base)
        segments.forEach {
            drawRect(colors.getValue(it.mode), topLeft = Offset(it.start * size.width, 0f), size = Size((it.end - it.start) * size.width, size.height))
        }
    }
}

@Composable
fun HourLabels() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("00", "06", "12", "18", "24").forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Today's bar with a marker and label for the current time. */
@Composable
fun TodayTimeline(schedule: SoundSchedule, now: ZonedDateTime) {
    val context = LocalContext.current
    val ink = MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = maxWidth
            val x = width * dayFraction(now)
            Column {
                Box(Modifier.height(18.dp).fillMaxWidth()) {
                    Text(
                        formatMinutes(context, now.hour * 60 + now.minute),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.offset(x = (x - 16.dp).coerceIn(0.dp, width - 36.dp)),
                    )
                }
                Box {
                    DayBar(schedule, now, now.toLocalDate(), height = 34.dp)
                    Canvas(Modifier.fillMaxWidth().height(34.dp)) {
                        drawRect(ink, topLeft = Offset(size.width * dayFraction(now) - 1.5f, 0f), size = Size(3f, size.height))
                    }
                }
            }
        }
        HourLabels()
    }
}

/** Monday to Sunday of the current week, today outlined. */
@Composable
fun WeekStrip(schedule: SoundSchedule, now: ZonedDateTime) {
    val monday = now.toLocalDate().minusDays(now.dayOfWeek.value - 1L)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (0L..6L).map { monday.plusDays(it) }.forEach { date ->
            val today = date == now.toLocalDate()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()),
                    modifier = Modifier.width(22.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (today) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DayBar(schedule, now, date, height = if (today) 14.dp else 10.dp, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** The Sound schedule card on the home screen. */
@Composable
fun SoundScheduleHomeTile() {
    val context = LocalContext.current
    val now = rememberNow()
    var schedule by remember { mutableStateOf(ScheduleRepository(context).load()) }
    LifecycleResumeEffect(Unit) {
        schedule = ScheduleRepository(context).load()
        onPauseOrDispose { }
    }
    val text = remember(schedule, now) {
        if (!schedule.enabled) StatusText(null, "Off", "Tap to set it up")
        else statusText(context, SoundScheduler.resolve(context, schedule, now), schedule, now)
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconCircle(text.mode.icon(), text.mode.background(), text.mode.content(), size = 48)
            Column(Modifier.weight(1f)) {
                Text("Sound schedule", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    if (schedule.enabled) "${text.headline} · ${text.detail}" else text.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(painterResource(com.utilityapp.R.drawable.ic_chevron), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        if (schedule.enabled) {
            Column(Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box {
                    DayBar(schedule, now, now.toLocalDate(), height = 12.dp)
                    val marker = MaterialTheme.colorScheme.surface
                    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                        drawRect(marker, topLeft = Offset(size.width * dayFraction(now) - 1.5f, 0f), size = Size(3f, size.height))
                    }
                }
                HourLabels()
            }
        }
    }
}
