package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.gatt.GattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import java.util.*

object STATUS_CODES {
    const val RESET = 0x01
    const val FITNESS_MACHINE_STOPPED_BY_USER = 0x02
    const val FITNESS_MACHINE_STOPPED_BY_SAFETY_KEY = 0x03
    const val FITNESS_MACHINE_STARTED_BY_USER = 0x04
    const val TARGET_SPEED_CHANGED = 0x05
    const val TARGET_INCLINE_CHANGED = 0x06
    const val TARGET_RESISTANCE_LEVEL_CHANGED = 0x07
    const val TARGET_POWER_CHANGED = 0x08
    const val INDOOR_BIKE_SIMULATION_PARAMETER_CHANGED = 0x12

}

class FitnessMachineStatusCharacteristic(gattProfile: GattProfile) : AbstractDataMeasurementCharacteristic(gattProfile) {
    val GATT_FITNESS_MACHINE_STATUS_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002ADA-0000-1000-8000-00805f9b34fb")

    override fun getCharacteristicUUID(): UUID {
        return GATT_FITNESS_MACHINE_STATUS_CHARACTERISTIC_UUID
    }


    init {
        gattCharacteristic = BluetoothGattCharacteristic(
            GATT_FITNESS_MACHINE_STATUS_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ)


        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }

    }

    override fun getData(): ByteArray {
        val field = ByteArray(4)
        field[0] = (0x04).toByte()
        field[1] = (0x04).toByte()
        field[2] = (0x04).toByte()
        field[3] = (0x04).toByte()

        return field
    }


}