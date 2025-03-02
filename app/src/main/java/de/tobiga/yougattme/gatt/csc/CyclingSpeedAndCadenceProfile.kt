package de.tobiga.yougattme.gatt.csc

import android.bluetooth.BluetoothDevice
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.gatt.GattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import de.tobiga.yougattme.gatt.csc.characteristics.CSCMeasurementCharacteristic
import java.util.*

class CyclingSpeedAndCadenceProfile(val profileData: CyclingSpeedAndCadenceProfileData) : GattProfile() {

    private var measurementCharacteristic: AbstractDataMeasurementCharacteristic
    val GATT_CSC_SERVICE_UUID: UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")

    override fun getServiceUUID(): UUID {
        return GATT_CSC_SERVICE_UUID
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

    override fun publishData() {
        measurementCharacteristic.sendData()
    }

}