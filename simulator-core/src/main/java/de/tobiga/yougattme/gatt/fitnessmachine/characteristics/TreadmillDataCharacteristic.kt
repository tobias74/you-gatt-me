package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.fitnessmachine.TreadmillData
import java.util.UUID

private object TreadmillDataFlags {
    const val AVERAGE_SPEED_PRESENT = 1
    const val TOTAL_DISTANCE_PRESENT = 2
    const val INCLINATION_AND_RAMP_ANGLE_PRESENT = 3
    const val ELEVATION_GAIN_PRESENT = 4
    const val INSTANTANEOUS_PACE_PRESENT = 5
    const val AVERAGE_PACE_PRESENT = 6
    const val EXPENDED_ENERGY_PRESENT = 7
    const val HEART_RATE_PRESENT = 8
    const val METABOLIC_EQUIVALENT_PRESENT = 9
    const val ELAPSED_TIME_PRESENT = 10
    const val REMAINING_TIME_PRESENT = 11
    const val FORCE_ON_BELT_AND_POWER_OUTPUT_PRESENT = 12
}

class TreadmillDataCharacteristic(
    gattProfile: AbstractGattProfile,
    private val treadmillData: TreadmillData
) : AbstractFitnessMachineDataCharacteristic(gattProfile) {

    private val GATT_TREADMILL_DATA_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("00002ACD-0000-1000-8000-00805f9b34fb")

    override fun getCharacteristicUUID(): UUID = GATT_TREADMILL_DATA_CHARACTERISTIC_UUID

    init {
        gattCharacteristic = BluetoothGattCharacteristic(
            GATT_TREADMILL_DATA_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0
        )
        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }
    }

    override fun getFtmsDataRecord(): FtmsDataRecord {
        val optional = buildList {
            if (treadmillData.includeAverageSpeed) add(ftmsField(TreadmillDataFlags.AVERAGE_SPEED_PRESENT) { u16(treadmillData.getAverageSpeedHundredthsKmh()) })
            if (treadmillData.includeTotalDistance) add(ftmsField(TreadmillDataFlags.TOTAL_DISTANCE_PRESENT) { u24(treadmillData.getTotalDistanceMeters()) })
            if (treadmillData.includeInclination) add(ftmsField(TreadmillDataFlags.INCLINATION_AND_RAMP_ANGLE_PRESENT) {
                s16(treadmillData.getInclinationTenthsPercent())
                s16(treadmillData.getRampAngleTenthsDegree())
            })
            if (treadmillData.includeElevationGain) add(ftmsField(TreadmillDataFlags.ELEVATION_GAIN_PRESENT) {
                u16(treadmillData.getPositiveElevationGainTenthsMeters())
                u16(treadmillData.getNegativeElevationGainTenthsMeters())
            })
            if (treadmillData.includeInstantaneousPace) add(ftmsField(TreadmillDataFlags.INSTANTANEOUS_PACE_PRESENT) { u16(treadmillData.getInstantaneousPaceSeconds()) })
            if (treadmillData.includeAveragePace) add(ftmsField(TreadmillDataFlags.AVERAGE_PACE_PRESENT) { u16(treadmillData.getAveragePaceSeconds()) })
            if (treadmillData.includeExpendedEnergy) add(ftmsField(TreadmillDataFlags.EXPENDED_ENERGY_PRESENT) {
                u16(treadmillData.getTotalEnergyKcal())
                u16(treadmillData.getEnergyPerHourKcal())
                u8(treadmillData.getEnergyPerMinuteKcal())
            })
            if (treadmillData.includeHeartRate) add(ftmsField(TreadmillDataFlags.HEART_RATE_PRESENT) { u8(treadmillData.getHeartRateBpm()) })
            if (treadmillData.includeMetabolicEquivalent) add(ftmsField(TreadmillDataFlags.METABOLIC_EQUIVALENT_PRESENT) { u8(treadmillData.getMetabolicEquivalentTenths()) })
            if (treadmillData.includeElapsedTime) add(ftmsField(TreadmillDataFlags.ELAPSED_TIME_PRESENT) { u16(treadmillData.getElapsedTimeSeconds()) })
            if (treadmillData.includeRemainingTime) add(ftmsField(TreadmillDataFlags.REMAINING_TIME_PRESENT) { u16(treadmillData.getRemainingTimeSeconds()) })
            if (treadmillData.includeForceOnBeltAndPowerOutput) add(ftmsField(TreadmillDataFlags.FORCE_ON_BELT_AND_POWER_OUTPUT_PRESENT) {
                s16(treadmillData.getForceOnBeltNewton())
                s16(treadmillData.getPowerOutputWatt())
            })
        }
        return FtmsDataRecord(
            flagsWidth = 2,
            mandatoryFields = ftmsMandatory { u16(treadmillData.getInstantaneousSpeedHundredthsKmh()) },
            optionalFields = optional
        )
    }
}
