package com.utilityapp.soundschedule

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile: tap to pause or resume the schedule. It's off by default; the user turns
 * it on in Toolbelt's settings and then adds it from the Quick Settings edit panel.
 */
class ScheduleTileService : TileService() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        val repo = ScheduleRepository(this)
        val schedule = repo.load()
        when {
            !schedule.enabled -> {
                repo.save(schedule.copy(enabled = true, override = null))
                SoundScheduler.sync(this, force = true)
            }
            schedule.override?.mode == null && schedule.override != null -> SoundScheduler.setOverride(this, null)
            else -> SoundScheduler.setOverride(this, Override(mode = null, untilMillis = null))
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val schedule = ScheduleRepository(this).load()
        val active = if (schedule.enabled) SoundScheduler.resolve(this, schedule) else null
        val running = active != null && active !is Active.Paused
        val text = active?.let { statusText(this, it, schedule) }

        tile.label = "Schedule"
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, (if (running) text?.mode else null).icon())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                active == null -> "Off"
                active is Active.Paused -> "Paused"
                else -> text?.headline
            }
        }
        tile.updateTile()
    }

    companion object {
        fun isEnabled(context: Context): Boolean =
            context.packageManager.getComponentEnabledSetting(component(context)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED

        fun setEnabled(context: Context, enabled: Boolean) {
            context.packageManager.setComponentEnabledSetting(
                component(context),
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }

        private fun component(context: Context) = ComponentName(context, ScheduleTileService::class.java)
    }
}
