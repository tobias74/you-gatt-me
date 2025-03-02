package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import de.tobiga.yougattme.gatt.GattProfile
import de.tobiga.yougattme.gatt.common.AbstractDataMeasurementCharacteristic
import de.tobiga.yougattme.gatt.fitnessmachine.TreadmillData
import java.util.*

class TreadmillDataCharacteristic(gattProfile: GattProfile, val treadmillData: TreadmillData) : AbstractDataMeasurementCharacteristic(gattProfile) {

    // UUID for Treadmill Data characteristic (commonly 2ACD)
    val GATT_TREADMILL_DATA_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("00002ACD-0000-1000-8000-00805f9b34fb")

    override fun getCharacteristicUUID(): UUID = GATT_TREADMILL_DATA_CHARACTERISTIC_UUID

    init {
        gattCharacteristic = BluetoothGattCharacteristic(
            GATT_TREADMILL_DATA_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }
    }

    override fun getData(): ByteArray {

        // Build the flags field.
        var flags = 0
        // Bit assignments per our grouping:
        // Bit 1: Average Speed
        if (treadmillData.includeAverageSpeed) flags = flags or (1 shl 1)
        // Bit 2: Total Distance (always included; flag default true)
        if (treadmillData.includeTotalDistance) flags = flags or (1 shl 2)
        // Bit 5: Instantaneous Pace
        if (treadmillData.includeInstantaneousPace) flags = flags or (1 shl 5)
        // Bit 6: Average Pace
        if (treadmillData.includeAveragePace) flags = flags or (1 shl 6)
        // Bit 8: Heart Rate
        if (treadmillData.includeHeartRate) flags = flags or (1 shl 8)

        // Bit 10: Elapsed Time (mandatory; flag default true)
        flags = flags or (1 shl 10)

        // Bit 13: Steps
        if (treadmillData.includeSteps) flags = flags or (1 shl 13)

        // Calculate packet length (starting with 2 bytes for flags)
        var length = 2
        length += 2 // Instantaneous Speed (mandatory: uint16)
        if (treadmillData.includeAverageSpeed) length += 2
        if (treadmillData.includeTotalDistance) length += 3
        if (treadmillData.includeInstantaneousPace) length += 1 // uint8
        if (treadmillData.includeAveragePace) length += 1 // uint8
        if (treadmillData.includeHeartRate) length += 1
        length += 2 // Elapsed Time (mandatory: uint16)
        if (treadmillData.includeSteps) length += 3

        val data = ByteArray(length)
        // Write flags (2 bytes, little-endian)
        data[0] = (flags and 0xFF).toByte()
        data[1] = ((flags shr 8) and 0xFF).toByte()
        var index = 2

        // Field 1: Instantaneous Speed (uint16, 0.01 km/h)
        val instSpeed = (treadmillData.instantaneousSpeed * 100).toInt()
        data[index++] = (instSpeed and 0xFF).toByte()
        data[index++] = ((instSpeed shr 8) and 0xFF).toByte()

        // Field 2: Average Speed (if present)
        if (treadmillData.includeAverageSpeed) {
            val avgSpeed = (treadmillData.averageSpeed * 100).toInt()
            data[index++] = (avgSpeed and 0xFF).toByte()
            data[index++] = ((avgSpeed shr 8) and 0xFF).toByte()
        }

        // Field 3: Total Distance (uint24, metres)
        if (treadmillData.includeTotalDistance) {
            val distance = treadmillData.totalDistance
            data[index++] = (distance and 0xFF).toByte()
            data[index++] = ((distance shr 8) and 0xFF).toByte()
            data[index++] = ((distance shr 16) and 0xFF).toByte()
        }

        // Field 6: Instantaneous Pace (if present, uint8, 0.1 km/m)
        if (treadmillData.includeInstantaneousPace) {
            val instPace = (treadmillData.instantaneousPace * 10).toInt()
            data[index++] = (instPace and 0xFF).toByte()
        }

        // Field 7: Average Pace (if present, uint8, 0.1 km/m)
        if (treadmillData.includeAveragePace) {
            val avgPace = (treadmillData.averagePace * 10).toInt()
            data[index++] = (avgPace and 0xFF).toByte()
        }

        // Field 9: Heart Rate (if present, uint8)
        if (treadmillData.includeHeartRate) {
            val hr = treadmillData.heartRate
            data[index++] = (hr and 0xFF).toByte()
        }

        // Field 11: Elapsed Time (mandatory, uint16)
        val et = treadmillData.elapsedTime.toInt()
        data[index++] = (et and 0xFF).toByte()
        data[index++] = ((et shr 8) and 0xFF).toByte()

        // Field 14: Steps (if present, uint24)
        if (treadmillData.includeSteps) {
            val steps = treadmillData.steps.toInt()

            data[index++] = (steps and 0xFF).toByte()
            data[index++] = ((steps shr 8) and 0xFF).toByte()
            data[index++] = ((steps shr 16) and 0xFF).toByte()
        }

        return data
    }
}
