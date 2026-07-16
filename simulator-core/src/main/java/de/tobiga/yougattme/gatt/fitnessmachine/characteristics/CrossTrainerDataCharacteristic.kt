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

    override fun getFtmsDataRecord(): FtmsDataRecord {
        val optional = buildList {
            if (crossTrainerData.includeAverageSpeed) add(ftmsField(CrossTrainerDataFlags.AVERAGE_SPEED_PRESENT) { u16(crossTrainerData.getAverageSpeedHundredthsKmh()) })
            if (crossTrainerData.includeTotalDistance) add(ftmsField(CrossTrainerDataFlags.TOTAL_DISTANCE_PRESENT) { u24(crossTrainerData.getTotalDistanceMeters()) })
            if (crossTrainerData.includeStepCount) add(ftmsField(CrossTrainerDataFlags.STEP_COUNT_PRESENT) {
                u16(crossTrainerData.getInstantaneousStepRateSpm())
                u16(crossTrainerData.getAverageStepRateSpm())
            })
            if (crossTrainerData.includeStrideCount) add(ftmsField(CrossTrainerDataFlags.STRIDE_COUNT_PRESENT) { u16(crossTrainerData.getStrideCountTenths()) })
            if (crossTrainerData.includeElevationGain) add(ftmsField(CrossTrainerDataFlags.ELEVATION_GAIN_PRESENT) {
                u16(crossTrainerData.getPositiveElevationGainMeters())
                u16(crossTrainerData.getNegativeElevationGainMeters())
            })
            if (crossTrainerData.includeInclination) add(ftmsField(CrossTrainerDataFlags.INCLINATION_AND_RAMP_ANGLE_PRESENT) {
                s16(crossTrainerData.getInclinationTenthsPercent())
                s16(crossTrainerData.getRampAngleTenthsDegree())
            })
            if (crossTrainerData.includeResistanceLevel) add(ftmsField(CrossTrainerDataFlags.RESISTANCE_LEVEL_PRESENT) { u8(crossTrainerData.getResistanceLevelRaw()) })
            if (crossTrainerData.includeInstantaneousPower) add(ftmsField(CrossTrainerDataFlags.INSTANTANEOUS_POWER_PRESENT) { s16(crossTrainerData.getInstantaneousPowerWatt()) })
            if (crossTrainerData.includeAveragePower) add(ftmsField(CrossTrainerDataFlags.AVERAGE_POWER_PRESENT) { s16(crossTrainerData.getAveragePowerWatt()) })
            if (crossTrainerData.includeExpendedEnergy) add(ftmsField(CrossTrainerDataFlags.EXPENDED_ENERGY_PRESENT) {
                u16(crossTrainerData.getTotalEnergyKcal())
                u16(crossTrainerData.getEnergyPerHourKcal())
                u8(crossTrainerData.getEnergyPerMinuteKcal())
            })
            if (crossTrainerData.includeHeartRate) add(ftmsField(CrossTrainerDataFlags.HEART_RATE_PRESENT) { u8(crossTrainerData.getHeartRateBpm()) })
            if (crossTrainerData.includeMetabolicEquivalent) add(ftmsField(CrossTrainerDataFlags.METABOLIC_EQUIVALENT_PRESENT) { u8(crossTrainerData.getMetabolicEquivalentTenths()) })
            if (crossTrainerData.includeElapsedTime) add(ftmsField(CrossTrainerDataFlags.ELAPSED_TIME_PRESENT) { u16(crossTrainerData.getElapsedTimeSeconds()) })
            if (crossTrainerData.includeRemainingTime) add(ftmsField(CrossTrainerDataFlags.REMAINING_TIME_PRESENT) { u16(crossTrainerData.getRemainingTimeSeconds()) })
        }
        val fixedFlags = if (crossTrainerData.movementDirectionBackward) {
            1 shl CrossTrainerDataFlags.MOVEMENT_DIRECTION_BACKWARD
        } else {
            0
        }
        return FtmsDataRecord(
            flagsWidth = 3,
            mandatoryFields = ftmsMandatory { u16(crossTrainerData.getInstantaneousSpeedHundredthsKmh()) },
            optionalFields = optional,
            fixedFlags = fixedFlags
        )
    }
}
