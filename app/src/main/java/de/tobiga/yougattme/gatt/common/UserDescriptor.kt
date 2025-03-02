package de.tobiga.yougattme.gatt.common

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattDescriptor
import android.util.Log
import de.tobiga.yougattme.gatt.GattProfile
import de.tobiga.yougattme.gatt.TobigaGattDescriptor
import java.util.*

class UserDescriptor(val myGattProfile: GattProfile, val description: ByteArray = "tobiga GATT".toByteArray()): TobigaGattDescriptor(myGattProfile) {

    val USER_DESCRIPTOR: UUID = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb")

    init {
        gattDescriptor = BluetoothGattDescriptor(USER_DESCRIPTOR, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
    }

    override fun onReadRequest(device: BluetoothDevice, requestId: Int, offset: Int) {
        gattProfile.bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,0, description)

    }

    override fun onWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray
    ) {

    }


}