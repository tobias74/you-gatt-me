package de.tobiga.yougattme.gatt.rsc

import android.bluetooth.BluetoothDevice
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.gatt.GattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import de.tobiga.yougattme.gatt.rsc.charateristics.RSCMeasurementCharacteristic
import java.util.*

class RunningSpeedAndCadenceProfile(val profileData: RunningSpeedAndCadenceProfileData) : GattProfile() {

    private var measurementCharacteristic: AbstractDataMeasurementCharacteristic
    val GATT_RSC_SERVICE_UUID: UUID = UUID.fromString("00001814-0000-1000-8000-00805f9b34fb")

    override fun getServiceUUID(): UUID {
        return GATT_RSC_SERVICE_UUID
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

    override fun publishData() {
        measurementCharacteristic.sendData()
    }

}