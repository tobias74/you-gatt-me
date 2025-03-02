package de.tobiga.yougattme.gatt

import android.bluetooth.*
import android.bluetooth.le.AdvertiseData
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

abstract class GattProfile {
    var bluetoothGattServer: BluetoothGattServer? = null

    protected val activeCharacteristics = mutableSetOf<TobigaGattCharacteristic>()

    abstract fun onDeviceDisconnected(device: BluetoothDevice)
    abstract fun publishData()
    abstract fun clearAllDevices()
    abstract fun getServiceUUID(): UUID

    fun addMyServices(dataBuilder: AdvertiseData.Builder) {
        dataBuilder.addServiceUuid(ParcelUuid(getServiceUUID()))
    }

    fun createService(): BluetoothGattService {
        val service = BluetoothGattService(getServiceUUID(), BluetoothGattService.SERVICE_TYPE_PRIMARY)

        activeCharacteristics.forEach {
            service.addCharacteristic(it.gattCharacteristic)
        }

        return service
    }

    fun onCharacteristicReadRequest(bluetoothGattServer: BluetoothGattServer?, device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic):Boolean {

        val myCharacteristic = activeCharacteristics.find {
            it.getCharacteristicUUID() === characteristic.uuid
        }

        if (myCharacteristic !== null) {
            if (myCharacteristic.gattCharacteristic !== characteristic) {

            }
            myCharacteristic.onReadRequest(device, requestId, offset)
            return true
        } else {
            return false
        }

    }

    fun onCharacteristicWriteRequest(bluetoothGattServer: BluetoothGattServer?, device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray): Boolean {
        val myCharacteristic = activeCharacteristics.find {
            it.getCharacteristicUUID() === characteristic.uuid
        }

        if (myCharacteristic !== null) {
            if (myCharacteristic.gattCharacteristic !== characteristic) {

            }

            myCharacteristic.onWriteRequest(device, requestId, preparedWrite, responseNeeded, offset, value)
            return true
        } else {
            return false
        }
    }

    fun onDescriptorReadRequest(bluetoothGattServer: BluetoothGattServer?, device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor): Boolean {
        val myCharacteristic = activeCharacteristics.find {
            it.getCharacteristicUUID() === descriptor.characteristic.uuid
        }

        if (myCharacteristic !== null) {
            if (myCharacteristic.gattCharacteristic !== descriptor.characteristic) {

            }
            val tobigaGattDescriptor = myCharacteristic.getTobigaDescriptorByUUID(descriptor.uuid)

            if (tobigaGattDescriptor !== null) {
                tobigaGattDescriptor.onReadRequest(device, requestId, offset)
            } else {

            }
            return true
        } else {
            return false
        }
    }

    fun onDescriptorWriteRequest(bluetoothGattServer: BluetoothGattServer?, device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray): Boolean {
        val myCharacteristic = activeCharacteristics.find {
            it.getCharacteristicUUID() === descriptor.characteristic.uuid
        }

        if (myCharacteristic !== null) {
            if (myCharacteristic.gattCharacteristic !== descriptor.characteristic) {

            }
            myCharacteristic.getTobigaDescriptorByUUID(descriptor.uuid)?.onWriteRequest(device, requestId, preparedWrite, responseNeeded, offset, value)
            return true
        } else {
            return false
        }
    }


}