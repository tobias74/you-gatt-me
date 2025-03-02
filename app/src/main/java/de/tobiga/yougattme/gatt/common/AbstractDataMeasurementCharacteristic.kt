package de.tobiga.yougattme.gatt.common

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.util.Log
import de.tobiga.yougattme.gatt.GattProfile
import de.tobiga.yougattme.gatt.TobigaGattCharacteristic
import de.tobiga.yougattme.tools.toHex

abstract class AbstractDataMeasurementCharacteristic(gattProfile: GattProfile) : TobigaGattCharacteristic(gattProfile) {
    var clientConfigDescriptor: ClientConfigDescriptor

    abstract fun getData(): ByteArray

    init {
        clientConfigDescriptor = ClientConfigDescriptor(gattProfile)
        activeDescriptors.add(clientConfigDescriptor)
        activeDescriptors.add(UserDescriptor(gattProfile))
        activeDescriptors.add(ServerConfigDescriptor(gattProfile))

    }

    fun sendData() {
        val dataBlock = getData()

        if (clientConfigDescriptor.registeredDevices.isEmpty()) {
            return
        }
        for (device in clientConfigDescriptor.registeredDevices) {
            gattCharacteristic.value = dataBlock
            gattProfile.bluetoothGattServer?.notifyCharacteristicChanged(device, gattCharacteristic, false)
        }
    }

    final override fun onReadRequest(device: BluetoothDevice, requestId: Int, offset: Int) {
        gattProfile.bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,0,
            getData()
        )
    }

    final override fun onWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray
    ) {
    }


}