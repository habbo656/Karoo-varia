package io.hammerhead.datamodels.timeseriesData.models

import android.os.Parcel
import android.os.Parcelable

/**
 * Stands in for the sensor service's own enum, which unparcels by this exact class name.
 */
class LightMode(private val modeName: String) : Parcelable {
    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(modeName)
    }
}
