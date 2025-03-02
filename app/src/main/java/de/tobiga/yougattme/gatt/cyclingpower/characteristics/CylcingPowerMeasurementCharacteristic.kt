package de.tobiga.yougattme.gatt.cyclingpower.characteristics

import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.gatt.GattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import de.tobiga.yougattme.gatt.cyclingpower.CyclingPowerProfileData
import java.util.*

class CylcingPowerMeasurementCharacteristic (gattProfile: GattProfile, val profileData: CyclingPowerProfileData) : AbstractDataMeasurementCharacteristic(gattProfile) {

    val GATT_CYCLING_POWER_MEASUREMENT_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A63-0000-1000-8000-00805f9b34fb")

    override fun getCharacteristicUUID(): UUID {
        return GATT_CYCLING_POWER_MEASUREMENT_CHARACTERISTIC_UUID
    }

    init {
        gattCharacteristic = BluetoothGattCharacteristic(
            GATT_CYCLING_POWER_MEASUREMENT_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ)


        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }

    }


    override fun getData(): ByteArray {
        val field = ByteArray(14)
        field[0] = (0x00).toByte()
        field[1] = (0x00).toByte()
        field[2] = (0xFF and profileData.instantaneousPower).toByte()
        field[3] = (profileData.instantaneousPower shr 8).toByte()

        return field

    }

}