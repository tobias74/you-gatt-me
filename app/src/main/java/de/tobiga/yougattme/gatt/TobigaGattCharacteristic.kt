package de.tobiga.yougattme.gatt

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import java.util.*

abstract class TobigaGattCharacteristic(protected val gattProfile: GattProfile) {

    lateinit var gattCharacteristic: BluetoothGattCharacteristic
    abstract fun getCharacteristicUUID(): UUID

    abstract fun onReadRequest(device: BluetoothDevice, requestId: Int, offset: Int)
    abstract fun onWriteRequest(device: BluetoothDevice, requestId: Int, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray)

    protected val activeDescriptors = mutableSetOf<TobigaGattDescriptor>()

    init {

    }

    fun getTobigaDescriptorByUUID(uuid: UUID): TobigaGattDescriptor? {
        val tobigaDescriptor = activeDescriptors.find {
            it.gattDescriptor.uuid == uuid
        }
        return tobigaDescriptor
    }
}