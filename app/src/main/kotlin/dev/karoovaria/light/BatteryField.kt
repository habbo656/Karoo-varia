package dev.karoovaria.light

import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.BatteryStatus
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private val COLOR_GOOD = Color.parseColor("#FF00C853")
private val COLOR_OK = Color.parseColor("#FFFFD600")
private val COLOR_LOW = Color.parseColor("#FFFF9100")
private val COLOR_CRITICAL = Color.parseColor("#FFFF3D00")
internal val COLOR_UNKNOWN = Color.parseColor("#FF9E9E9E")

object BatteryUi {
    fun icon(status: BatteryStatus?): Int = when (status) {
        BatteryStatus.NEW -> R.drawable.ic_battery_new
        BatteryStatus.GOOD -> R.drawable.ic_battery_good
        BatteryStatus.OK -> R.drawable.ic_battery_ok
        BatteryStatus.LOW -> R.drawable.ic_battery_low
        BatteryStatus.CRITICAL -> R.drawable.ic_battery_critical
        BatteryStatus.INVALID, null -> R.drawable.ic_battery_unknown
    }

    fun color(status: BatteryStatus?): Int = when (status) {
        BatteryStatus.NEW, BatteryStatus.GOOD -> COLOR_GOOD
        BatteryStatus.OK -> COLOR_OK
        BatteryStatus.LOW -> COLOR_LOW
        BatteryStatus.CRITICAL -> COLOR_CRITICAL
        BatteryStatus.INVALID, null -> COLOR_UNKNOWN
    }

    fun label(context: Context, status: BatteryStatus?): String = context.getString(
        when (status) {
            BatteryStatus.NEW -> R.string.battery_new
            BatteryStatus.GOOD -> R.string.battery_good
            BatteryStatus.OK -> R.string.battery_ok
            BatteryStatus.LOW -> R.string.battery_low
            BatteryStatus.CRITICAL -> R.string.battery_critical
            BatteryStatus.INVALID, null -> R.string.mode_unknown
        },
    )
}

class BatteryField(extension: String, private val radar: RadarStatus) : DataTypeImpl(extension, "battery") {
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        if (config.preview) {
            emitter.updateView(render(context, config, BatteryStatus.GOOD, connected = true))
            return
        }
        radar.attach()
        val job = CoroutineScope(Dispatchers.Main).launch {
            combine(VariaBattery.status, radar.connected) { status, connected -> status to connected }
                .collectLatest { (status, connected) ->
                    while (true) {
                        emitter.updateView(render(context, config, status, connected != false))
                        delay(VIEW_REFRESH_MS)
                    }
                }
        }
        emitter.setCancellable {
            job.cancel()
            radar.detach()
        }
    }

    private fun render(context: Context, config: ViewConfig, status: BatteryStatus?, connected: Boolean): RemoteViews {
        val shown = status.takeIf { connected }
        val views = FieldViews.item(context, config)
        views.setInt(R.id.item, "setBackgroundColor", COLOR_FIELD)
        views.setImageViewResource(R.id.icon, BatteryUi.icon(shown))
        views.setInt(R.id.icon, "setColorFilter", BatteryUi.color(shown))
        views.setTextViewText(R.id.label, if (connected) BatteryUi.label(context, status) else context.getString(R.string.disconnected))
        views.setTextColor(R.id.label, if (connected) Color.WHITE else COLOR_UNKNOWN)
        return FieldViews.withCorner(context, views, R.drawable.ic_light, if (connected) Color.WHITE else COLOR_UNKNOWN)
    }
}
