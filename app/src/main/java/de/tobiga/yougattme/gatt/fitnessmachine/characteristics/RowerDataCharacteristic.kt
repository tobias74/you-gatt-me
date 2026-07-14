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

    override fun getData(): ByteArray {
        val writer = FtmsPacketWriter()
        writer.u16(buildFlags())

        // More Data is never set in this simulator path, so stroke rate/count are mandatory.
        writer.u8(rowerData.getStrokeRateHalfSpm())
        writer.u16(rowerData.getStrokeCountTotal())

        if (rowerData.includeAverageStrokeRate) {
            writer.u8(rowerData.getAverageStrokeRateHalfSpm())
        }
        if (rowerData.includeTotalDistance) {
            writer.u24(rowerData.getTotalDistanceMeters())
        }
        if (rowerData.includeInstantaneousPace) {
            writer.u16(rowerData.getInstantaneousPaceSeconds())
        }
        if (rowerData.includeAveragePace) {
            writer.u16(rowerData.getAveragePaceSeconds())
        }
        if (rowerData.includeInstantaneousPower) {
            writer.s16(rowerData.getInstantaneousPowerWatt())
        }
        if (rowerData.includeAveragePower) {
            writer.s16(rowerData.getAveragePowerWatt())
        }
        if (rowerData.includeResistanceLevel) {
            writer.s16(rowerData.getResistanceLevelRaw())
        }
        if (rowerData.includeExpendedEnergy) {
            writer.u16(rowerData.getTotalEnergyKcal())
            writer.u16(rowerData.getEnergyPerHourKcal())
            writer.u8(rowerData.getEnergyPerMinuteKcal())
        }
        if (rowerData.includeHeartRate) {
            writer.u8(rowerData.getHeartRateBpm())
        }
        if (rowerData.includeMetabolicEquivalent) {
            writer.u8(rowerData.getMetabolicEquivalentTenths())
        }
        if (rowerData.includeElapsedTime) {
            writer.u16(rowerData.getElapsedTimeSeconds())
        }
        if (rowerData.includeRemainingTime) {
            writer.u16(rowerData.getRemainingTimeSeconds())
        }

        return writer.bytes()
    }

    private fun buildFlags(): Int {
        var flags = 0
        if (rowerData.includeAverageStrokeRate) flags = flags or (1 shl RowerDataFlags.AVERAGE_STROKE_RATE_PRESENT)
        if (rowerData.includeTotalDistance) flags = flags or (1 shl RowerDataFlags.TOTAL_DISTANCE_PRESENT)
        if (rowerData.includeInstantaneousPace) flags = flags or (1 shl RowerDataFlags.INSTANTANEOUS_PACE_PRESENT)
        if (rowerData.includeAveragePace) flags = flags or (1 shl RowerDataFlags.AVERAGE_PACE_PRESENT)
        if (rowerData.includeInstantaneousPower) flags = flags or (1 shl RowerDataFlags.INSTANTANEOUS_POWER_PRESENT)
        if (rowerData.includeAveragePower) flags = flags or (1 shl RowerDataFlags.AVERAGE_POWER_PRESENT)
        if (rowerData.includeResistanceLevel) flags = flags or (1 shl RowerDataFlags.RESISTANCE_LEVEL_PRESENT)
        if (rowerData.includeExpendedEnergy) flags = flags or (1 shl RowerDataFlags.EXPENDED_ENERGY_PRESENT)
        if (rowerData.includeHeartRate) flags = flags or (1 shl RowerDataFlags.HEART_RATE_PRESENT)
        if (rowerData.includeMetabolicEquivalent) flags = flags or (1 shl RowerDataFlags.METABOLIC_EQUIVALENT_PRESENT)
        if (rowerData.includeElapsedTime) flags = flags or (1 shl RowerDataFlags.ELAPSED_TIME_PRESENT)
        if (rowerData.includeRemainingTime) flags = flags or (1 shl RowerDataFlags.REMAINING_TIME_PRESENT)
        return flags
    }
}
