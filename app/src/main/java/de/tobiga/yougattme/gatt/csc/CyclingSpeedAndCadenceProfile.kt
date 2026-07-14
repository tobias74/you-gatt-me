package de.tobiga.yougattme.gatt.csc

import android.bluetooth.BluetoothDevice
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.gatt.SingleServiceGattProfile
import de.tobiga.yougattme.gatt.csc.characteristics.CSCMeasurementCharacteristic
import java.util.*

class CyclingSpeedAndCadenceProfile(
    val profileData: ApplicationViewModel
) : SingleServiceGattProfile() {

    private val measurementCharacteristic: CSCMeasurementCharacteristic
    private val GATT_CSC_SERVICE_UUID: UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")

    override fun getServiceUUIDs(): List<UUID> {
        return listOf(GATT_CSC_SERVICE_UUID)
    }

    init {
        measurementCharacteristic = CSCMeasurementCharacteristic(this, profileData)
        activeCharacteristics.add(measurementCharacteristic)
    }

    override fun clearAllDevices() {
        measurementCharacteristic.clientConfigDescriptor.registeredDevices.clear()
    }

    override fun onDeviceDisconnected(device: BluetoothDevice) {
        measurementCharacteristic.clientConfigDescriptor.registeredDevices.remove(device)
    }

    override fun triggerPeriodicTimer() {
        measurementCharacteristic.publishMeasurementData()
    }

}