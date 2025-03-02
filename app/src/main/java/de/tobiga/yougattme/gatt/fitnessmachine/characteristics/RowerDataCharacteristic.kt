package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import de.tobiga.yougattme.gatt.GattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import de.tobiga.yougattme.gatt.fitnessmachine.RowerData
import java.util.*
import kotlin.experimental.or

class RowerDataCharacteristic(gattProfile: GattProfile, val rowerData: RowerData) : AbstractDataMeasurementCharacteristic(gattProfile) {

    private val secondPer500: Int = 180
    val GATT_ROWER_DATA_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002AD1-0000-1000-8000-00805f9b34fb")

    val BITMASK_INSTANTANEOUS_PACE = (0b00001000).toByte()
    val BITMASK_INSTANTANEOUS_POWER = (0b00100000).toByte()

    val BITMASK2_HEART_RATE = (0b00000010).toByte()

    override fun getCharacteristicUUID(): UUID {
        return GATT_ROWER_DATA_CHARACTERISTIC_UUID
    }

    init {
        gattCharacteristic = BluetoothGattCharacteristic(
            GATT_ROWER_DATA_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ)

        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }
    }

    override fun getData(): ByteArray {

        val field = ByteArray(100)
        var bitmask1 = (0x00).toByte()
        var bitmask2 = (0x00).toByte()

        if (rowerData.includeInstantaneousPace) {
            bitmask1 = bitmask1 or BITMASK_INSTANTANEOUS_PACE
        }

        if (rowerData.includeInstantaneousPower) {
            bitmask1 = bitmask1 or BITMASK_INSTANTANEOUS_POWER
        }

        if (rowerData.includeHeartRate) {
            bitmask2 = bitmask2 or BITMASK2_HEART_RATE
        }

        field[0] = bitmask1
        field[1] = bitmask2
        val strokeCount = rowerData.strokeCount.toInt()
        field[2] = ((rowerData.strokeRate * 2).toInt() and 0xFF).toByte()
        field[3] = (0xFF and strokeCount).toByte()
        field[4] = (strokeCount shr 8).toByte()

        var index = 5

        if (rowerData.includeInstantaneousPace) {
            field[index] = (0xFF and rowerData.instantaneousPace).toByte()
            field[index+1] = (rowerData.instantaneousPace shr 8).toByte()
            index +=2
        }

        if (rowerData.includeInstantaneousPower) {
            field[index] = (0xFF and rowerData.instantaneousPower).toByte()
            field[index+1] = (rowerData.instantaneousPower shr 8).toByte()
            index +=2
        }

        if (rowerData.includeHeartRate) {
            field[index] = (0x45).toByte()
            index += 1
        }

        return field.copyOf(index)
    }
}