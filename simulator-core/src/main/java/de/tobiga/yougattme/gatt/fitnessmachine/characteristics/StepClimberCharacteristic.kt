package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.fitnessmachine.StepClimberData
import java.util.UUID

private object StepClimberDataFlags {
    const val STEPS_PER_MINUTE_PRESENT = 1
    const val AVERAGE_STEP_RATE_PRESENT = 2
    const val POSITIVE_ELEVATION_GAIN_PRESENT = 3
    const val EXPENDED_ENERGY_PRESENT = 4
    const val HEART_RATE_PRESENT = 5
    const val METABOLIC_EQUIVALENT_PRESENT = 6
    const val ELAPSED_TIME_PRESENT = 7
    const val REMAINING_TIME_PRESENT = 8
}

class StepClimberDataCharacteristic(
    gattProfile: AbstractGattProfile,
    private val stepClimberData: StepClimberData
) : AbstractFitnessMachineDataCharacteristic(gattProfile) {

    private val GATT_STEP_CLIMBER_DATA_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("00002ACF-0000-1000-8000-00805f9b34fb")

    override fun getCharacteristicUUID(): UUID = GATT_STEP_CLIMBER_DATA_CHARACTERISTIC_UUID

    init {
        gattCharacteristic = BluetoothGattCharacteristic(
            GATT_STEP_CLIMBER_DATA_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0
        )
        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }
    }

    override fun getFtmsDataRecord(): FtmsDataRecord {
        val optional = buildList {
            if (stepClimberData.includeStepsPerMinute) add(ftmsField(StepClimberDataFlags.STEPS_PER_MINUTE_PRESENT) { u16(stepClimberData.getStepsPerMinuteSpm()) })
            if (stepClimberData.includeAverageStepRate) add(ftmsField(StepClimberDataFlags.AVERAGE_STEP_RATE_PRESENT) { u16(stepClimberData.getAverageStepRateSpm()) })
            if (stepClimberData.includePositiveElevationGain) add(ftmsField(StepClimberDataFlags.POSITIVE_ELEVATION_GAIN_PRESENT) { u16(stepClimberData.getPositiveElevationGainMeters()) })
            if (stepClimberData.includeExpendedEnergy) add(ftmsField(StepClimberDataFlags.EXPENDED_ENERGY_PRESENT) {
                u16(stepClimberData.getTotalEnergyKcal())
                u16(stepClimberData.getEnergyPerHourKcal())
                u8(stepClimberData.getEnergyPerMinuteKcal())
            })
            if (stepClimberData.includeHeartRate) add(ftmsField(StepClimberDataFlags.HEART_RATE_PRESENT) { u8(stepClimberData.getHeartRateBpm()) })
            if (stepClimberData.includeMetabolicEquivalent) add(ftmsField(StepClimberDataFlags.METABOLIC_EQUIVALENT_PRESENT) { u8(stepClimberData.getMetabolicEquivalentTenths()) })
            if (stepClimberData.includeElapsedTime) add(ftmsField(StepClimberDataFlags.ELAPSED_TIME_PRESENT) { u16(stepClimberData.getElapsedTimeSeconds()) })
            if (stepClimberData.includeRemainingTime) add(ftmsField(StepClimberDataFlags.REMAINING_TIME_PRESENT) { u16(stepClimberData.getRemainingTimeSeconds()) })
        }
        return FtmsDataRecord(
            flagsWidth = 2,
            mandatoryFields = ftmsMandatory {
                u16(stepClimberData.floors)
                u16(stepClimberData.stepCount)
            },
            optionalFields = optional
        )
    }
}
