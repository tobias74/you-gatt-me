package de.tobiga.yougattme.gatt.common

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattDescriptor
import de.tobiga.yougattme.gatt.GattProfile
import de.tobiga.yougattme.gatt.TobigaGattDescriptor
import java.util.*

class ServerConfigDescriptor(gattProfile: GattProfile): TobigaGattDescriptor(gattProfile) {
    val SERVER_CONFIG_DESCRIPTOR_UUID: UUID = UUID.fromString("00002903-0000-1000-8000-00805f9b34fb")

    init {
        gattDescriptor = BluetoothGattDescriptor(SERVER_CONFIG_DESCRIPTOR_UUID, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
    }

    override fun onReadRequest(device: BluetoothDevice, requestId: Int, offset: Int) {
        gattProfile.bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,0, null)
    }

    override fun onWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray
    ) {
        TODO("Not yet implemented")
    }

}