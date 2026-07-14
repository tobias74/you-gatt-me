package de.tobiga.yougattme.gatt

import android.bluetooth.BluetoothDevice
import java.util.*

/**
 * Compatibility base class for older characteristics that predate BaseGattCharacteristic.
 */
abstract class TobigaGattCharacteristic(
    override val gattProfile: AbstractGattProfile
) : BaseGattCharacteristic(gattProfile) {
    abstract override fun getCharacteristicUUID(): UUID
    abstract override fun onReadRequest(device: BluetoothDevice, requestId: Int, offset: Int)
    abstract override fun onWriteRequest(device: BluetoothDevice, requestId: Int, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray)
}
