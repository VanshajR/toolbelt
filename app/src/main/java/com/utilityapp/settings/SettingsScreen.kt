package com.utilityapp.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.utilityapp.soundschedule.ScheduleRepository
import com.utilityapp.soundschedule.ScheduleTileService
import com.utilityapp.soundschedule.SoundScheduler
import com.utilityapp.ui.RowDivider
import com.utilityapp.update.UpdateSection
import com.utilityapp.ui.ChoiceRow
import com.utilityapp.ui.ListRow
import com.utilityapp.ui.LocalDarkTheme
import com.utilityapp.ui.Panel
import com.utilityapp.ui.ScreenHeader
import com.utilityapp.ui.SectionLabel
import com.utilityapp.ui.ToolSwitch
import com.utilityapp.ui.accent

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(settings: AppSettings, onChange: (AppSettings) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    var tileEnabled by remember { mutableStateOf(ScheduleTileService.isEnabled(context)) }
    val scheduleRepo = remember { ScheduleRepository(context) }
    var strictTiming by remember { mutableStateOf(scheduleRepo.load().strictTiming) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("Settings", onBack = onBack)

        SectionLabel("Appearance")
        ChoiceRow(
            options = ThemeMode.entries,
            selected = settings.themeMode,
            onSelect = { onChange(settings.copy(themeMode = it)) },
            label = { it.label },
            height = 48,
        )

        SectionLabel("Colour", Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Palette.available.forEach { palette ->
                val swatch = when {
                    palette == Palette.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                        (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
                    else -> palette.accent().let { if (dark) it.second else it.first }
                }
                Swatch(palette.label, swatch, selected = palette == settings.palette) {
                    onChange(settings.copy(palette = palette))
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Text(
                "Wallpaper uses the colours Android picks from your wallpaper (Material You).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionLabel("Sound schedule", Modifier.padding(top = 8.dp))
        Panel {
            ListRow(
                title = "Quick Settings tile",
                subtitle = if (tileEnabled) "Add it from the Quick Settings edit panel" else "Pause or resume from the pull-down panel",
                trailing = {
                    ToolSwitch(tileEnabled, onCheckedChange = {
                        ScheduleTileService.setEnabled(context, it)
                        tileEnabled = it
                    })
                },
            )
            RowDivider(inset = 16)
            ListRow(
                title = "Strict timing",
                subtitle = "Never delayed, even in deep sleep. Shows an alarm icon and your lock screen's \"next alarm\" will show the next change.",
                trailing = {
                    ToolSwitch(strictTiming, {
                        strictTiming = it
                        scheduleRepo.save(scheduleRepo.load().copy(strictTiming = it))
                        SoundScheduler.sync(context)
                    })
                },
            )
        }

        SectionLabel("Updates", Modifier.padding(top = 8.dp))
        UpdateSection()
    }
}

@Composable
private fun Swatch(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            selected = selected,
            onClick = onClick,
            shape = CircleShape,
            color = Color.Transparent,
            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
            modifier = Modifier
                .size(52.dp)
                .semantics { contentDescription = "$label colour" },
        ) {
            Box(Modifier.padding(5.dp)) {
                Surface(shape = CircleShape, color = color, modifier = Modifier.fillMaxSize()) {}
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal))
    }
}
