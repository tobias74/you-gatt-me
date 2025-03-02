package de.tobiga.yougattme.gatt

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattDescriptor

abstract class TobigaGattDescriptor(protected val gattProfile: GattProfile) {

    lateinit var gattDescriptor: BluetoothGattDescriptor

    abstract fun onReadRequest(device: BluetoothDevice, requestId: Int, offset: Int)
    abstract fun onWriteRequest(device: BluetoothDevice, requestId: Int, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray)


}