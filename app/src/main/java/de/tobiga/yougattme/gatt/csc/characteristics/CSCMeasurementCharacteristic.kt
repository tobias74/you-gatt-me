package de.tobiga.yougattme.gatt.csc.characteristics

import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.gatt.GattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import de.tobiga.yougattme.gatt.csc.CyclingSpeedAndCadenceProfileData
import java.util.*
import kotlin.experimental.or

class CSCMeasurementCharacteristic(gattProfile: GattProfile, val profileData: CyclingSpeedAndCadenceProfileData) : AbstractDataMeasurementCharacteristic(gattProfile) {

    val GATT_CSC_MEASUREMENT_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A5B-0000-1000-8000-00805f9b34fb")


    val BITMASK_WHEEL_REV_PRESENT =                  (0b00000001).toByte()
    val BITMASK_CRANK_REV_PRESENT =                  (0b00000010).toByte()

    override fun getCharacteristicUUID(): UUID {
        return GATT_CSC_MEASUREMENT_CHARACTERISTIC_UUID
    }

    init {

        gattCharacteristic = BluetoothGattCharacteristic(
            GATT_CSC_MEASUREMENT_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ)


        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }

    }

    override fun getData(): ByteArray {

        val wheelRevBytes = encodeWheelRevolutions(profileData.cumulativeWheelRevolutions.toUInt())
        val lastWheelEventTimeBytes = encodeEventTime(profileData.lastWheelEventTime.toUInt())
        val crankRevBytes = encodeCrankRevolutions(profileData.cumulativeCrankRevolutions.toUInt())
        val lastCrankEventTimeBytes = encodeEventTime(profileData.lastCrankEventTime.toUInt())

        val flags = (BITMASK_CRANK_REV_PRESENT or BITMASK_WHEEL_REV_PRESENT)
        val field = ByteArray(11)

        field[0] = flags

        field[1] = wheelRevBytes[0]
        field[2] = wheelRevBytes[1]
        field[3] = wheelRevBytes[2]
        field[4] = wheelRevBytes[3]

        field[5] = lastWheelEventTimeBytes[0]
        field[6] = lastWheelEventTimeBytes[1]

        field[7] = crankRevBytes[0]
        field[8] = crankRevBytes[1]

        field[9] = lastCrankEventTimeBytes[0]
        field[10] = lastCrankEventTimeBytes[1]


        return field

    }

    private fun encodeWheelRevolutions(wheelRevolutions: UInt): ByteArray {
        return byteArrayOf(
            (wheelRevolutions and 0xFFu).toByte(),
            ((wheelRevolutions shr 8) and 0xFFu).toByte(),
            ((wheelRevolutions shr 16) and 0xFFu).toByte(),
            ((wheelRevolutions shr 24) and 0xFFu).toByte()
        )
    }

    private fun encodeCrankRevolutions(wheelRevolutions: UInt): ByteArray {
        return byteArrayOf(
            (wheelRevolutions and 0xFFu).toByte(),
            ((wheelRevolutions shr 8) and 0xFFu).toByte(),
        )
    }

    private fun encodeEventTime(eventTime: UInt): ByteArray {
        return byteArrayOf(
            (eventTime and 0xFFu).toByte(),
            ((eventTime shr 8) and 0xFFu).toByte(),
        )
    }

}
