package de.tobiga.yougattme.gatt.common

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattDescriptor
import android.util.Log
import de.tobiga.yougattme.gatt.GattProfile
import de.tobiga.yougattme.gatt.TobigaGattDescriptor
import java.util.*

class ClientConfigDescriptor(gattProfile: GattProfile): TobigaGattDescriptor(gattProfile) {

    val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    val registeredDevices = mutableSetOf<BluetoothDevice>()

    init {
        gattDescriptor = BluetoothGattDescriptor(CLIENT_CONFIG_DESCRIPTOR, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
    }

    override fun onReadRequest(device: BluetoothDevice, requestId: Int, offset: Int) {
        val returnValue = if (registeredDevices.contains(device)) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        }
        gattProfile.bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,0, returnValue)
    }

    override fun onWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray
    ) {
        if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
            registeredDevices.add(device)
        } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
            registeredDevices.remove(device)
        }

        if (responseNeeded) {
            gattProfile.bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,0, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        }


    }
}