package de.tobiga.yougattme.gatt.cyclingpower

import android.bluetooth.BluetoothDevice
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.gatt.SingleServiceGattProfile
import de.tobiga.yougattme.gatt.cyclingpower.characteristics.CyclingPowerMeasurementCharacteristic
import java.util.*

class CyclingPowerProfile(
    val profileData: ApplicationViewModel
) : SingleServiceGattProfile() {

    private val cyclingPowerMeasurementCharacteristic: CyclingPowerMeasurementCharacteristic
    val GATT_CYCLING_POWER_SERVICE_UUID: UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")

    override fun getServiceUUIDs(): List<UUID> {
       return listOf(GATT_CYCLING_POWER_SERVICE_UUID)
    }

    init {
        cyclingPowerMeasurementCharacteristic = CyclingPowerMeasurementCharacteristic(this, profileData)
        activeCharacteristics.add(cyclingPowerMeasurementCharacteristic)
    }

    override fun clearAllDevices() {
        cyclingPowerMeasurementCharacteristic.clientConfigDescriptor.registeredDevices.clear()
    }

    override fun onDeviceDisconnected(device: BluetoothDevice) {
        cyclingPowerMeasurementCharacteristic.clientConfigDescriptor.registeredDevices.remove(device)
    }

    override fun triggerPeriodicTimer() {
        cyclingPowerMeasurementCharacteristic.publishMeasurementData()
    }


}
