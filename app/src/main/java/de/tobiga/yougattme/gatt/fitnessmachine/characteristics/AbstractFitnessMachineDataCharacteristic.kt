package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic

abstract class AbstractFitnessMachineDataCharacteristic(
    gattProfile: AbstractGattProfile
) : AbstractDataMeasurementCharacteristic(gattProfile) {

    override fun onReadRequest(device: BluetoothDevice, requestId: Int, offset: Int) {
        gattProfile.bluetoothGattServer!!.sendResponse(
            device,
            requestId,
            BluetoothGatt.GATT_READ_NOT_PERMITTED,
            0,
            null
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
        if (responseNeeded) {
            gattProfile.bluetoothGattServer!!.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_WRITE_NOT_PERMITTED,
                0,
                null
            )
        }
    }
}
