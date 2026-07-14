package de.tobiga.yougattme.gatt

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import java.util.UUID

abstract class BaseGattCharacteristic(
    open val gattProfile: AbstractGattProfile
) {
    lateinit var gattCharacteristic: BluetoothGattCharacteristic

    protected val activeDescriptors = mutableSetOf<TobigaGattDescriptor>()

    abstract fun getCharacteristicUUID(): UUID

    abstract fun onReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int
    )

    abstract fun onWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray
    )

    fun getTobigaDescriptorByUUID(uuid: UUID): TobigaGattDescriptor? =
        activeDescriptors.find { it.gattDescriptor.uuid == uuid }
}
