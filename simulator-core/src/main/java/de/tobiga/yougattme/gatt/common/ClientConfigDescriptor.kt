package de.tobiga.yougattme.gatt.common

import de.tobiga.yougattme.gatt.trace.sendTracedResponse

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattDescriptor
import android.util.Log
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.TobigaGattDescriptor
import java.util.Arrays
import java.util.UUID

class ClientConfigDescriptor(
    gattProfile: AbstractGattProfile,
    private val supportsNotifications: Boolean = true,
    private val supportsIndications: Boolean = false
) : TobigaGattDescriptor(gattProfile) {

    val CLIENT_CONFIG_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    val registeredDevices = mutableSetOf<BluetoothDevice>()
    val indicatedDevices = mutableSetOf<BluetoothDevice>()

    init {
        gattDescriptor = BluetoothGattDescriptor(
            CLIENT_CONFIG_DESCRIPTOR,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
    }

    override fun onReadRequest(
        device: BluetoothDevice,
        requestId: Int,
        offset: Int
    ) {
        val returnValue = when {
            indicatedDevices.contains(device) -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            registeredDevices.contains(device) -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        }

        Log.d("BLE", "CCCD read from ${device.address}: ${returnValue.joinToString()}")

        gattProfile.bluetoothGattServer!!.sendTracedResponse(
            device,
            requestId,
            BluetoothGatt.GATT_SUCCESS,
            0,
            returnValue
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
        Log.d("BLE", "CCCD write from ${device.address}: ${value.joinToString()}")

        val status = when {
            Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value) && supportsNotifications -> {
                removeDevice(device)
                registeredDevices.add(device)
                BluetoothGatt.GATT_SUCCESS
            }
            Arrays.equals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE, value) && supportsIndications -> {
                removeDevice(device)
                indicatedDevices.add(device)
                BluetoothGatt.GATT_SUCCESS
            }
            Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value) -> {
                removeDevice(device)
                BluetoothGatt.GATT_SUCCESS
            }
            else -> {
                Log.w("BLE", "Unsupported CCCD value for this characteristic: ${value.joinToString()}")
                BluetoothGatt.GATT_FAILURE
            }
        }

        if (responseNeeded) {
            gattProfile.bluetoothGattServer!!.sendTracedResponse(
                device,
                requestId,
                status,
                0,
                null
            )
        }
    }

    fun isIndicationEnabled(device: BluetoothDevice): Boolean {
        return indicatedDevices.any { it.address == device.address }
    }

    fun removeDevice(device: BluetoothDevice) {
        registeredDevices.removeAll { it.address == device.address }
        indicatedDevices.removeAll { it.address == device.address }
    }

    fun clearDevices() {
        registeredDevices.clear()
        indicatedDevices.clear()
    }
}
