package com.utilityapp.soundschedule

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.utilityapp.R
import com.utilityapp.ui.Badge
import com.utilityapp.ui.ChoiceRow
import com.utilityapp.ui.IconCircle
import com.utilityapp.ui.ListRow
import com.utilityapp.ui.LocalDarkTheme
import com.utilityapp.ui.Panel
import com.utilityapp.ui.RowDivider
import com.utilityapp.ui.ScreenHeader
import com.utilityapp.ui.SectionLabel
import com.utilityapp.ui.SectionTitle
import com.utilityapp.ui.ToolSwitch
import com.utilityapp.ui.rememberNow
import java.time.ZonedDateTime

private sealed interface Editing {
    data class Window(val window: TimeWindow, val isNew: Boolean) : Editing
    data object Default : Editing
}

@Composable
fun SoundScheduleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ScheduleRepository(context) }
    var schedule by remember { mutableStateOf(repo.load()) }
    var permissions by remember { mutableStateOf(PermissionState.read(context)) }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var showOverride by remember { mutableStateOf(false) }
    val now = rememberNow()

    LifecycleResumeEffect(Unit) {
        // The widget or tile may have changed things while we were away.
        schedule = repo.load()
        val fresh = PermissionState.read(context)
        if (fresh != permissions) {
            permissions = fresh
            if (schedule.enabled) SoundScheduler.sync(context, force = true)
        }
        onPauseOrDispose { }
    }

    fun update(new: SoundSchedule) {
        schedule = new
        repo.save(new)
        SoundScheduler.sync(context, force = true)
    }

    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissions = PermissionState.read(context)
        if (granted) update(schedule.copy(calendar = schedule.calendar.copy(enabled = true)))
    }

    when (val e = editing) {
        is Editing.Window -> {
            val others = schedule.windows.filter { it.id != e.window.id }
            WindowEditorScreen(
                initial = e.window,
                others = others,
                isNew = e.isNew,
                onSave = {
                    update(schedule.copy(windows = others + it))
                    editing = null
                },
                onDelete = {
                    update(schedule.copy(windows = others))
                    editing = null
                },
                onClose = { editing = null },
            )
            return
        }
        Editing.Default -> {
            DefaultEditorScreen(
                profile = schedule.defaultProfile,
                onSave = {
                    update(schedule.copy(defaultProfile = it))
                    editing = null
                },
                onClose = { editing = null },
            )
            return
        }
        null -> Unit
    }

    val active = remember(schedule, now) { SoundScheduler.resolve(context, schedule, now) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenHeader("Sound schedule", onBack = onBack)

        Hero(
            schedule = schedule,
            active = active,
            now = now,
            onToggle = { update(schedule.copy(enabled = it, override = null)) },
            onOverride = { showOverride = true },
            onEndOverride = { update(schedule.copy(override = null)) },
        )

        SetupCard(permissions, needsCalendar = schedule.calendar.enabled)

        Panel(padding = true) {
            Text("Today", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            TodayTimeline(schedule, now)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionLabel("This week")
            WeekStrip(schedule, now)
        }

        SectionTitle("Time windows", trailing = "${schedule.windows.size} of $MAX_WINDOWS")
        Panel {
            val activeId = (active as? Active.Window)?.occurrence?.window?.id
            schedule.windows.sortedBy { it.start }.forEachIndexed { i, window ->
                if (i > 0) RowDivider()
                WindowRow(
                    window = window,
                    isNow = schedule.enabled && window.id == activeId,
                    now = now,
                    onClick = { editing = Editing.Window(window, isNew = false) },
                    onToggle = { on ->
                        val clash = if (on) schedule.windows.firstOrNull { it.id != window.id && it.enabled && it.overlaps(window) } else null
                        if (clash != null) {
                            Toast.makeText(context, "Overlaps with \"${clash.name}\". Change its times or days first.", Toast.LENGTH_LONG).show()
                        } else {
                            update(schedule.copy(windows = schedule.windows.map { if (it.id == window.id) it.copy(enabled = on) else it }))
                        }
                    },
                )
            }
            if (schedule.windows.isNotEmpty()) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(
                onClick = { editing = Editing.Window(newWindow(schedule), isNew = true) },
                enabled = schedule.windows.size < MAX_WINDOWS,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(painterResource(R.drawable.ic_plus), null, Modifier.size(18.dp))
                Text(
                    if (schedule.windows.size < MAX_WINDOWS) "  Add time window" else "  Maximum of $MAX_WINDOWS windows",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }

        SectionTitle("Everything else")
        Panel {
            val default = schedule.defaultProfile
            ListRow(
                title = "Default",
                subtitle = default.summary(),
                onClick = { editing = Editing.Default },
                leading = { IconCircle(default.ringerMode.icon(), default.ringerMode.background(), default.ringerMode.content()) },
                trailing = { Icon(painterResource(R.drawable.ic_chevron), null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            RowDivider()
            ListRow(
                title = "Calendar events",
                subtitle = if (schedule.calendar.enabled) "${schedule.calendar.mode.label} during busy events" else "Go quiet during busy events",
                leading = { IconCircle(R.drawable.ic_calendar, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface) },
                trailing = {
                    ToolSwitch(schedule.calendar.enabled, { on ->
                        if (on && !CalendarSource.hasPermission(context)) {
                            calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                        } else {
                            update(schedule.copy(calendar = schedule.calendar.copy(enabled = on)))
                        }
                    })
                },
            )
            if (schedule.calendar.enabled) {
                Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    ChoiceRow(
                        options = listOf(RingerMode.VIBRATE, RingerMode.SILENT),
                        selected = schedule.calendar.mode,
                        onSelect = { update(schedule.copy(calendar = schedule.calendar.copy(mode = it))) },
                        label = { it.label },
                        height = 44,
                    )
                }
            }
        }
    }

    if (showOverride) {
        OverrideSheet(
            active = active,
            onStart = {
                showOverride = false
                update(schedule.copy(override = it))
            },
            onDismiss = { showOverride = false },
        )
    }
}

/** The big coloured card: what's active now, the master switch and the override button. */
@Composable
private fun Hero(
    schedule: SoundSchedule,
    active: Active,
    now: ZonedDateTime,
    onToggle: (Boolean) -> Unit,
    onOverride: () -> Unit,
    onEndOverride: () -> Unit,
) {
    val context = LocalContext.current
    val text = if (schedule.enabled) statusText(context, active, schedule, now) else StatusText(null, "Off", "Your sound settings are left alone")
    val bg = text.mode.background()
    val fg = text.mode.content()
    val overriding = schedule.enabled && (active is Active.Manual || active is Active.Paused)
    val label = when {
        !schedule.enabled -> "Schedule"
        active is Active.Window -> "Now · ${active.occurrence.window.name}"
        active is Active.Meeting -> "Now · Calendar"
        overriding -> "Now · Override"
        else -> "Now · Default"
    }

    Surface(shape = RoundedCornerShape(28.dp), color = bg, contentColor = fg, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
                    modifier = Modifier.weight(1f),
                )
                Text(if (schedule.enabled) "On" else "Off", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 10.dp))
                Switch(
                    checked = schedule.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = fg,
                        checkedThumbColor = bg,
                        uncheckedTrackColor = Color.Transparent,
                        uncheckedBorderColor = fg,
                        uncheckedThumbColor = fg,
                    ),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(text.mode.icon()), null, Modifier.size(36.dp))
                    Text(text.headline, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, fontSize = 40.sp))
                }
                Text(text.detail, style = MaterialTheme.typography.bodyLarge)
            }
            if (schedule.enabled) {
                Button(
                    onClick = if (overriding) onEndOverride else onOverride,
                    colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Icon(painterResource(if (overriding) R.drawable.ic_close else R.drawable.ic_clock), null, Modifier.size(18.dp))
                    Text(if (overriding) "  Back to schedule" else "  Quick override", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun WindowRow(window: TimeWindow, isNow: Boolean, now: ZonedDateTime, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val mode = window.profile.ringerMode
    val skipping = window.skipDate?.takeIf { !it.isBefore(now.toLocalDate().minusDays(1)) }
    ListRow(
        title = "${formatMinutes(context, window.start)} – ${formatMinutes(context, window.end)}",
        subtitle = listOfNotNull(
            window.name,
            daysSummary(window.days),
            skipping?.let { "skipping ${it.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())}" },
            if (!window.enabled) "Off" else null,
        ).joinToString(" · "),
        onClick = onClick,
        dimmed = !window.enabled,
        leading = { IconCircle(mode.icon(), mode.background(), mode.content()) },
        titleBadge = if (isNow) ({ Badge("Now") }) else null,
        trailing = { ToolSwitch(window.enabled, onToggle) },
    )
}

/** Missing permissions, explained calmly with a button each. Hidden when all is well. */
@Composable
private fun SetupCard(permissions: PermissionState, needsCalendar: Boolean) {
    val context = LocalContext.current
    val items = buildList {
        if (!permissions.dndAccess) {
            add(Triple("Do Not Disturb access", "Needed for Silent windows and \"let through\" rules.") {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            })
        }
        if (!permissions.exactAlarms && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Triple("Exact timing", "Without it, Android may run changes a few minutes late.") {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            })
        }
        if (!permissions.batteryExempt) {
            add(Triple("Battery optimisation", "Some phones stop scheduled changes to save battery. Exempting Toolbelt costs almost nothing.") {
                @android.annotation.SuppressLint("BatteryLife")
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            })
        }
        if (needsCalendar && !permissions.calendar) {
            add(Triple("Calendar access", "Needed to go quiet during your events.") {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            })
        }
    }
    if (items.isEmpty()) return

    Panel(padding = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(10.dp).padding(0.dp)) {
                Surface(shape = CircleShape, color = Color(0xFFD98A1F), modifier = Modifier.fillMaxSize()) {}
            }
            Text("Finish setup", style = MaterialTheme.typography.titleSmall)
        }
        items.forEach { (title, body, action) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = action, modifier = Modifier.height(44.dp)) { Text("Allow") }
            }
        }
    }
}

private data class PermissionState(
    val dndAccess: Boolean,
    val exactAlarms: Boolean,
    val calendar: Boolean,
    val batteryExempt: Boolean,
) {
    companion object {
        fun read(context: Context) = PermissionState(
            batteryExempt = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
            dndAccess = context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted,
            exactAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            calendar = CalendarSource.hasPermission(context),
        )
    }
}

private fun newWindow(schedule: SoundSchedule) = TimeWindow(
    id = (schedule.windows.maxOfOrNull { it.id } ?: 0) + 1,
    name = "Window ${schedule.windows.size + 1}",
    start = 12 * 60,
    end = 13 * 60,
    profile = SoundProfile(RingerMode.VIBRATE),
)

fun SoundProfile.summary(): String = buildList {
    add(ringerMode.label)
    if (ringerMode == RingerMode.NORMAL) {
        ringVolume?.let { add("Ring $it%") }
        notificationVolume?.let { add("Notifications $it%") }
    }
    alarmVolume?.let { add("Alarm $it%") }
    mediaVolume?.let { add("Media $it%") }
}.joinToString(" · ")
