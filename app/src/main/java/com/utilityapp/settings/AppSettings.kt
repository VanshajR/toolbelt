package com.utilityapp.settings

import android.content.Context
import android.os.Build

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class Palette(val label: String) {
    WALLPAPER("Wallpaper"),
    INDIGO("Indigo"),
    TEAL("Teal"),
    ROSE("Rose"),
    AMBER("Amber"),
    GRAPHITE("Graphite");

    companion object {
        /** Wallpaper colours (Material You) exist on Android 12 and later. */
        val available: List<Palette>
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) entries else entries - WALLPAPER
    }
}

/** App-wide preferences that aren't tied to one utility. */
data class AppSettings(val themeMode: ThemeMode = ThemeMode.SYSTEM, val palette: Palette = Palette.INDIGO) {

    fun save(context: Context) {
        prefs(context).edit().putString("theme_mode", themeMode.name).putString("palette", palette.name).apply()
    }

    companion object {
        fun load(context: Context): AppSettings {
            val prefs = prefs(context)
            return AppSettings(
                themeMode = prefs.getString("theme_mode", null)
                    ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
                palette = prefs.getString("palette", null)
                    ?.let { runCatching { Palette.valueOf(it) }.getOrNull() }
                    ?.takeIf { it in Palette.available } ?: Palette.INDIGO,
            )
        }

        private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    }
}
