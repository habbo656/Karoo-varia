package dev.karoovaria.light

import io.hammerhead.karooext.models.BatteryStatus
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Last battery status the Karoo has saved for the light.
 */
object VariaBattery {
    val status = MutableStateFlow<BatteryStatus?>(null)
}
