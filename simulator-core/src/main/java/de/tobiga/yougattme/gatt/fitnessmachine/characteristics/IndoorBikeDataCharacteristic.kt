package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.fitnessmachine.IndoorBikeData
import java.util.UUID

private object IndoorBikeDataFlags {
    const val AVERAGE_SPEED_PRESENT = 1
    const val INSTANTANEOUS_CADENCE_PRESENT = 2
    const val AVERAGE_CADENCE_PRESENT = 3
    const val TOTAL_DISTANCE_PRESENT = 4
    const val RESISTANCE_LEVEL_PRESENT = 5
    const val INSTANTANEOUS_POWER_PRESENT = 6
    const val AVERAGE_POWER_PRESENT = 7
    const val EXPENDED_ENERGY_PRESENT = 8
    const val HEART_RATE_PRESENT = 9
    const val METABOLIC_EQUIVALENT_PRESENT = 10
    const val ELAPSED_TIME_PRESENT = 11
    const val REMAINING_TIME_PRESENT = 12
}

class IndoorBikeDataCharacteristic(
    gattProfile: AbstractGattProfile,
    private val indoorBikeData: IndoorBikeData
) : AbstractFitnessMachineDataCharacteristic(gattProfile) {

    private val GATT_INDOOR_BIKE_DATA_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("00002AD2-0000-1000-8000-00805f9b34fb")

    override fun getCharacteristicUUID(): UUID {
        return GATT_INDOOR_BIKE_DATA_CHARACTERISTIC_UUID
    }

    init {
        gattCharacteristic = BluetoothGattCharacteristic(
            GATT_INDOOR_BIKE_DATA_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0
        )

        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }
    }

    override fun getData(): ByteArray {
        val data = mutableListOf<Byte>()
        val flags = buildFlags()

        writeUInt16(data, flags)

        // More Data is never set in this simulator path, so instantaneous speed is mandatory.
        writeUInt16(data, indoorBikeData.getInstantaneousSpeedHundredthsKmh())

        if (indoorBikeData.includeAverageSpeed) {
            writeUInt16(data, indoorBikeData.getAverageSpeedHundredthsKmh())
        }
        if (indoorBikeData.includeInstantaneousCadence) {
            writeUInt16(data, indoorBikeData.getInstantaneousCadenceHalfRpm())
        }
        if (indoorBikeData.includeAverageCadence) {
            writeUInt16(data, indoorBikeData.getAverageCadenceHalfRpm())
        }
        if (indoorBikeData.includeTotalDistance) {
            writeUInt24(data, indoorBikeData.getTotalDistanceInt())
        }
        if (indoorBikeData.includeResistanceLevel) {
            writeSInt16(data, indoorBikeData.getResistanceLevelRaw())
        }
        if (indoorBikeData.includeInstantaneousPower) {
            writeSInt16(data, indoorBikeData.getInstantaneousPowerRaw())
        }
        if (indoorBikeData.includeAveragePower) {
            writeSInt16(data, indoorBikeData.getAveragePowerRaw())
        }
        if (indoorBikeData.includeExpendedEnergy) {
            writeUInt16(data, indoorBikeData.getTotalEnergyKcal())
            writeUInt16(data, indoorBikeData.getEnergyPerHourKcal())
            writeUInt8(data, indoorBikeData.getEnergyPerMinuteKcal())
        }
        if (indoorBikeData.includeHeartRate) {
            writeUInt8(data, indoorBikeData.getHeartRateBpm())
        }
        if (indoorBikeData.includeMetabolicEquivalent) {
            writeUInt8(data, indoorBikeData.getMetabolicEquivalentTenths())
        }
        if (indoorBikeData.includeElapsedTime) {
            writeUInt16(data, indoorBikeData.getElapsedTimeInt())
        }
        if (indoorBikeData.includeRemainingTime) {
            writeUInt16(data, indoorBikeData.getRemainingTimeInt())
        }

        return data.toByteArray()
    }

    private fun buildFlags(): Int {
        var flags = 0
        if (indoorBikeData.includeAverageSpeed) {
            flags = flags or (1 shl IndoorBikeDataFlags.AVERAGE_SPEED_PRESENT)
        }
        if (indoorBikeData.includeInstantaneousCadence) {
            flags = flags or (1 shl IndoorBikeDataFlags.INSTANTANEOUS_CADENCE_PRESENT)
        }
        if (indoorBikeData.includeAverageCadence) {
            flags = flags or (1 shl IndoorBikeDataFlags.AVERAGE_CADENCE_PRESENT)
        }
        if (indoorBikeData.includeTotalDistance) {
            flags = flags or (1 shl IndoorBikeDataFlags.TOTAL_DISTANCE_PRESENT)
        }
        if (indoorBikeData.includeResistanceLevel) {
            flags = flags or (1 shl IndoorBikeDataFlags.RESISTANCE_LEVEL_PRESENT)
        }
        if (indoorBikeData.includeInstantaneousPower) {
            flags = flags or (1 shl IndoorBikeDataFlags.INSTANTANEOUS_POWER_PRESENT)
        }
        if (indoorBikeData.includeAveragePower) {
            flags = flags or (1 shl IndoorBikeDataFlags.AVERAGE_POWER_PRESENT)
        }
        if (indoorBikeData.includeExpendedEnergy) {
            flags = flags or (1 shl IndoorBikeDataFlags.EXPENDED_ENERGY_PRESENT)
        }
        if (indoorBikeData.includeHeartRate) {
            flags = flags or (1 shl IndoorBikeDataFlags.HEART_RATE_PRESENT)
        }
        if (indoorBikeData.includeMetabolicEquivalent) {
            flags = flags or (1 shl IndoorBikeDataFlags.METABOLIC_EQUIVALENT_PRESENT)
        }
        if (indoorBikeData.includeElapsedTime) {
            flags = flags or (1 shl IndoorBikeDataFlags.ELAPSED_TIME_PRESENT)
        }
        if (indoorBikeData.includeRemainingTime) {
            flags = flags or (1 shl IndoorBikeDataFlags.REMAINING_TIME_PRESENT)
        }
        return flags
    }

    private fun writeUInt8(data: MutableList<Byte>, value: Int) {
        data.add((value and 0xFF).toByte())
    }

    private fun writeUInt16(data: MutableList<Byte>, value: Int) {
        data.add((value and 0xFF).toByte())
        data.add(((value shr 8) and 0xFF).toByte())
    }

    private fun writeSInt16(data: MutableList<Byte>, value: Int) {
        writeUInt16(data, value)
    }

    private fun writeUInt24(data: MutableList<Byte>, value: Int) {
        data.add((value and 0xFF).toByte())
        data.add(((value shr 8) and 0xFF).toByte())
        data.add(((value shr 16) and 0xFF).toByte())
    }
}
