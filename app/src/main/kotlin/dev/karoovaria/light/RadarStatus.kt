package dev.karoovaria.light

import android.os.Handler
import android.os.Looper
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Ignores short dropouts
private const val DISCONNECT_DELAY_MS = 5000L

/**
 * Follows the radar connection for views showing it.
 */
class RadarStatus(private val karooSystem: KarooSystemService) {
    private val connectedFlow = MutableStateFlow<Boolean?>(null)

    /**
     * Null until the Karoo has reported a state.
     */
    val connected: StateFlow<Boolean?> = connectedFlow

    private val handler = Handler(Looper.getMainLooper())
    private val markDisconnected = Runnable { connectedFlow.value = false }
    private var consumerId: String? = null
    private var viewCount = 0

    @Synchronized
    fun attach() {
        viewCount++
        if (viewCount > 1) return
        consumerId = karooSystem.addConsumer(OnStreamState.StartStreaming(DataType.Type.RADAR)) { event: OnStreamState ->
            onState(event.state)
        }
    }

    @Synchronized
    fun detach() {
        viewCount--
        if (viewCount > 0) return
        consumerId?.let { karooSystem.removeConsumer(it) }
        consumerId = null
        handler.removeCallbacks(markDisconnected)
        connectedFlow.value = null
    }

    private fun onState(state: StreamState) {
        if (state is StreamState.Streaming) {
            handler.removeCallbacks(markDisconnected)
            connectedFlow.value = true
        } else if (connectedFlow.value == null) {
            connectedFlow.value = false
        } else if (connectedFlow.value == true) {
            handler.removeCallbacks(markDisconnected)
            handler.postDelayed(markDisconnected, DISCONNECT_DELAY_MS)
        }
    }
}
