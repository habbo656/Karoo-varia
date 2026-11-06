package dev.karoovaria.light

import android.content.Context
import android.widget.RemoteViews
import io.hammerhead.karooext.models.ViewConfig

object FieldViews {
    /**
     * A single icon and label, arranged to suit the field's shape.
     */
    fun item(context: Context, config: ViewConfig): RemoteViews {
        val (width, height) = config.viewSize
        return RemoteViews(context.packageName, if (width >= height * 2) R.layout.item_mode_wide else R.layout.item_mode_tall)
    }

    /**
     * Adds a small symbol in the top left corner, like the Karoo's own data fields.
     */
    fun withCorner(context: Context, content: RemoteViews, icon: Int, color: Int): RemoteViews {
        val frame = RemoteViews(context.packageName, R.layout.field_frame)
        frame.addView(R.id.frame, content)
        val corner = RemoteViews(context.packageName, R.layout.view_corner)
        corner.setImageViewResource(R.id.corner, icon)
        corner.setInt(R.id.corner, "setColorFilter", color)
        frame.addView(R.id.frame, corner)
        return frame
    }
}
