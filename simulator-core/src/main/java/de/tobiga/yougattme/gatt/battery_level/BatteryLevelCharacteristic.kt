package de.tobiga.yougattme.gatt.battery_level

import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import java.util.*

class BatteryLevelCharacteristic(
    gattProfile: AbstractGattProfile,
    val batteryLevelProvider: () -> Int
) : AbstractDataMeasurementCharacteristic(gattProfile) {

    val BATTERY_LEVEL_MEASUREMENT_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")

    override fun getCharacteristicUUID(): UUID {
        return BATTERY_LEVEL_MEASUREMENT_CHARACTERISTIC_UUID
    }

    init {

        gattCharacteristic = BluetoothGattCharacteristic(
            BATTERY_LEVEL_MEASUREMENT_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ)


        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }

    }

    override fun getData(): ByteArray {
        val level = batteryLevelProvider().coerceIn(0, 100)
        val payload = byteArrayOf(level.toByte())
        return payload
    }

}
