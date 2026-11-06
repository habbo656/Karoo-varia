package dev.karoovaria.light

import android.content.Context

// Same order as the Garmin Varia app
private val MODE_ORDER = listOf("STEADY4", "STEADY5", "SLOW_FLASH", "FAST_FLASH", "OFF")

object LightModeUi {
    fun ordered(modes: List<String>): List<String> = modes.sortedBy { mode ->
        MODE_ORDER.indexOf(mode).takeIf { it >= 0 } ?: MODE_ORDER.size
    }

    fun icon(mode: String?): Int = when (mode) {
        "STEADY4" -> R.drawable.ic_mode_max_solid
        "STEADY5" -> R.drawable.ic_mode_peloton
        "SLOW_FLASH" -> R.drawable.ic_mode_night_flash
        "FAST_FLASH" -> R.drawable.ic_mode_day_flash
        "OFF" -> R.drawable.ic_mode_off
        else -> R.drawable.ic_light
    }

    fun label(context: Context, mode: String?): String = when (mode) {
        null -> context.getString(R.string.mode_unknown)
        "STEADY4" -> context.getString(R.string.mode_max_solid)
        "STEADY5" -> context.getString(R.string.mode_peloton)
        "SLOW_FLASH" -> context.getString(R.string.mode_night_flash)
        "FAST_FLASH" -> context.getString(R.string.mode_day_flash)
        "OFF" -> context.getString(R.string.mode_off)
        else -> mode
    }
}
