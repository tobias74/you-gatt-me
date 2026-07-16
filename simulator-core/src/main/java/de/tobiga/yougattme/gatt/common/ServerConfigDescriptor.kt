package de.tobiga.yougattme.gatt.common

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattDescriptor
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.TobigaGattDescriptor
import java.util.UUID

class ServerConfigDescriptor(
    gattProfile: AbstractGattProfile,
    private val onBroadcastChanged: (BluetoothDevice, Boolean) -> Unit = { _, _ -> }
) : TobigaGattDescriptor(gattProfile) {
    private val enabledDeviceAddresses = mutableSetOf<String>()

    init {
        gattDescriptor = BluetoothGattDescriptor(
            UUID.fromString("00002903-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
    }

    override fun onReadRequest(device: BluetoothDevice, requestId: Int, offset: Int) {
        val value = if (device.address in enabledDeviceAddresses) {
            byteArrayOf(0x01, 0x00)
        } else {
            byteArrayOf(0x00, 0x00)
        }
        gattProfile.bluetoothGattServer?.sendResponse(
            device,
            requestId,
            BluetoothGatt.GATT_SUCCESS,
            0,
            value
        )
    }

    override fun onWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray
    ) {
        val enabled = value.contentEquals(byteArrayOf(0x01, 0x00))
        val disabled = value.contentEquals(byteArrayOf(0x00, 0x00))
        val status = if (!preparedWrite && offset == 0 && (enabled || disabled)) {
            if (enabled) enabledDeviceAddresses.add(device.address) else enabledDeviceAddresses.remove(device.address)
            onBroadcastChanged(device, enabled)
            BluetoothGatt.GATT_SUCCESS
        } else {
            BluetoothGatt.GATT_FAILURE
        }
        if (responseNeeded) {
            gattProfile.bluetoothGattServer?.sendResponse(device, requestId, status, 0, null)
        }
    }

    fun removeDevice(device: BluetoothDevice) {
        enabledDeviceAddresses.remove(device.address)
    }

    fun clearDevices() {
        enabledDeviceAddresses.clear()
    }
}
