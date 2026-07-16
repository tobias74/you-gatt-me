package de.tobiga.yougattme.gatt.common

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.util.Log
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.BaseGattCharacteristic
import de.tobiga.yougattme.tools.toHex

abstract class AbstractDataMeasurementCharacteristic(
    gattProfile: AbstractGattProfile,
    includeUserDescriptor: Boolean = true
) : BaseGattCharacteristic(gattProfile) {

    var clientConfigDescriptor: ClientConfigDescriptor

    abstract fun getData(): ByteArray

    open fun getNotificationPackets(maxPayloadBytes: Int): List<ByteArray> = listOf(getData())

    open fun getNotificationPacketsForDevice(
        device: BluetoothDevice,
        maxPayloadBytes: Int
    ): List<ByteArray> = getNotificationPackets(maxPayloadBytes)

    init {
        clientConfigDescriptor = ClientConfigDescriptor(gattProfile)
        activeDescriptors.add(clientConfigDescriptor)
        if (includeUserDescriptor) activeDescriptors.add(UserDescriptor(gattProfile))

    }

    open fun sendData(dataBlock: ByteArray, coalesceKey: String? = null) {
        sendDataToRegisteredDevices(dataBlock, coalesceKey)
    }

    protected fun sendDataToRegisteredDevices(
        dataBlock: ByteArray,
        coalesceKey: String? = null,
        excludedDeviceAddress: String? = null
    ) {

        if (clientConfigDescriptor.registeredDevices.isEmpty()) {
            Log.w("BLE","No devices registered for data characteristic, not sending out for $this")
            return
        }
        for (device in clientConfigDescriptor.registeredDevices.filterNot { device ->
            device.address == excludedDeviceAddress
        }) {

            Log.d("BLE","sending data to registered device ${device.address} " + dataBlock.toHex())

            GattNotificationDispatcher.enqueue(
                server = gattProfile.bluetoothGattServer!!,
                device = device,
                characteristic = gattCharacteristic,
                confirm = false,
                value = dataBlock,
                coalesceKey = coalesceKey
            )
        }

    }

    fun publishMeasurementData() {
        if (clientConfigDescriptor.registeredDevices.isEmpty()) {
            return
        }
        for (device in clientConfigDescriptor.registeredDevices) {
            val packets = getNotificationPacketsForDevice(
                device,
                gattProfile.getNotificationPayloadSize(device)
            )
            packets.forEachIndexed { index, dataBlock ->
                Log.d("BLE", "sending packet ${index + 1}/${packets.size} to ${device.address} ${dataBlock.toHex()}")
            }
            GattNotificationDispatcher.enqueueBatch(
                server = gattProfile.bluetoothGattServer!!,
                device = device,
                characteristic = gattCharacteristic,
                confirm = false,
                values = packets,
                coalesceKey = getCharacteristicUUID().toString()
            )
        }
    }

    override fun onReadRequest(device: BluetoothDevice, requestId: Int, offset: Int) {
        Log.d("BLE", "responding to read request in data characteristic: ${getData().toHex()}")

        gattProfile.bluetoothGattServer!!.sendResponse(
            device,
            requestId,
            BluetoothGatt.GATT_SUCCESS,
            0,
            getData()
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
        Log.d("BLE", "Received write request in data characteristic? ::: ${value.toHex()}")
        if(responseNeeded) {
            gattProfile.bluetoothGattServer!!.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
    }


}
