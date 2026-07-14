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

    override fun getData(): ByteArray {
        val writer = FtmsPacketWriter()
        writer.u16(buildFlags())

        // More Data is never set in this simulator path, so floors and step count are mandatory.
        writer.u16(stepClimberData.floors)
        writer.u16(stepClimberData.stepCount)

        if (stepClimberData.includeStepsPerMinute) {
            writer.u16(stepClimberData.getStepsPerMinuteSpm())
        }
        if (stepClimberData.includeAverageStepRate) {
            writer.u16(stepClimberData.getAverageStepRateSpm())
        }
        if (stepClimberData.includePositiveElevationGain) {
            writer.u16(stepClimberData.getPositiveElevationGainMeters())
        }
        if (stepClimberData.includeExpendedEnergy) {
            writer.u16(stepClimberData.getTotalEnergyKcal())
            writer.u16(stepClimberData.getEnergyPerHourKcal())
            writer.u8(stepClimberData.getEnergyPerMinuteKcal())
        }
        if (stepClimberData.includeHeartRate) {
            writer.u8(stepClimberData.getHeartRateBpm())
        }
        if (stepClimberData.includeMetabolicEquivalent) {
            writer.u8(stepClimberData.getMetabolicEquivalentTenths())
        }
        if (stepClimberData.includeElapsedTime) {
            writer.u16(stepClimberData.getElapsedTimeSeconds())
        }
        if (stepClimberData.includeRemainingTime) {
            writer.u16(stepClimberData.getRemainingTimeSeconds())
        }

        return writer.bytes()
    }

    private fun buildFlags(): Int {
        var flags = 0
        if (stepClimberData.includeStepsPerMinute) flags = flags or (1 shl StepClimberDataFlags.STEPS_PER_MINUTE_PRESENT)
        if (stepClimberData.includeAverageStepRate) flags = flags or (1 shl StepClimberDataFlags.AVERAGE_STEP_RATE_PRESENT)
        if (stepClimberData.includePositiveElevationGain) flags = flags or (1 shl StepClimberDataFlags.POSITIVE_ELEVATION_GAIN_PRESENT)
        if (stepClimberData.includeExpendedEnergy) flags = flags or (1 shl StepClimberDataFlags.EXPENDED_ENERGY_PRESENT)
        if (stepClimberData.includeHeartRate) flags = flags or (1 shl StepClimberDataFlags.HEART_RATE_PRESENT)
        if (stepClimberData.includeMetabolicEquivalent) flags = flags or (1 shl StepClimberDataFlags.METABOLIC_EQUIVALENT_PRESENT)
        if (stepClimberData.includeElapsedTime) flags = flags or (1 shl StepClimberDataFlags.ELAPSED_TIME_PRESENT)
        if (stepClimberData.includeRemainingTime) flags = flags or (1 shl StepClimberDataFlags.REMAINING_TIME_PRESENT)
        return flags
    }
}
