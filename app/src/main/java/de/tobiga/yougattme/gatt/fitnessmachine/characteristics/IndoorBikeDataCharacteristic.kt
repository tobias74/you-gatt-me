package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import android.bluetooth.*
import android.util.Log
import de.tobiga.yougattme.gatt.GattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import de.tobiga.yougattme.gatt.fitnessmachine.IndoorBikeData
import java.util.*
import kotlin.experimental.or

class IndoorBikeDataCharacteristic(gattProfile: GattProfile, val indoorBikeData: IndoorBikeData) : AbstractDataMeasurementCharacteristic(gattProfile) {

    val GATT_INDOOR_BIKE_DATA_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002AD2-0000-1000-8000-00805f9b34fb")

    override fun getCharacteristicUUID(): UUID {
        return GATT_INDOOR_BIKE_DATA_CHARACTERISTIC_UUID
    }

    // Flag Bit Masks
    val BITMASK_INSTANTANEOUS_CADENCE = (0b00000100).toByte()
    val BITMASK_INSTANTANEOUS_POWER = (0b01000000).toByte()
    val BITMASK_TOTAL_DISTANCE = (0b00010000).toByte()  // New: Total Distance Present
    val BITMASK2_HEART_RATE = (0b00000010).toByte()
    val BITMASK2_ELAPSED_TIME = (0b00001000).toByte()   // New: Elapsed Time Present

    init {
        gattCharacteristic = BluetoothGattCharacteristic(
            GATT_INDOOR_BIKE_DATA_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ)

        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }
    }

    fun calculateHundredthKmPerHour(speedKmH: Float): Int {
        return (speedKmH / 0.01).toInt()
    }

    override fun getData(): ByteArray {
        val field = ByteArray(100)  // Temporary oversized array
        var firstFlagByte = (0x00).toByte()
        var secondFlagByte = (0x00).toByte()
        var index = 2  // Start after where flags will be

        // Write speed first (always included)
        val instantSpeedHundredth = calculateHundredthKmPerHour(indoorBikeData.instantaneousSpeed)
        field[index++] = (instantSpeedHundredth and 0xFF).toByte()
        field[index++] = (instantSpeedHundredth shr 8).toByte()

        // Include cadence
        firstFlagByte = firstFlagByte or BITMASK_INSTANTANEOUS_CADENCE
        val doubledInstantCadence = indoorBikeData.instantaneousCadence * 2
        field[index++] = (doubledInstantCadence and 0xFF).toByte()
        field[index++] = (doubledInstantCadence shr 8).toByte()

        // Include total distance (3 bytes, little-endian)
        firstFlagByte = firstFlagByte or BITMASK_TOTAL_DISTANCE
        val totalDistanceInt = indoorBikeData.getTotalDistanceInt()
        field[index++] = (totalDistanceInt and 0xFF).toByte()
        field[index++] = ((totalDistanceInt shr 8) and 0xFF).toByte()
        field[index++] = ((totalDistanceInt shr 16) and 0xFF).toByte()


        // Include power
        firstFlagByte = firstFlagByte or BITMASK_INSTANTANEOUS_POWER
        field[index++] = (indoorBikeData.instantaneousPower and 0xFF).toByte()
        field[index++] = (indoorBikeData.instantaneousPower shr 8).toByte()

        // Include heart rate (1 byte, if present)
        if (indoorBikeData.includeHeartRate) {
            secondFlagByte = secondFlagByte or BITMASK2_HEART_RATE
            field[index++] = (indoorBikeData.heartRate).toByte()
        }


        // Include elapsed time (2 bytes, little-endian)
        secondFlagByte = secondFlagByte or BITMASK2_ELAPSED_TIME
        val elapsedTimeInt = indoorBikeData.getElapsedTimeInt()
        field[index++] = (elapsedTimeInt and 0xFF).toByte()
        field[index++] = ((elapsedTimeInt shr 8) and 0xFF).toByte()


        // Write flags at the start
        field[0] = firstFlagByte
        field[1] = secondFlagByte

        // Return only the bytes we used
        return field.copyOf(index)
    }
}
