package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.fitnessmachine.CrossTrainerData
import java.util.UUID

private object CrossTrainerDataFlags {
    const val AVERAGE_SPEED_PRESENT = 1
    const val TOTAL_DISTANCE_PRESENT = 2
    const val STEP_COUNT_PRESENT = 3
    const val STRIDE_COUNT_PRESENT = 4
    const val ELEVATION_GAIN_PRESENT = 5
    const val INCLINATION_AND_RAMP_ANGLE_PRESENT = 6
    const val RESISTANCE_LEVEL_PRESENT = 7
    const val INSTANTANEOUS_POWER_PRESENT = 8
    const val AVERAGE_POWER_PRESENT = 9
    const val EXPENDED_ENERGY_PRESENT = 10
    const val HEART_RATE_PRESENT = 11
    const val METABOLIC_EQUIVALENT_PRESENT = 12
    const val ELAPSED_TIME_PRESENT = 13
    const val REMAINING_TIME_PRESENT = 14
    const val MOVEMENT_DIRECTION_BACKWARD = 15
}

class CrossTrainerDataCharacteristic(
    gattProfile: AbstractGattProfile,
    private val crossTrainerData: CrossTrainerData
) : AbstractFitnessMachineDataCharacteristic(gattProfile) {

    private val GATT_CROSS_TRAINER_DATA_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("00002ACE-0000-1000-8000-00805f9b34fb")

    override fun getCharacteristicUUID(): UUID = GATT_CROSS_TRAINER_DATA_CHARACTERISTIC_UUID

    init {
        gattCharacteristic = BluetoothGattCharacteristic(
            GATT_CROSS_TRAINER_DATA_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0
        )
        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }
    }

    override fun getData(): ByteArray {
        val writer = FtmsPacketWriter()
        writer.u24(buildFlags())

        // More Data is never set in this simulator path, so instantaneous speed is mandatory.
        writer.u16(crossTrainerData.getInstantaneousSpeedHundredthsKmh())

        if (crossTrainerData.includeAverageSpeed) {
            writer.u16(crossTrainerData.getAverageSpeedHundredthsKmh())
        }
        if (crossTrainerData.includeTotalDistance) {
            writer.u24(crossTrainerData.getTotalDistanceMeters())
        }
        if (crossTrainerData.includeStepCount) {
            writer.u16(crossTrainerData.getInstantaneousStepRateSpm())
            writer.u16(crossTrainerData.getAverageStepRateSpm())
        }
        if (crossTrainerData.includeStrideCount) {
            writer.u16(crossTrainerData.getStrideCountTenths())
        }
        if (crossTrainerData.includeElevationGain) {
            writer.u16(crossTrainerData.getPositiveElevationGainMeters())
            writer.u16(crossTrainerData.getNegativeElevationGainMeters())
        }
        if (crossTrainerData.includeInclination) {
            writer.s16(crossTrainerData.getInclinationTenthsPercent())
            writer.s16(crossTrainerData.getRampAngleTenthsDegree())
        }
        if (crossTrainerData.includeResistanceLevel) {
            writer.s16(crossTrainerData.getResistanceLevelTenths())
        }
        if (crossTrainerData.includeInstantaneousPower) {
            writer.s16(crossTrainerData.getInstantaneousPowerWatt())
        }
        if (crossTrainerData.includeAveragePower) {
            writer.s16(crossTrainerData.getAveragePowerWatt())
        }
        if (crossTrainerData.includeExpendedEnergy) {
            writer.u16(crossTrainerData.getTotalEnergyKcal())
            writer.u16(crossTrainerData.getEnergyPerHourKcal())
            writer.u8(crossTrainerData.getEnergyPerMinuteKcal())
        }
        if (crossTrainerData.includeHeartRate) {
            writer.u8(crossTrainerData.getHeartRateBpm())
        }
        if (crossTrainerData.includeMetabolicEquivalent) {
            writer.u8(crossTrainerData.getMetabolicEquivalentTenths())
        }
        if (crossTrainerData.includeElapsedTime) {
            writer.u16(crossTrainerData.getElapsedTimeSeconds())
        }
        if (crossTrainerData.includeRemainingTime) {
            writer.u16(crossTrainerData.getRemainingTimeSeconds())
        }

        return writer.bytes()
    }

    private fun buildFlags(): Int {
        var flags = 0
        if (crossTrainerData.includeAverageSpeed) flags = flags or (1 shl CrossTrainerDataFlags.AVERAGE_SPEED_PRESENT)
        if (crossTrainerData.includeTotalDistance) flags = flags or (1 shl CrossTrainerDataFlags.TOTAL_DISTANCE_PRESENT)
        if (crossTrainerData.includeStepCount) flags = flags or (1 shl CrossTrainerDataFlags.STEP_COUNT_PRESENT)
        if (crossTrainerData.includeStrideCount) flags = flags or (1 shl CrossTrainerDataFlags.STRIDE_COUNT_PRESENT)
        if (crossTrainerData.includeElevationGain) flags = flags or (1 shl CrossTrainerDataFlags.ELEVATION_GAIN_PRESENT)
        if (crossTrainerData.includeInclination) flags = flags or (1 shl CrossTrainerDataFlags.INCLINATION_AND_RAMP_ANGLE_PRESENT)
        if (crossTrainerData.includeResistanceLevel) flags = flags or (1 shl CrossTrainerDataFlags.RESISTANCE_LEVEL_PRESENT)
        if (crossTrainerData.includeInstantaneousPower) flags = flags or (1 shl CrossTrainerDataFlags.INSTANTANEOUS_POWER_PRESENT)
        if (crossTrainerData.includeAveragePower) flags = flags or (1 shl CrossTrainerDataFlags.AVERAGE_POWER_PRESENT)
        if (crossTrainerData.includeExpendedEnergy) flags = flags or (1 shl CrossTrainerDataFlags.EXPENDED_ENERGY_PRESENT)
        if (crossTrainerData.includeHeartRate) flags = flags or (1 shl CrossTrainerDataFlags.HEART_RATE_PRESENT)
        if (crossTrainerData.includeMetabolicEquivalent) flags = flags or (1 shl CrossTrainerDataFlags.METABOLIC_EQUIVALENT_PRESENT)
        if (crossTrainerData.includeElapsedTime) flags = flags or (1 shl CrossTrainerDataFlags.ELAPSED_TIME_PRESENT)
        if (crossTrainerData.includeRemainingTime) flags = flags or (1 shl CrossTrainerDataFlags.REMAINING_TIME_PRESENT)
        if (crossTrainerData.movementDirectionBackward) flags = flags or (1 shl CrossTrainerDataFlags.MOVEMENT_DIRECTION_BACKWARD)
        return flags
    }
}
