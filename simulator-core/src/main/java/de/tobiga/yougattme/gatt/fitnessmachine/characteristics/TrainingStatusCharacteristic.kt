package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import java.util.UUID

object TRAINING_STATUS_CODES {
    const val IDLE = 0x01
    const val MANUAL_MODE = 0x0D
}

class TrainingStatusCharacteristic(
    gattProfile: AbstractGattProfile
) : AbstractDataMeasurementCharacteristic(gattProfile) {

    private val GATT_TRAINING_STATUS_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("00002AD3-0000-1000-8000-00805f9b34fb")

    private var trainingStatus = TRAINING_STATUS_CODES.IDLE

    override fun getCharacteristicUUID(): UUID {
        return GATT_TRAINING_STATUS_CHARACTERISTIC_UUID
    }

    init {
        gattCharacteristic = BluetoothGattCharacteristic(
            GATT_TRAINING_STATUS_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }
    }

    override fun getData(): ByteArray {
        val flags = 0x00
        return byteArrayOf(flags.toByte(), trainingStatus.toByte())
    }

    override fun onReadRequest(device: BluetoothDevice, requestId: Int, offset: Int) {
        val data = getData()
        if (offset > data.size) {
            gattProfile.bluetoothGattServer!!.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            return
        }

        gattProfile.bluetoothGattServer!!.sendResponse(
            device,
            requestId,
            BluetoothGatt.GATT_SUCCESS,
            offset,
            data.copyOfRange(offset, data.size)
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

    fun setTrainingStatus(status: Int) {
        if (trainingStatus == status) {
            return
        }

        trainingStatus = status
        sendData(getData())
    }
}
