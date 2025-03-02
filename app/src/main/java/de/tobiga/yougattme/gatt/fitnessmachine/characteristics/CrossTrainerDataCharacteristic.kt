package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import de.tobiga.yougattme.gatt.GattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import de.tobiga.yougattme.gatt.fitnessmachine.CrossTrainerData
import java.util.*
import kotlin.experimental.*

class CrossTrainerDataCharacteristic(
    gattProfile: GattProfile,
    val crossTrainerData: CrossTrainerData
) : AbstractDataMeasurementCharacteristic(gattProfile) {

    // Bitmask flags for the first set of fields.
    // Note: BITMASK_STEP_COUNT now indicates that step data (instantaneous, average, and total) is included.
    val BITMASK_STEP_COUNT = (0b00001000).toByte()
    val BITMASK_STRIDE_COUNT = (0b00010000).toByte()
    // Bitmask for Average Speed (if needed later)
    val BITMASK_AVERAGE_SPEED = (0b00000010).toByte()
    // Bitmask for Total Distance (optional)
    val BITMASK_TOTAL_DISTANCE = (0b00000100).toByte()

    // Flags for the second set of fields.
    val BITMASK2_INSTANTANEOUS_POWER =       (0b00000001).toByte()
    val BITMASK2_AVERAGE_POWER =             (0b00000010).toByte()
    val BITMASK2_EXPENDED_ENERGY =           (0b00000100).toByte()
    val BITMASK2_HEART_RATE =                (0b00001000).toByte()
    val BITMASK2_ELAPSED_TIME =              (0b00100000).toByte()
    val BITMASK2_REMAINING_TIME =            (0b01000000).toByte()

    val GATT_CROSS_TRAINER_DATA_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("00002ACE-0000-1000-8000-00805f9b34fb")

    override fun getCharacteristicUUID(): UUID {
        return GATT_CROSS_TRAINER_DATA_CHARACTERISTIC_UUID
    }

    init {
        gattCharacteristic = BluetoothGattCharacteristic(
            GATT_CROSS_TRAINER_DATA_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }
    }

    override fun getData(): ByteArray {

        val field = ByteArray(100)
        var bitmask1 = (0x00).toByte()
        var bitmask2 = (0x00).toByte()

        // Set flags for the second byte (e.g. instantaneous power, heart rate, elapsed time).
        if (crossTrainerData.includeInstantaneousPower)
            bitmask2 = bitmask2 or BITMASK2_INSTANTANEOUS_POWER
        if (crossTrainerData.includeHeartRate)
            bitmask2 = bitmask2 or BITMASK2_HEART_RATE
        if (crossTrainerData.includeElapsedTime)
            bitmask2 = bitmask2 or BITMASK2_ELAPSED_TIME

        // Set flags for the first byte.
        if (crossTrainerData.includeTotalDistance)
            bitmask1 = bitmask1 or BITMASK_TOTAL_DISTANCE

        // Always include step data.
        bitmask1 = bitmask1 or BITMASK_STEP_COUNT
        bitmask1 = bitmask1 or BITMASK_STRIDE_COUNT

        field[0] = bitmask1
        field[1] = bitmask2
        field[2] = 0.toByte() // Reserved

        var index = 3

        // Field 1: Instantaneous Speed (2 bytes, little-endian)
        val instSpeed = (crossTrainerData.instantaneousSpeed * 100).toInt()
        field[index] = (instSpeed and 0xFF).toByte()
        field[index + 1] = ((instSpeed shr 8) and 0xFF).toByte()
        index += 2

        // Field 3: Total Distance (3 bytes, little-endian), if enabled.
        if ((bitmask1 and BITMASK_TOTAL_DISTANCE) != 0.toByte()) {
            val dist = crossTrainerData.totalDistance
            field[index] = (dist and 0xFF).toByte()
            field[index + 1] = ((dist shr 8) and 0xFF).toByte()
            field[index + 2] = ((dist shr 16) and 0xFF).toByte()
            index += 3
        }


        // Field 2: Step Data (5 bytes) — in order:
        // a) Instantaneous Steps Per Minute (1 byte)

        // Instantaneous Steps Per Minute (16 bits)
        val instSPM = crossTrainerData.instantaneousStepsPerMinute.toInt()
        field[index] = (instSPM and 0xFF).toByte()
        field[index + 1] = ((instSPM shr 8) and 0xFF).toByte()
        index += 2

        // Average Steps Per Minute (16 bits)
        val avgSPM = crossTrainerData.averageStepsPerMinute.toInt()
        field[index] = (avgSPM and 0xFF).toByte()
        field[index + 1] = ((avgSPM shr 8) and 0xFF).toByte()
        index += 2


        // c) Total Step Count (3 bytes, little-endian)
        val totalSteps = crossTrainerData.totalStepCount
        field[index] = (totalSteps and 0xFF).toByte()
        field[index + 1] = ((totalSteps shr 8) and 0xFF).toByte()
        index += 2


        // Field 4: Instantaneous Power (2 bytes, little-endian), if enabled.
        if (crossTrainerData.includeInstantaneousPower) {
            val instPower = crossTrainerData.instantaneousPower
            field[index] = (instPower and 0xFF).toByte()
            field[index + 1] = ((instPower shr 8) and 0xFF).toByte()
            index += 2
        }

        // Field 5: Heart Rate (1 byte, if enabled)
        if (crossTrainerData.includeHeartRate) {
            field[index] = crossTrainerData.heartRate.toByte()
            index++
        }

        // Field 6: Elapsed Time (2 bytes, little-endian, if enabled)
        if (crossTrainerData.includeElapsedTime) {
            val elapsed = crossTrainerData.elapsedTime.toInt() // assuming elapsedTime is a Float internally
            field[index] = (elapsed and 0xFF).toByte()
            field[index + 1] = ((elapsed shr 8) and 0xFF).toByte()
            index += 2
        }

        return field.copyOf(index)
    }
}
