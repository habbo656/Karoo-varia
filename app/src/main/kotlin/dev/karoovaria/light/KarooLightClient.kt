package dev.karoovaria.light

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import io.hammerhead.datamodels.timeseriesData.models.LightMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "KarooVaria"

private const val SENSOR_PACKAGE = "io.hammerhead.sensorservice"
private const val SENSOR_SERVICE = "$SENSOR_PACKAGE.service.SensorService"
private const val SENSOR_AIDL = "$SENSOR_PACKAGE.SensorServiceAIDL"
private const val LIGHT_AIDL = "$SENSOR_PACKAGE.LightCommandConnectionAIDL"
private const val LISTENER_AIDL = "io.hammerhead.aidlrx.IParcelableListener"
private const val MODELS = "io.hammerhead.datamodels.timeseriesData.models"

private const val TX_GET_LIGHT_BINDER = 18
private const val TX_REGISTER_LISTENER = 1
private const val TX_UNREGISTER_LISTENER = 2
private const val TX_SET_LIGHT_MODE = 3

private const val CLIENT_ID = "karoo-varia"

/**
 * Current state as reported by the light.
 */
data class LightState(
    val mode: String? = null,
    val supportedModes: List<String> = emptyList(),
)

/**
 * Talks to the Karoo's private light service: undocumented, and may change with any Karoo OS update.
 */
object KarooLightClient {
    private val stateFlow = MutableStateFlow(LightState())
    val state: StateFlow<LightState> = stateFlow

    private var lightBinder: IBinder? = null
    private var lightUid: String? = null
    private var registeredUid: String? = null
    private var viewCount = 0

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            lightBinder = fetchLightBinder(service)
            registeredUid = null
            updateListener()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.w(TAG, "Sensor service disconnected")
            lightBinder = null
        }
    }

    // Receives LightParameters updates from the sensor service
    private val listener = object : Binder() {
        init {
            attachInterface(null, LISTENER_AIDL)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(LISTENER_AIDL)
                return true
            }
            data.enforceInterface(LISTENER_AIDL)
            when (code) {
                1 -> {
                    data.readString() // transaction id
                    data.readString() // class name
                    onLightParameters(data.createByteArray() ?: ByteArray(0))
                    reply?.writeNoException()
                }
                2 -> Log.w(TAG, "Light listener error ${data.readInt()}: ${data.readString()}")
                3 -> Log.i(TAG, "Light listener completed")
                else -> return super.onTransact(code, data, reply, flags)
            }
            return true
        }
    }

    /**
     * Sets which saved sensor to control, null if no light is paired.
     */
    @Synchronized
    fun setLight(uid: String?) {
        lightUid = uid
        updateListener()
    }

    /**
     * Starts following the light for a view showing its state.
     */
    @Synchronized
    fun attach(context: Context) {
        viewCount++
        if (viewCount == 1) {
            val intent = Intent().setClassName(SENSOR_PACKAGE, SENSOR_SERVICE)
            context.applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    @Synchronized
    fun detach(context: Context) {
        viewCount--
        if (viewCount == 0) {
            lightBinder?.let { unregisterListener(it) }
            context.applicationContext.unbindService(connection)
            lightBinder = null
            registeredUid = null
        }
    }

    fun setMode(mode: String) {
        val binder = lightBinder
        val uid = lightUid
        if (binder == null || uid == null) {
            Log.w(TAG, "Ignoring mode $mode, light not available")
            return
        }
        val bundle = Bundle().apply { putParcelable("value", LightMode(mode)) }
        transact(binder, TX_SET_LIGHT_MODE) {
            writeString(CLIENT_ID)
            writeInt(1)
            writeLightDevice(uid)
            writeInt(1)
            bundle.writeToParcel(this, 0)
        }
    }

    private fun onLightParameters(payload: ByteArray) {
        val parcel = Parcel.obtain()
        try {
            parcel.unmarshall(payload, 0, payload.size)
            parcel.setDataPosition(0)
            val mode = parcel.readString()
            parcel.readString() // location
            val supported = List(parcel.readInt()) { parcel.readString().orEmpty() }
            stateFlow.value = LightState(mode, supported)
        } finally {
            parcel.recycle()
        }
    }

    private fun fetchLightBinder(service: IBinder): IBinder? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SENSOR_AIDL)
            service.transact(TX_GET_LIGHT_BINDER, data, reply, 0)
            reply.readException()
            return reply.readStrongBinder()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun updateListener() {
        val binder = lightBinder ?: return
        val uid = lightUid
        if (uid == registeredUid) return
        if (registeredUid != null) unregisterListener(binder)
        registeredUid = uid
        if (uid == null) {
            stateFlow.value = LightState()
            return
        }
        transact(binder, TX_REGISTER_LISTENER) {
            writeString(CLIENT_ID)
            writeInt(1)
            writeLightDevice(uid)
            writeStrongBinder(listener)
        }
    }

    private fun unregisterListener(binder: IBinder) {
        transact(binder, TX_UNREGISTER_LISTENER) { writeString(CLIENT_ID) }
    }

    private fun transact(binder: IBinder, code: Int, write: Parcel.() -> Unit) {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(LIGHT_AIDL)
            data.write()
            binder.transact(code, data, null, IBinder.FLAG_ONEWAY)
        } finally {
            data.recycle()
        }
    }

    // Matches Device.writeToParcel in the sensor service; only the uid and data type matter for lookup.
    private fun Parcel.writeLightDevice(uid: String) {
        writeString(uid)
        writeString("$MODELS.DeviceInfo")
        writeString(uid) // name
        writeString(null) // axsBikeId
        writeInt(0) // syncedWithAxs
        writeInt(0) // nameEditedByUser
        writeInt(0) // lastBatteryValue
        writeInt(0) // manufacturerInfo
        writeInt(0) // componentInfo
        writeList(listOf("TYPE_BIKE_LIGHT_ID")) // supportedDataTypes
        writeList(emptyList<String>()) // blockedDataTypes
        writeInt(1) // isEnabled
        writeInt(1) // isConnectedAtLeastOnce
        writeInt(1) // connectionType = ANT_PLUS
        writeString(null) // wheelCircumferenceInfo
        writeString(null) // shiftingInfo
        writeInt(-1) // lightMode
        writeInt(-1) // autoLightControl
        writeInt(-1) // ctfOffset
        writeInt(-1) // priority
        writeInt(-1) // functionSets
        writeString(null) // axsPrimarySerial
        writeInt(0) // alerts size
        writeIntArray(IntArray(0)) // alerts
        writeInt(-1) // tirePressureAlerts
    }
}
