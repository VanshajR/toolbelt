package com.utilityapp

import androidx.compose.runtime.Composable
import com.utilityapp.soundschedule.SoundScheduleHomeTile
import com.utilityapp.soundschedule.SoundScheduleScreen

data class Utility(
    val id: String,
    val title: String,
    /** The card body on the home screen: a live summary of the utility. */
    val tile: @Composable () -> Unit,
    /** The utility's own screen. */
    val content: @Composable (onBack: () -> Unit) -> Unit,
)

/** Every tool in Toolbelt. Add an entry here to put a new utility on the home screen. */
val utilities = listOf(
    Utility(
        id = "sound_schedule",
        title = "Sound schedule",
        tile = { SoundScheduleHomeTile() },
        content = { onBack -> SoundScheduleScreen(onBack) },
    ),
)
