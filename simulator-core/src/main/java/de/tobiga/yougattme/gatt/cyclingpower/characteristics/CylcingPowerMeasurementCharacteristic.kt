package de.tobiga.yougattme.gatt.cyclingpower.characteristics

import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import java.util.*

class CyclingPowerMeasurementCharacteristic(
    gattProfile: AbstractGattProfile,
    private val profileData: ApplicationViewModel
) : AbstractDataMeasurementCharacteristic(gattProfile) {

    val GATT_CYCLING_POWER_MEASUREMENT_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A63-0000-1000-8000-00805f9b34fb")

    // Define bitmasks for all features we will include
    val BITMASK_WHEEL_REV_PRESENT = (1 shl 4) // Bit 4
    val BITMASK_CRANK_REV_PRESENT = (1 shl 5) // Bit 5

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
        // --- 1. Combine flags for all present features ---
        val flags = BITMASK_WHEEL_REV_PRESENT or BITMASK_CRANK_REV_PRESENT

        // --- 2. Encode all data fields using helper functions ---
        val flagsBytes = encodeShort(flags)
        val powerBytes = encodeShort(profileData.cyclingPowerWatts)

        // Wheel Data
        val wheelRevBytes = encodeInt(profileData.cumulativeWheelRevolutions.toUInt())

        val lastWheelEventTimeBle = ((profileData.lastWheelEventTime * 2048) / 1000).toUInt()
        val lastWheelEventTimeBytes = encodeShort(lastWheelEventTimeBle)

        // Crank Data
        val crankRevBytes = encodeShort(profileData.cumulativeCrankRevolutions.toUInt())
        val lastCrankEventTimeBle = ((profileData.lastCrankEventTime * 1024) / 1000).toUInt()
        val lastCrankEventTimeBytes = encodeShort(lastCrankEventTimeBle)

        // --- 3. Assemble the final 14-byte packet ---
        val field = ByteArray(14)
        var i = 0

        // Bytes 0-1: Flags
        field[i++] = flagsBytes[0]
        field[i++] = flagsBytes[1]

        // Bytes 2-3: Instantaneous Power (Mandatory)
        field[i++] = powerBytes[0]
        field[i++] = powerBytes[1]

        // --- Fields must be in order of their flag's bit number ---

        // Bytes 4-9: Wheel Revolution Data (Bit 4)
        field[i++] = wheelRevBytes[0]
        field[i++] = wheelRevBytes[1]
        field[i++] = wheelRevBytes[2]
        field[i++] = wheelRevBytes[3]
        field[i++] = lastWheelEventTimeBytes[0]
        field[i++] = lastWheelEventTimeBytes[1]

        // Bytes 10-13: Crank Revolution Data (Bit 5)
        field[i++] = crankRevBytes[0]
        field[i++] = crankRevBytes[1]
        field[i++] = lastCrankEventTimeBytes[0]
        field[i++] = lastCrankEventTimeBytes[1]

        return field
    }

    // --- Helper Functions for Encoding (Little-Endian) ---

    // Encodes a 2-byte value (UINT16 or SINT16)
    private fun encodeShort(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte()
        )
    }

    // Overload for UInt
    private fun encodeShort(value: UInt): ByteArray {
        return encodeShort(value.toInt())
    }

    // Encodes a 4-byte value (UINT32)
    private fun encodeInt(value: UInt): ByteArray {
        return byteArrayOf(
            (value and 0xFFu).toByte(),
            ((value shr 8) and 0xFFu).toByte(),
            ((value shr 16) and 0xFFu).toByte(),
            ((value shr 24) and 0xFFu).toByte()
        )
    }
}
