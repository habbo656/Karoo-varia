package dev.karoovaria.light

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles taps on the data field: sets the mode in [EXTRA_MODE], or the next supported mode without it.
 */
class LightCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mode = intent.getStringExtra(EXTRA_MODE) ?: nextMode() ?: return
        KarooLightClient.setMode(mode)
    }

    private fun nextMode(): String? {
        val state = KarooLightClient.state.value
        val modes = LightModeUi.ordered(state.supportedModes).ifEmpty { return null }
        return modes[(modes.indexOf(state.mode) + 1) % modes.size]
    }

    companion object {
        const val EXTRA_MODE = "mode"
    }
}
