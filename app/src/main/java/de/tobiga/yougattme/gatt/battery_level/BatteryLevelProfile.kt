package de.tobiga.yougattme.gatt.battery_level

import android.bluetooth.BluetoothDevice
import de.tobiga.yougattme.gatt.SingleServiceGattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import java.util.*

class BatteryLevelProfile(
    batteryLevelProvider: () -> Int
) : SingleServiceGattProfile() {

    private var measurementCharacteristic: AbstractDataMeasurementCharacteristic
    val GATT_BATTERY_LEVEL_SERVICE_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")

    /**
     * Implements the abstract method from the base class to provide the
     * UUID that should be advertised.
     */
    override fun getServiceUUIDs(): List<UUID> {
        return listOf(GATT_BATTERY_LEVEL_SERVICE_UUID)
    }

    init {
        measurementCharacteristic = BatteryLevelCharacteristic(this, batteryLevelProvider)
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
