package de.tobiga.yougattme.gatt.cyclingpower

import android.bluetooth.*
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.gatt.GattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import de.tobiga.yougattme.gatt.cyclingpower.characteristics.CylcingPowerMeasurementCharacteristic
import java.util.*

class CyclingPowerProfile(val profileData: CyclingPowerProfileData) : GattProfile() {

    private var cyclingPowerMeasurementCharacteristic: AbstractDataMeasurementCharacteristic
    val GATT_CYCLING_POWER_SERVICE_UUID: UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")

    override fun getServiceUUID(): UUID {
       return GATT_CYCLING_POWER_SERVICE_UUID
    }

    init {
        cyclingPowerMeasurementCharacteristic = CylcingPowerMeasurementCharacteristic(this, profileData)
        activeCharacteristics.add(cyclingPowerMeasurementCharacteristic);
    }

    override fun clearAllDevices() {
        cyclingPowerMeasurementCharacteristic.clientConfigDescriptor.registeredDevices.clear()
    }

    override fun onDeviceDisconnected(device: BluetoothDevice) {
        cyclingPowerMeasurementCharacteristic.clientConfigDescriptor.registeredDevices.remove(device)
    }

    override fun publishData() {
        cyclingPowerMeasurementCharacteristic.sendData()
    }


}
