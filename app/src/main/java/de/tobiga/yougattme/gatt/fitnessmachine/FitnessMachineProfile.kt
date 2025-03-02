package de.tobiga.yougattme.gatt.fitnessmachine

import android.bluetooth.*
import android.util.Log
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.gatt.GattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import de.tobiga.yougattme.gatt.fitnessmachine.characteristics.*
import java.util.*

/**
 * Implementation of the Bluetooth GATT Time Profile.
 * https://www.bluetooth.com/specifications/adopted-specifications
 */
class FitnessMachineProfile(val machineType: String, val mainViewModel: ApplicationViewModel) : GattProfile() {


    private var dataCharacteristic: AbstractDataMeasurementCharacteristic
    private var controlPointCharacteristic: FitnessMachineControlPointCharacteristic

    val GATT_FITNESS_MACHINE_SERVICE_UUID: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")

    override fun getServiceUUID(): UUID {
        return GATT_FITNESS_MACHINE_SERVICE_UUID
    }


    init {
        when (machineType) {
            "indoor_bike" -> {
                dataCharacteristic = IndoorBikeDataCharacteristic(this, mainViewModel.ftmsIndoorBikeData)
                activeCharacteristics.add(dataCharacteristic);
            }
            "rower" -> {
                dataCharacteristic = RowerDataCharacteristic(this, mainViewModel.ftmsRowerData)
                activeCharacteristics.add(dataCharacteristic)
            }
            "treadmill" -> {
                dataCharacteristic = TreadmillDataCharacteristic(this, mainViewModel.ftmsTreadmillData)
                activeCharacteristics.add(dataCharacteristic)
            }
            "cross_trainer" -> {
                dataCharacteristic = CrossTrainerDataCharacteristic(this, mainViewModel.ftmsCrossTrainerData)
                activeCharacteristics.add(dataCharacteristic)
            }
            else -> {
                dataCharacteristic = IndoorBikeDataCharacteristic(this, mainViewModel.ftmsIndoorBikeData)
                activeCharacteristics.add(dataCharacteristic);
            }
        }


        controlPointCharacteristic = FitnessMachineControlPointCharacteristic(this, mainViewModel)
        activeCharacteristics.add(controlPointCharacteristic)


        activeCharacteristics.add(FitnessMachineStatusCharacteristic(this))
        activeCharacteristics.add(FitnessMachineFeatureCharacteristic(this))


    }

    override fun clearAllDevices() {
        dataCharacteristic.clientConfigDescriptor.registeredDevices.clear()
    }

    override fun onDeviceDisconnected(device: BluetoothDevice) {
        dataCharacteristic.clientConfigDescriptor.registeredDevices.remove(device)
    }

    override fun publishData() {
        dataCharacteristic.sendData()
    }


}
