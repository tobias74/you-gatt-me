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

    override fun getData(): ByteArray {
        val writer = FtmsPacketWriter()
        writer.u16(buildFlags())

        // More Data is never set in this simulator path, so instantaneous speed is mandatory.
        writer.u16(treadmillData.getInstantaneousSpeedHundredthsKmh())

        if (treadmillData.includeAverageSpeed) {
            writer.u16(treadmillData.getAverageSpeedHundredthsKmh())
        }
        if (treadmillData.includeTotalDistance) {
            writer.u24(treadmillData.getTotalDistanceMeters())
        }
        if (treadmillData.includeInclination) {
            writer.s16(treadmillData.getInclinationTenthsPercent())
            writer.s16(treadmillData.getRampAngleTenthsDegree())
        }
        if (treadmillData.includeElevationGain) {
            writer.u16(treadmillData.getPositiveElevationGainTenthsMeters())
            writer.u16(treadmillData.getNegativeElevationGainTenthsMeters())
        }
        if (treadmillData.includeInstantaneousPace) {
            writer.u8(treadmillData.getInstantaneousPaceKmpmTenths())
        }
        if (treadmillData.includeAveragePace) {
            writer.u8(treadmillData.getAveragePaceKmpmTenths())
        }
        if (treadmillData.includeExpendedEnergy) {
            writer.u16(treadmillData.getTotalEnergyKcal())
            writer.u16(treadmillData.getEnergyPerHourKcal())
            writer.u8(treadmillData.getEnergyPerMinuteKcal())
        }
        if (treadmillData.includeHeartRate) {
            writer.u8(treadmillData.getHeartRateBpm())
        }
        if (treadmillData.includeMetabolicEquivalent) {
            writer.u8(treadmillData.getMetabolicEquivalentTenths())
        }
        if (treadmillData.includeElapsedTime) {
            writer.u16(treadmillData.getElapsedTimeSeconds())
        }
        if (treadmillData.includeRemainingTime) {
            writer.u16(treadmillData.getRemainingTimeSeconds())
        }
        if (treadmillData.includeForceOnBeltAndPowerOutput) {
            writer.s16(treadmillData.getForceOnBeltNewton())
            writer.s16(treadmillData.getPowerOutputWatt())
        }

        return writer.bytes()
    }

    private fun buildFlags(): Int {
        var flags = 0
        if (treadmillData.includeAverageSpeed) flags = flags or (1 shl TreadmillDataFlags.AVERAGE_SPEED_PRESENT)
        if (treadmillData.includeTotalDistance) flags = flags or (1 shl TreadmillDataFlags.TOTAL_DISTANCE_PRESENT)
        if (treadmillData.includeInclination) flags = flags or (1 shl TreadmillDataFlags.INCLINATION_AND_RAMP_ANGLE_PRESENT)
        if (treadmillData.includeElevationGain) flags = flags or (1 shl TreadmillDataFlags.ELEVATION_GAIN_PRESENT)
        if (treadmillData.includeInstantaneousPace) flags = flags or (1 shl TreadmillDataFlags.INSTANTANEOUS_PACE_PRESENT)
        if (treadmillData.includeAveragePace) flags = flags or (1 shl TreadmillDataFlags.AVERAGE_PACE_PRESENT)
        if (treadmillData.includeExpendedEnergy) flags = flags or (1 shl TreadmillDataFlags.EXPENDED_ENERGY_PRESENT)
        if (treadmillData.includeHeartRate) flags = flags or (1 shl TreadmillDataFlags.HEART_RATE_PRESENT)
        if (treadmillData.includeMetabolicEquivalent) flags = flags or (1 shl TreadmillDataFlags.METABOLIC_EQUIVALENT_PRESENT)
        if (treadmillData.includeElapsedTime) flags = flags or (1 shl TreadmillDataFlags.ELAPSED_TIME_PRESENT)
        if (treadmillData.includeRemainingTime) flags = flags or (1 shl TreadmillDataFlags.REMAINING_TIME_PRESENT)
        if (treadmillData.includeForceOnBeltAndPowerOutput) flags = flags or (1 shl TreadmillDataFlags.FORCE_ON_BELT_AND_POWER_OUTPUT_PRESENT)
        return flags
    }
}
