package de.tobiga.yougattme.gatt.deviceinfo

import android.bluetooth.BluetoothDevice
import de.tobiga.yougattme.gatt.SingleServiceGattProfile
import de.tobiga.yougattme.gatt.common.ReadOnlyCharacteristic
import java.util.*

class DeviceInfoProfile : SingleServiceGattProfile() {

    private val SERVICE_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")

    /**
     * Implements the abstract method from the base class to provide the
     * UUID that should be advertised.
     */
    override fun getServiceUUIDs(): List<UUID> = listOf(SERVICE_UUID)

    init {
        activeCharacteristics.add(ReadOnlyCharacteristic(this, UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb"), "Tobiga Inc.")) // Manufacturer
        activeCharacteristics.add(ReadOnlyCharacteristic(this, UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb"), "BLE Sim 3000")) // Model
        activeCharacteristics.add(ReadOnlyCharacteristic(this, UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb"), "1.0.0")) // Firmware
    }

    // These methods from the abstract base class must be implemented,
    // even if they do nothing for this simple profile.
    override fun clearAllDevices() {}
    override fun onDeviceDisconnected(device: BluetoothDevice) {}
    override fun triggerPeriodicTimer() {}
}
