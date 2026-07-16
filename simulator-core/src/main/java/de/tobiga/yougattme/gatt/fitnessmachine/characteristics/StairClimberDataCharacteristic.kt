package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.fitnessmachine.StairClimberData
import java.util.UUID

private object StairClimberDataFlags {
    const val STEPS_PER_MINUTE_PRESENT = 1
    const val AVERAGE_STEP_RATE_PRESENT = 2
    const val POSITIVE_ELEVATION_GAIN_PRESENT = 3
    const val STRIDE_COUNT_PRESENT = 4
    const val EXPENDED_ENERGY_PRESENT = 5
    const val HEART_RATE_PRESENT = 6
    const val METABOLIC_EQUIVALENT_PRESENT = 7
    const val ELAPSED_TIME_PRESENT = 8
    const val REMAINING_TIME_PRESENT = 9
}

class StairClimberDataCharacteristic(
    gattProfile: AbstractGattProfile,
    private val stairClimberData: StairClimberData
) : AbstractFitnessMachineDataCharacteristic(gattProfile) {

    private val GATT_STAIR_CLIMBER_DATA_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("00002AD0-0000-1000-8000-00805f9b34fb")

    override fun getCharacteristicUUID(): UUID = GATT_STAIR_CLIMBER_DATA_CHARACTERISTIC_UUID

    init {
        gattCharacteristic = BluetoothGattCharacteristic(
            GATT_STAIR_CLIMBER_DATA_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0
        )
        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }
    }

    override fun getFtmsDataRecord(): FtmsDataRecord {
        val optional = buildList {
            if (stairClimberData.includeStepsPerMinute) add(ftmsField(StairClimberDataFlags.STEPS_PER_MINUTE_PRESENT) { u16(stairClimberData.getStepsPerMinuteSpm()) })
            if (stairClimberData.includeAverageStepRate) add(ftmsField(StairClimberDataFlags.AVERAGE_STEP_RATE_PRESENT) { u16(stairClimberData.getAverageStepRateSpm()) })
            if (stairClimberData.includePositiveElevationGain) add(ftmsField(StairClimberDataFlags.POSITIVE_ELEVATION_GAIN_PRESENT) { u16(stairClimberData.getPositiveElevationGainMeters()) })
            if (stairClimberData.includeStrideCount) add(ftmsField(StairClimberDataFlags.STRIDE_COUNT_PRESENT) { u16(stairClimberData.strideCount) })
            if (stairClimberData.includeExpendedEnergy) add(ftmsField(StairClimberDataFlags.EXPENDED_ENERGY_PRESENT) {
                u16(stairClimberData.getTotalEnergyKcal())
                u16(stairClimberData.getEnergyPerHourKcal())
                u8(stairClimberData.getEnergyPerMinuteKcal())
            })
            if (stairClimberData.includeHeartRate) add(ftmsField(StairClimberDataFlags.HEART_RATE_PRESENT) { u8(stairClimberData.getHeartRateBpm()) })
            if (stairClimberData.includeMetabolicEquivalent) add(ftmsField(StairClimberDataFlags.METABOLIC_EQUIVALENT_PRESENT) { u8(stairClimberData.getMetabolicEquivalentTenths()) })
            if (stairClimberData.includeElapsedTime) add(ftmsField(StairClimberDataFlags.ELAPSED_TIME_PRESENT) { u16(stairClimberData.getElapsedTimeSeconds()) })
            if (stairClimberData.includeRemainingTime) add(ftmsField(StairClimberDataFlags.REMAINING_TIME_PRESENT) { u16(stairClimberData.getRemainingTimeSeconds()) })
        }
        return FtmsDataRecord(
            flagsWidth = 2,
            mandatoryFields = ftmsMandatory { u16(stairClimberData.floors) },
            optionalFields = optional
        )
    }
}
