package dev.karoovaria.light

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

// The page grid is 60 units in both directions
private const val GRID_FULL = 60
private const val GRID_HALF = 30
private const val GRID_STACKED = 45

internal val COLOR_FIELD = Color.parseColor("#FF2B2B2B")
// The ride screen can drop updates sent while it is loading, so views are re-sent periodically
internal const val VIEW_REFRESH_MS = 3000L

private val PREVIEW_STATE = LightState("STEADY4", listOf("OFF", "STEADY4", "STEADY5", "SLOW_FLASH", "FAST_FLASH"))

class LightModeField(extension: String, private val radar: RadarStatus) : DataTypeImpl(extension, "light-mode") {
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        if (config.preview) {
            emitter.updateView(
                FieldViews.withCorner(
                    context,
                    render(context, config, PREVIEW_STATE, interactive = false),
                    BatteryUi.icon(BatteryStatus.GOOD),
                    BatteryUi.color(BatteryStatus.GOOD),
                ),
            )
            return
        }
        KarooLightClient.attach(context)
        radar.attach()
        val job = CoroutineScope(Dispatchers.Main).launch {
            combine(KarooLightClient.state, VariaBattery.status, radar.connected) { state, battery, connected ->
                Triple(state, battery, connected)
            }.collectLatest { (state, battery, connected) ->
                while (true) {
                    val views = if (connected == false) {
                        renderDisconnected(context, config)
                    } else {
                        FieldViews.withCorner(
                            context,
                            render(context, config, state, interactive = true),
                            BatteryUi.icon(battery),
                            BatteryUi.color(battery),
                        )
                    }
                    emitter.updateView(views)
                    delay(VIEW_REFRESH_MS)
                }
            }
        }
        emitter.setCancellable {
            job.cancel()
            radar.detach()
            KarooLightClient.detach(context)
        }
    }

    private fun render(context: Context, config: ViewConfig, state: LightState, interactive: Boolean): RemoteViews {
        val (columns, rows) = config.gridSize
        return if (columns >= GRID_FULL && rows >= GRID_HALF && state.supportedModes.isNotEmpty()) {
            renderAllModes(context, rows >= GRID_STACKED, state, interactive)
        } else {
            renderToggle(context, config, state, interactive)
        }
    }

    private fun renderAllModes(context: Context, stacked: Boolean, state: LightState, interactive: Boolean): RemoteViews {
        val container = RemoteViews(
            context.packageName,
            if (stacked) R.layout.field_modes_stacked else R.layout.field_modes_row,
        )
        LightModeUi.ordered(state.supportedModes).forEachIndexed { index, mode ->
            val item = RemoteViews(context.packageName, if (stacked) R.layout.item_mode_wide else R.layout.item_mode_cell)
            bindMode(context, item, mode, selected = mode == state.mode)
            if (interactive) {
                item.setOnClickPendingIntent(R.id.item, tapIntent(context, index + 1, mode))
            }
            container.addView(R.id.container, item)
        }
        return container
    }

    private fun renderToggle(context: Context, config: ViewConfig, state: LightState, interactive: Boolean): RemoteViews {
        val views = FieldViews.item(context, config)
        bindMode(context, views, state.mode, selected = false)
        if (interactive) {
            views.setOnClickPendingIntent(R.id.item, tapIntent(context, 0, null))
        }
        return views
    }

    private fun renderDisconnected(context: Context, config: ViewConfig): RemoteViews {
        val views = FieldViews.item(context, config)
        views.setInt(R.id.item, "setBackgroundColor", COLOR_FIELD)
        views.setImageViewResource(R.id.icon, R.drawable.ic_mode_off)
        views.setInt(R.id.icon, "setColorFilter", COLOR_UNKNOWN)
        views.setTextViewText(R.id.label, context.getString(R.string.disconnected))
        views.setTextColor(R.id.label, COLOR_UNKNOWN)
        return views
    }

    private fun bindMode(context: Context, views: RemoteViews, mode: String?, selected: Boolean) {
        val foreground = if (selected) Color.BLACK else Color.WHITE
        views.setInt(R.id.item, "setBackgroundColor", if (selected) Color.WHITE else COLOR_FIELD)
        views.setImageViewResource(R.id.icon, LightModeUi.icon(mode))
        views.setInt(R.id.icon, "setColorFilter", foreground)
        views.setTextViewText(R.id.label, LightModeUi.label(context, mode))
        views.setTextColor(R.id.label, foreground)
    }

    private fun tapIntent(context: Context, requestCode: Int, mode: String?): PendingIntent {
        val intent = Intent(context, LightCommandReceiver::class.java)
        mode?.let { intent.putExtra(LightCommandReceiver.EXTRA_MODE, it) }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
