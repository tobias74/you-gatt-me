package de.tobiga.yougattme.gatt.rsc

import android.bluetooth.BluetoothDevice
import de.tobiga.yougattme.gatt.SingleServiceGattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import de.tobiga.yougattme.gatt.rsc.charateristics.RSCMeasurementCharacteristic
import java.util.*

class RunningSpeedAndCadenceProfile(
    val profileData: RunningSpeedAndCadenceProfileData
) : SingleServiceGattProfile() {

    private var measurementCharacteristic: AbstractDataMeasurementCharacteristic
    val GATT_RSC_SERVICE_UUID: UUID = UUID.fromString("00001814-0000-1000-8000-00805f9b34fb")

    override fun getServiceUUIDs(): List<UUID> {
        return listOf(GATT_RSC_SERVICE_UUID)
    }

    init {
        measurementCharacteristic = RSCMeasurementCharacteristic(this, profileData)
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