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
import kotlinx.coroutines.launch

private val COLOR_GOOD = Color.parseColor("#FF00C853")
private val COLOR_OK = Color.parseColor("#FFFFD600")
private val COLOR_LOW = Color.parseColor("#FFFF9100")
private val COLOR_CRITICAL = Color.parseColor("#FFFF3D00")
private val COLOR_UNKNOWN = Color.parseColor("#FF9E9E9E")

class BatteryField(extension: String) : DataTypeImpl(extension, "battery") {
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        if (config.preview) {
            emitter.updateView(render(context, config, BatteryStatus.GOOD))
            return
        }
        val job = CoroutineScope(Dispatchers.Main).launch {
            VariaBattery.status.collect { status ->
                emitter.updateView(render(context, config, status))
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    private fun render(context: Context, config: ViewConfig, status: BatteryStatus?): RemoteViews {
        val (width, height) = config.viewSize
        val views = RemoteViews(context.packageName, if (width >= height * 2) R.layout.item_mode_wide else R.layout.item_mode_tall)
        views.setInt(R.id.item, "setBackgroundColor", COLOR_FIELD)
        views.setImageViewResource(R.id.icon, icon(status))
        views.setInt(R.id.icon, "setColorFilter", color(status))
        views.setTextViewText(R.id.label, label(context, status))
        views.setTextColor(R.id.label, Color.WHITE)
        return views
    }

    private fun icon(status: BatteryStatus?): Int = when (status) {
        BatteryStatus.NEW -> R.drawable.ic_battery_new
        BatteryStatus.GOOD -> R.drawable.ic_battery_good
        BatteryStatus.OK -> R.drawable.ic_battery_ok
        BatteryStatus.LOW -> R.drawable.ic_battery_low
        BatteryStatus.CRITICAL -> R.drawable.ic_battery_critical
        BatteryStatus.INVALID, null -> R.drawable.ic_battery_unknown
    }

    private fun color(status: BatteryStatus?): Int = when (status) {
        BatteryStatus.NEW, BatteryStatus.GOOD -> COLOR_GOOD
        BatteryStatus.OK -> COLOR_OK
        BatteryStatus.LOW -> COLOR_LOW
        BatteryStatus.CRITICAL -> COLOR_CRITICAL
        BatteryStatus.INVALID, null -> COLOR_UNKNOWN
    }

    private fun label(context: Context, status: BatteryStatus?): String = context.getString(
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
