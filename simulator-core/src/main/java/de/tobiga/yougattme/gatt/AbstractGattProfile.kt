package de.tobiga.yougattme.gatt

import android.bluetooth.*
import android.bluetooth.le.AdvertiseData
import android.os.ParcelUuid
import android.util.Log
import java.util.*

/**
 * The new, unified abstract base class for all GATT profiles.
 * It now uses a ServiceCreationStrategy to build its services, making it
 * flexible enough to handle both single and multi-service peripherals.
 *
 * @param serviceCreationStrategy The strategy object responsible for building the services.
 */
abstract class AbstractGattProfile() {
    var bluetoothGattServer: BluetoothGattServer? = null
    private val negotiatedMtuByAddress = mutableMapOf<String, Int>()

    // This remains the same.
    val activeCharacteristics = mutableSetOf<BaseGattCharacteristic>()

    // --- Abstract Methods ---
    abstract fun onDeviceDisconnected(device: BluetoothDevice)
    abstract fun triggerPeriodicTimer()
    abstract fun clearAllDevices()
    abstract fun getServiceUUIDs(): List<UUID>

    open fun destroy() {

    }

    open fun onDeviceConnected(device: BluetoothDevice) {
        negotiatedMtuByAddress[device.address] = DEFAULT_ATT_MTU
        Log.d("AbstractGattProfile", "Device connected: $device")
    }

    open fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
        negotiatedMtuByAddress[device.address] = mtu.coerceAtLeast(DEFAULT_ATT_MTU)
    }

    fun getNotificationPayloadSize(device: BluetoothDevice): Int {
        return ((negotiatedMtuByAddress[device.address] ?: DEFAULT_ATT_MTU) - ATT_NOTIFICATION_OVERHEAD)
            .coerceAtLeast(1)
    }

    protected fun forgetDeviceMtu(device: BluetoothDevice) {
        negotiatedMtuByAddress.remove(device.address)
    }

    protected fun clearDeviceMtus() {
        negotiatedMtuByAddress.clear()
    }

    abstract fun createServices(gattServer: BluetoothGattServer): List<BluetoothGattService>

    fun addMyFirstServiceToAdvertisement(dataBuilder: AdvertiseData.Builder) {
        getServiceUUIDs().firstOrNull()?.let { uuid ->
            Log.d("TobigaDebug","Adding on service uuid to data builder " + uuid)
            dataBuilder.addServiceUuid(ParcelUuid(uuid))
        }
    }

    // --- GATT Request Handlers (No changes needed here) ---

    open fun onCharacteristicReadRequest(server: BluetoothGattServer?, device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic): Boolean {
        val myCharacteristic = activeCharacteristics.find { it.getCharacteristicUUID() == characteristic.uuid }
        if (myCharacteristic != null) {
            myCharacteristic.onReadRequest(device, requestId, offset)
            return true
        }
        Log.e("AbstractGattProfile", "Characteristic read request failed: UUID not found - ${characteristic.uuid}")
        return false
    }

    fun onCharacteristicWriteRequest(server: BluetoothGattServer?, device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray): Boolean {
        val myCharacteristic = activeCharacteristics.find { it.getCharacteristicUUID() == characteristic.uuid }
        if (myCharacteristic != null) {
            myCharacteristic.onWriteRequest(device, requestId, preparedWrite, responseNeeded, offset, value)
            return true
        }
        Log.e("AbstractGattProfile", "Characteristic write request failed: UUID not found - ${characteristic.uuid}")
        return false
    }

    fun onDescriptorReadRequest(server: BluetoothGattServer?, device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor): Boolean {
        val myCharacteristic = activeCharacteristics.find { it.getCharacteristicUUID() == descriptor.characteristic.uuid }
        myCharacteristic?.getTobigaDescriptorByUUID(descriptor.uuid)?.onReadRequest(device, requestId, offset)
        return myCharacteristic != null
    }

    fun onDescriptorWriteRequest(server: BluetoothGattServer?, device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray): Boolean {
        val myCharacteristic = activeCharacteristics.find { it.getCharacteristicUUID() == descriptor.characteristic.uuid }
        myCharacteristic?.getTobigaDescriptorByUUID(descriptor.uuid)?.onWriteRequest(device, requestId, preparedWrite, responseNeeded, offset, value)
        return myCharacteristic != null
    }

    private companion object {
        const val DEFAULT_ATT_MTU = 23
        const val ATT_NOTIFICATION_OVERHEAD = 3
    }
}
