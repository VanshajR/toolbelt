package com.utilityapp.soundschedule

import android.media.AudioManager
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.utilityapp.R
import com.utilityapp.ui.ChoiceRow
import com.utilityapp.ui.ListRow
import com.utilityapp.ui.LocalDarkTheme
import com.utilityapp.ui.Motion
import com.utilityapp.ui.Panel
import com.utilityapp.ui.RowDivider
import com.utilityapp.ui.ScreenHeader
import com.utilityapp.ui.SectionLabel
import com.utilityapp.ui.ToolSwitch
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Full-screen editor for one time window. */
@Composable
fun WindowEditorScreen(
    initial: TimeWindow,
    others: List<TimeWindow>,
    isNew: Boolean,
    onSave: (TimeWindow) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var picking by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onClose)

    val problem = when {
        draft.start == draft.end -> "Start and end can't be the same time."
        draft.days.isEmpty() -> "Pick at least one day."
        !draft.enabled -> null
        else -> others.firstOrNull { it.enabled && it.overlaps(draft) }?.let {
            "Overlaps with \"${it.name}\" (${formatMinutes(context, it.start)} – ${formatMinutes(context, it.end)}, " +
                "${daysSummary(it.days).lowercase()})."
        }
    }

    EditorScaffold(
        title = if (isNew) "New window" else "Edit window",
        canSave = problem == null,
        onSave = { onSave(draft.copy(name = draft.name.trim().ifEmpty { "Untitled" })) },
        onClose = onClose,
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { draft = draft.copy(name = it.take(30)) },
            label = { Text("Name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeBlock("From", draft.start, Modifier.weight(1f)) { picking = "start" }
                TimeBlock("To", draft.end, Modifier.weight(1f)) { picking = "end" }
            }
            if (problem != null) {
                Text(problem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            } else {
                val hours = draft.lengthMinutes / 60
                val minutes = draft.lengthMinutes % 60
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (draft.isOvernight) {
                        Icon(painterResource(R.drawable.ic_moon), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        (if (draft.isOvernight) "Ends the next morning · " else "") + "$hours h" + (if (minutes > 0) " $minutes min" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SectionLabel("Repeats on")
        DayPicker(draft.days) { draft = draft.copy(days = it) }
        val everyDay = DayOfWeek.entries.toSet()
        val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        ChoiceRow(
            options = listOf(everyDay, everyDay - weekend, weekend),
            selected = draft.days,
            onSelect = { draft = draft.copy(days = it) },
            label = { daysSummary(it) },
            height = 44,
        )

        ProfileFields(draft.profile) { draft = draft.copy(profile = it) }

        SectionLabel("When it ends")
        Panel(padding = true) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(painterResource(R.drawable.ic_sunrise), null, Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text("Fade sound back in", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        "Raise the volume gradually instead of all at once",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ToolSwitch(draft.fadeMinutes > 0, { draft = draft.copy(fadeMinutes = if (it) 5 else 0) })
            }
            if (draft.fadeMinutes > 0) {
                ChoiceRow(
                    options = listOf(2, 5, 10),
                    selected = draft.fadeMinutes,
                    onSelect = { draft = draft.copy(fadeMinutes = it) },
                    label = { "$it min" },
                    height = 44,
                )
            }
        }

        if (!isNew) {
            SkipRow(draft) { draft = it }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Delete window", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
            }
        }
    }

    picking?.let { which ->
        TimeDialog(
            initialMinutes = if (which == "start") draft.start else draft.end,
            onConfirm = {
                draft = if (which == "start") draft.copy(start = it) else draft.copy(end = it)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

/** Editor for the Default profile: just the sound settings. */
@Composable
fun DefaultEditorScreen(profile: SoundProfile, onSave: (SoundProfile) -> Unit, onClose: () -> Unit) {
    var draft by remember { mutableStateOf(profile) }
    BackHandler(onBack = onClose)
    EditorScaffold(title = "Default", canSave = true, onSave = { onSave(draft) }, onClose = onClose) {
        Text(
            "Used whenever no time window, calendar event or override applies.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProfileFields(draft) { draft = it }
    }
}

@Composable
private fun EditorScaffold(
    title: String,
    canSave: Boolean,
    onSave: () -> Unit,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenHeader(title, onBack = onClose, backIcon = R.drawable.ic_close) {
            Button(onClick = onSave, enabled = canSave, modifier = Modifier.height(44.dp)) { Text("Save") }
        }
        content()
    }
}

/** Mode, what gets through while silent, and volumes. */
@Composable
private fun ProfileFields(profile: SoundProfile, onChange: (SoundProfile) -> Unit) {
    val dark = LocalDarkTheme.current
    SectionLabel("Mode")
    ChoiceRow(
        options = listOf(RingerMode.NORMAL, RingerMode.VIBRATE, RingerMode.SILENT),
        selected = profile.ringerMode,
        onSelect = { onChange(profile.copy(ringerMode = it)) },
        label = { it.label },
        icon = { it.icon() },
        selectedColor = { Color(ModeColors.background(it, dark)) to Color(ModeColors.content(it, dark)) },
        height = 64,
    )

    AnimatedVisibility(
        visible = profile.ringerMode == RingerMode.SILENT,
        enter = fadeIn(Motion.fade()) + expandVertically(Motion.fade()),
        exit = fadeOut(Motion.fade()) + shrinkVertically(Motion.fade()),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SectionLabel("Still let through")
            Panel {
                val lt = profile.letThrough
                ListRow(
                    title = "Starred contacts",
                    subtitle = "Calls and messages ring as normal",
                    leading = { Icon(painterResource(R.drawable.ic_star), null, Modifier.size(22.dp)) },
                    trailing = { ToolSwitch(lt.starredContacts, { onChange(profile.copy(letThrough = lt.copy(starredContacts = it))) }) },
                )
                RowDivider(inset = 52)
                ListRow(
                    title = "Repeat callers",
                    subtitle = "Same number twice within 15 minutes",
                    leading = { Icon(painterResource(R.drawable.ic_repeat), null, Modifier.size(22.dp)) },
                    trailing = { ToolSwitch(lt.repeatCallers, { onChange(profile.copy(letThrough = lt.copy(repeatCallers = it))) }) },
                )
                RowDivider(inset = 52)
                ListRow(
                    title = "Alarms",
                    subtitle = "Your alarm clock always rings",
                    leading = { Icon(painterResource(R.drawable.ic_alarm), null, Modifier.size(22.dp)) },
                    trailing = { ToolSwitch(lt.alarms, { onChange(profile.copy(letThrough = lt.copy(alarms = it))) }) },
                )
            }
        }
    }

    SectionLabel("Volumes")
    Panel(padding = true) {
        if (profile.ringerMode == RingerMode.NORMAL) {
            VolumeRow(R.drawable.ic_sound, "Ring", AudioManager.STREAM_RING, profile.ringVolume) { onChange(profile.copy(ringVolume = it)) }
            VolumeRow(R.drawable.ic_sound, "Notifications", AudioManager.STREAM_NOTIFICATION, profile.notificationVolume) {
                onChange(profile.copy(notificationVolume = it))
            }
        }
        VolumeRow(R.drawable.ic_alarm, "Alarm", AudioManager.STREAM_ALARM, profile.alarmVolume) { onChange(profile.copy(alarmVolume = it)) }
        VolumeRow(R.drawable.ic_music, "Media", AudioManager.STREAM_MUSIC, profile.mediaVolume) { onChange(profile.copy(mediaVolume = it)) }
        if (profile.ringerMode != RingerMode.NORMAL) {
            Text(
                "Ring and notification volumes appear when the mode is Sound.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A volume the profile may control. Switching it off leaves that stream untouched. */
@Composable
private fun VolumeRow(icon: Int, label: String, stream: Int, value: Int?, onChange: (Int?) -> Unit) {
    val context = LocalContext.current
    val max = remember(stream) { context.getSystemService(AudioManager::class.java).getStreamMaxVolume(stream) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging?.roundToInt() ?: value

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(painterResource(icon), null, Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text("$label volume", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    if (shown == null) "Not changed" else "$shown% · level ${ProfileApplier.percentToIndex(shown, max)} of $max",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ToolSwitch(value != null, { onChange(if (it) 50 else null) })
        }
        if (value != null) {
            Slider(
                value = dragging ?: value.toFloat(),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { onChange(it.roundToInt()) }
                    dragging = null
                },
                valueRange = 0f..100f,
                steps = 19,
                modifier = Modifier.semantics { contentDescription = "$label volume" },
            )
        }
    }
}

@Composable
private fun TimeBlock(label: String, minutes: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMinutes(context, minutes), style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 34.sp))
        }
    }
}

@Composable
private fun DayPicker(days: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { day ->
            val selected = day in days
            val fullName = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
            val fill by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface, Motion.fade(), label = "day",
            )
            val ink by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface, Motion.fade(), label = "dayText",
            )
            val ring by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline, Motion.fade(), label = "dayRing",
            )
            Surface(
                checked = selected,
                onCheckedChange = { onChange(if (it) days + day else days - day) },
                shape = CircleShape,
                color = fill,
                contentColor = ink,
                border = BorderStroke(1.dp, ring),
                modifier = Modifier
                    .size(44.dp)
                    .semantics { contentDescription = fullName },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun SkipRow(window: TimeWindow, onChange: (TimeWindow) -> Unit) {
    val context = LocalContext.current
    val now = ZonedDateTime.now()
    val next = remember(window.start, window.end, window.days) { window.nextOccurrence(now) } ?: return
    val skipping = window.skipDate != null && !window.skipDate.isBefore(now.toLocalDate().minusDays(1))
    Panel {
        ListRow(
            title = if (skipping) "Skipping the next one" else "Skip the next one",
            subtitle = "${formatMoment(context, next.begin, now)} – ${formatMinutes(context, window.end)}",
            leading = { Icon(painterResource(R.drawable.ic_skip), null, Modifier.size(22.dp)) },
            trailing = {
                OutlinedButton(onClick = {
                    onChange(window.copy(skipDate = if (skipping) null else next.begin.toLocalDate()))
                }) { Text(if (skipping) "Undo" else "Skip") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initialMinutes: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(initialMinutes / 60, initialMinutes % 60, DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state) },
    )
}

fun daysSummary(days: Set<DayOfWeek>): String {
    val all = DayOfWeek.entries.toSet()
    val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    return when (days) {
        all -> "Every day"
        all - weekend -> "Weekdays"
        weekend -> "Weekends"
        else -> DayOfWeek.entries.filter { it in days }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }
}
