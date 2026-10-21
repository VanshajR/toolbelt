package com.utilityapp

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.utilityapp.settings.AppSettings
import com.utilityapp.settings.SettingsScreen
import com.utilityapp.ui.HomeScreen
import com.utilityapp.ui.ToolbeltTheme
import com.utilityapp.update.Updates

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Updates.schedule(this)
        askForNotificationsOnce()
        val startRoute = intent.getStringExtra(Updates.EXTRA_ROUTE) ?: ROUTE_HOME
        setContent { App(startRoute) }
    }

    /** Update notifications need permission on Android 13+; ask on first launch only. */
    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
    }
}

private const val ROUTE_HOME = "home"
private const val ROUTE_SETTINGS = "settings"

@Composable
private fun App(startRoute: String) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(AppSettings.load(context)) }
    var route by rememberSaveable { mutableStateOf(startRoute) }
    val goHome = { route = ROUTE_HOME }

    BackHandler(enabled = route != ROUTE_HOME, onBack = goHome)

    ToolbeltTheme(settings) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                when (route) {
                    ROUTE_HOME -> HomeScreen(
                        onOpen = { route = it.id },
                        onOpenSettings = { route = ROUTE_SETTINGS },
                    )
                    ROUTE_SETTINGS -> SettingsScreen(
                        settings = settings,
                        onChange = {
                            settings = it
                            it.save(context)
                        },
                        onBack = goHome,
                    )
                    else -> {
                        val utility = utilities.firstOrNull { it.id == route }
                        if (utility != null) utility.content(goHome) else LaunchedEffect(Unit) { goHome() }
                    }
                }
            }
        }
    }
}
