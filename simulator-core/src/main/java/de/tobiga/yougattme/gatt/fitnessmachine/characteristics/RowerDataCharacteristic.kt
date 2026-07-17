package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.fitnessmachine.RowerData
import java.util.UUID

private object RowerDataFlags {
    const val AVERAGE_STROKE_RATE_PRESENT = 1
    const val TOTAL_DISTANCE_PRESENT = 2
    const val INSTANTANEOUS_PACE_PRESENT = 3
    const val AVERAGE_PACE_PRESENT = 4
    const val INSTANTANEOUS_POWER_PRESENT = 5
    const val AVERAGE_POWER_PRESENT = 6
    const val RESISTANCE_LEVEL_PRESENT = 7
    const val EXPENDED_ENERGY_PRESENT = 8
    const val HEART_RATE_PRESENT = 9
    const val METABOLIC_EQUIVALENT_PRESENT = 10
    const val ELAPSED_TIME_PRESENT = 11
    const val REMAINING_TIME_PRESENT = 12
}

class RowerDataCharacteristic(
    gattProfile: AbstractGattProfile,
    private val rowerData: RowerData
) : AbstractFitnessMachineDataCharacteristic(gattProfile) {

    private val GATT_ROWER_DATA_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("00002AD1-0000-1000-8000-00805f9b34fb")

    override fun getCharacteristicUUID(): UUID = GATT_ROWER_DATA_CHARACTERISTIC_UUID

    init {
        gattCharacteristic = BluetoothGattCharacteristic(
            GATT_ROWER_DATA_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            0
        )
        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }
    }

    override fun getFtmsDataRecord(): FtmsDataRecord {
        val optional = buildList {
            if (rowerData.includeAverageStrokeRate) add(ftmsField(RowerDataFlags.AVERAGE_STROKE_RATE_PRESENT) { u8(rowerData.getAverageStrokeRateHalfSpm()) })
            if (rowerData.includeTotalDistance) add(ftmsField(RowerDataFlags.TOTAL_DISTANCE_PRESENT) { u24(rowerData.getTotalDistanceMeters()) })
            if (rowerData.includeInstantaneousPace) add(ftmsField(RowerDataFlags.INSTANTANEOUS_PACE_PRESENT) { u16(rowerData.getInstantaneousPaceSeconds()) })
            if (rowerData.includeAveragePace) add(ftmsField(RowerDataFlags.AVERAGE_PACE_PRESENT) { u16(rowerData.getAveragePaceSeconds()) })
            if (rowerData.includeInstantaneousPower) add(ftmsField(RowerDataFlags.INSTANTANEOUS_POWER_PRESENT) { s16(rowerData.getInstantaneousPowerWatt()) })
            if (rowerData.includeAveragePower) add(ftmsField(RowerDataFlags.AVERAGE_POWER_PRESENT) { s16(rowerData.getAveragePowerWatt()) })
            if (rowerData.includeResistanceLevel) add(ftmsField(RowerDataFlags.RESISTANCE_LEVEL_PRESENT) { s16(rowerData.getResistanceLevelRaw()) })
            if (rowerData.includeExpendedEnergy) add(ftmsField(RowerDataFlags.EXPENDED_ENERGY_PRESENT) {
                u16(rowerData.getTotalEnergyKcal())
                u16(rowerData.getEnergyPerHourKcal())
                u8(rowerData.getEnergyPerMinuteKcal())
            })
            if (rowerData.includeHeartRate) add(ftmsField(RowerDataFlags.HEART_RATE_PRESENT) { u8(rowerData.getHeartRateBpm()) })
            if (rowerData.includeMetabolicEquivalent) add(ftmsField(RowerDataFlags.METABOLIC_EQUIVALENT_PRESENT) { u8(rowerData.getMetabolicEquivalentTenths()) })
            if (rowerData.includeElapsedTime) add(ftmsField(RowerDataFlags.ELAPSED_TIME_PRESENT) { u16(rowerData.getElapsedTimeSeconds()) })
            if (rowerData.includeRemainingTime) add(ftmsField(RowerDataFlags.REMAINING_TIME_PRESENT) { u16(rowerData.getRemainingTimeSeconds()) })
        }
        return FtmsDataRecord(
            flagsWidth = 2,
            mandatoryFields = ftmsMandatory {
                u8(rowerData.getStrokeRateHalfSpm())
                u16(rowerData.getStrokeCountTotal())
            },
            optionalFields = optional
        )
    }
}
