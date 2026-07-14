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

    override fun getData(): ByteArray {
        val writer = FtmsPacketWriter()
        writer.u16(buildFlags())

        // More Data is never set in this simulator path, so floors are mandatory.
        writer.u16(stairClimberData.floors)

        if (stairClimberData.includeStepsPerMinute) {
            writer.u16(stairClimberData.getStepsPerMinuteSpm())
        }
        if (stairClimberData.includeAverageStepRate) {
            writer.u16(stairClimberData.getAverageStepRateSpm())
        }
        if (stairClimberData.includePositiveElevationGain) {
            writer.u16(stairClimberData.getPositiveElevationGainMeters())
        }
        if (stairClimberData.includeStrideCount) {
            writer.u16(stairClimberData.strideCount)
        }
        if (stairClimberData.includeExpendedEnergy) {
            writer.u16(stairClimberData.getTotalEnergyKcal())
            writer.u16(stairClimberData.getEnergyPerHourKcal())
            writer.u8(stairClimberData.getEnergyPerMinuteKcal())
        }
        if (stairClimberData.includeHeartRate) {
            writer.u8(stairClimberData.getHeartRateBpm())
        }
        if (stairClimberData.includeMetabolicEquivalent) {
            writer.u8(stairClimberData.getMetabolicEquivalentTenths())
        }
        if (stairClimberData.includeElapsedTime) {
            writer.u16(stairClimberData.getElapsedTimeSeconds())
        }
        if (stairClimberData.includeRemainingTime) {
            writer.u16(stairClimberData.getRemainingTimeSeconds())
        }

        return writer.bytes()
    }

    private fun buildFlags(): Int {
        var flags = 0
        if (stairClimberData.includeStepsPerMinute) flags = flags or (1 shl StairClimberDataFlags.STEPS_PER_MINUTE_PRESENT)
        if (stairClimberData.includeAverageStepRate) flags = flags or (1 shl StairClimberDataFlags.AVERAGE_STEP_RATE_PRESENT)
        if (stairClimberData.includePositiveElevationGain) flags = flags or (1 shl StairClimberDataFlags.POSITIVE_ELEVATION_GAIN_PRESENT)
        if (stairClimberData.includeStrideCount) flags = flags or (1 shl StairClimberDataFlags.STRIDE_COUNT_PRESENT)
        if (stairClimberData.includeExpendedEnergy) flags = flags or (1 shl StairClimberDataFlags.EXPENDED_ENERGY_PRESENT)
        if (stairClimberData.includeHeartRate) flags = flags or (1 shl StairClimberDataFlags.HEART_RATE_PRESENT)
        if (stairClimberData.includeMetabolicEquivalent) flags = flags or (1 shl StairClimberDataFlags.METABOLIC_EQUIVALENT_PRESENT)
        if (stairClimberData.includeElapsedTime) flags = flags or (1 shl StairClimberDataFlags.ELAPSED_TIME_PRESENT)
        if (stairClimberData.includeRemainingTime) flags = flags or (1 shl StairClimberDataFlags.REMAINING_TIME_PRESENT)
        return flags
    }
}
