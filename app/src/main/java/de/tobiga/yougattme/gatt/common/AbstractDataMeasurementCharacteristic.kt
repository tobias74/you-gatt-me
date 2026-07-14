package de.tobiga.yougattme.gatt.common

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.util.Log
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.BaseGattCharacteristic
import de.tobiga.yougattme.tools.toHex

abstract class AbstractDataMeasurementCharacteristic(
    gattProfile: AbstractGattProfile
) : BaseGattCharacteristic(gattProfile) {

    var clientConfigDescriptor: ClientConfigDescriptor

    abstract fun getData(): ByteArray

    init {
        clientConfigDescriptor = ClientConfigDescriptor(gattProfile)
        activeDescriptors.add(clientConfigDescriptor)
        activeDescriptors.add(UserDescriptor(gattProfile))

    }

    fun sendData(dataBlock: ByteArray) {

        if (clientConfigDescriptor.registeredDevices.isEmpty()) {
            Log.w("BLE","No devices registered for data characteristic, not sending out for $this")
            return
        }
        for (device in clientConfigDescriptor.registeredDevices) {

            Log.d("BLE","sending data to registered device ${device.address} " + dataBlock.toHex())

            gattCharacteristic.value = dataBlock
            gattProfile.bluetoothGattServer!!.notifyCharacteristicChanged(device, gattCharacteristic, false)
        }

    }

    fun publishMeasurementData() {
        val dataBlock = getData()
        sendData(dataBlock)
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
