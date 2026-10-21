package com.utilityapp.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.utilityapp.BuildConfig
import com.utilityapp.ui.ListRow
import com.utilityapp.ui.Panel
import com.utilityapp.ui.RowDivider
import com.utilityapp.ui.ToolSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Failed(val message: String) : UpdateState
}

@Composable
fun UpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(Updates.known(context)?.let { UpdateState.Available(it) } ?: UpdateState.Idle) }
    var autoCheck by remember { mutableStateOf(Updates.autoCheck(context)) }

    fun checkNow() {
        state = UpdateState.Checking
        scope.launch {
            state = try {
                withContext(Dispatchers.IO) { Updates.check(context) }?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
            } catch (e: Exception) {
                UpdateState.Failed("Couldn't reach GitHub. Check your connection.")
            }
        }
    }

    fun update(release: Release) {
        if (!Updates.canInstall(context)) {
            // Android asks once whether Toolbelt may install apps; come back and tap Update again.
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            return
        }
        state = UpdateState.Downloading(release, 0f)
        scope.launch {
            try {
                val apk = withContext(Dispatchers.IO) {
                    Updates.download(context, release) { p -> state = UpdateState.Downloading(release, p) }
                }
                withContext(Dispatchers.IO) { Updates.install(context, apk) }
            } catch (e: Exception) {
                Updates.log("Update failed", e)
                state = UpdateState.Failed("Download failed. Try again.")
            }
        }
    }

    Panel {
        ListRow(
            title = "Version ${BuildConfig.VERSION_NAME}",
            subtitle = when (val s = state) {
                UpdateState.Idle -> "Tap Check to look for updates"
                UpdateState.Checking -> "Checking…"
                UpdateState.UpToDate -> "You're on the latest version"
                is UpdateState.Available -> "Version ${s.release.version} is available"
                is UpdateState.Downloading -> "Downloading ${s.release.version}…"
                is UpdateState.Failed -> s.message
            },
            trailing = {
                when (val s = state) {
                    is UpdateState.Available -> Button(onClick = { update(s.release) }, modifier = Modifier.height(44.dp)) { Text("Update") }
                    is UpdateState.Downloading, UpdateState.Checking -> Unit
                    else -> OutlinedButton(onClick = ::checkNow, modifier = Modifier.height(44.dp)) { Text("Check") }
                }
            },
        )
        (state as? UpdateState.Downloading)?.let {
            LinearProgressIndicator(progress = { it.progress }, modifier = Modifier.fillMaxWidth().height(4.dp))
        }
        (state as? UpdateState.Available)?.release?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Text(
                notes.lines().take(6).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
        RowDivider(inset = 16)
        ListRow(
            title = "Check automatically",
            subtitle = "Once a day; you'll get a notification",
            trailing = {
                ToolSwitch(autoCheck, {
                    autoCheck = it
                    Updates.setAutoCheck(context, it)
                })
            },
        )
    }
    Text(
        "Updates come from github.com/${BuildConfig.UPDATE_REPO}.",
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
