package com.utilityapp.soundschedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.utilityapp.R
import com.utilityapp.ui.ChoiceRow
import com.utilityapp.ui.LocalDarkTheme
import com.utilityapp.ui.SectionLabel
import java.time.ZonedDateTime

private data class Choice(val label: String, val detail: String?, val untilMillis: Long?)

/** Bottom sheet for taking over from the schedule for a while. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverrideSheet(active: Active, onStart: (Override) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    val now = remember { ZonedDateTime.now() }
    val choices = remember(active) {
        fun after(minutes: Long) = now.plusMinutes(minutes).toInstant().toEpochMilli()
        buildList {
            add(Choice("30 minutes", null, after(30)))
            add(Choice("1 hour", null, after(60)))
            add(Choice("2 hours", null, after(120)))
            active.until?.let { until ->
                val what = when (active) {
                    is Active.Window -> "When ${active.occurrence.window.name} ends"
                    is Active.Meeting -> "When ${active.title.ifBlank { "the event" }} ends"
                    else -> "At the next scheduled change"
                }
                add(Choice("Until ${formatMoment(context, until, now)}", what, until.toInstant().toEpochMilli()))
            }
            add(Choice("Until I turn it off", null, null))
        }
    }
    var mode by remember { mutableStateOf(RingerMode.SILENT) }
    var choice by remember { mutableStateOf(choices[1]) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Quick override", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                Text(
                    "Takes over from the schedule, then hands back.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ChoiceRow(
                options = listOf(RingerMode.NORMAL, RingerMode.VIBRATE, RingerMode.SILENT),
                selected = mode,
                onSelect = { mode = it },
                label = { it.label },
                icon = { it.icon() },
                selectedColor = { Color(ModeColors.background(it, dark)) to Color(ModeColors.content(it, dark)) },
            )

            Column(Modifier.selectableGroup()) {
                SectionLabel("For how long", Modifier.padding(bottom = 4.dp))
                choices.forEachIndexed { i, c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .selectable(selected = c == choice, onClick = { choice = c }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = c == choice, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(c.label, style = MaterialTheme.typography.bodyLarge)
                            c.detail?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (i < choices.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { onStart(Override(mode, choice.untilMillis)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    val verb = when (mode) {
                        RingerMode.SILENT -> "Go silent"
                        RingerMode.VIBRATE -> "Vibrate"
                        RingerMode.NORMAL -> "Sound on"
                    }
                    val howLong = if (choice.label.startsWith("Until")) choice.label.replaceFirstChar { it.lowercase() } else "for ${choice.label}"
                    Text("$verb $howLong", style = MaterialTheme.typography.titleSmall)
                }
                TextButton(
                    onClick = { onStart(Override(null, choice.untilMillis)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_pause), null, Modifier.size(18.dp))
                    Text("  Pause the schedule instead", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
