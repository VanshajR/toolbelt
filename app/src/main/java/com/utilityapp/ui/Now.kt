package com.utilityapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

/** The current time, refreshed on every minute and whenever the screen comes back into view. */
@Composable
fun rememberNow(): ZonedDateTime {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    var resumes by remember { mutableStateOf(0) }
    LifecycleResumeEffect(Unit) {
        now = ZonedDateTime.now()
        resumes++
        onPauseOrDispose { }
    }
    LaunchedEffect(resumes) {
        while (true) {
            delay((60 - ZonedDateTime.now().second) * 1000L)
            now = ZonedDateTime.now()
        }
    }
    return now
}
