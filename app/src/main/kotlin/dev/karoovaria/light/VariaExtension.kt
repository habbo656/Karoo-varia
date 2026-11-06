package dev.karoovaria.light

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.SavedDevices

private const val ANT_DEVICE_TYPE_LIGHT = "35"

class VariaExtension : KarooExtension("varia", "0.1") {
    private lateinit var karooSystem: KarooSystemService

    override val types by lazy { listOf(LightModeField(extension), BatteryField(extension)) }

    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(this)
        karooSystem.addConsumer { event: SavedDevices ->
            // ANT+ sensor ids are <device number>-<device type>-<transmission type>
            val light = event.devices.firstOrNull { it.enabled && it.id.split("-").getOrNull(1) == ANT_DEVICE_TYPE_LIGHT }
            KarooLightClient.setLight(light?.id)
            VariaBattery.status.value = light?.details?.lastBattery
        }
        karooSystem.connect()
    }

    override fun onDestroy() {
        karooSystem.disconnect()
        super.onDestroy()
    }
}
