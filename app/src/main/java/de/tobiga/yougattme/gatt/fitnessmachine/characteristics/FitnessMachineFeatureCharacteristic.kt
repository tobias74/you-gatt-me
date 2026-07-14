package de.tobiga.yougattme.gatt.fitnessmachine.characteristics

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import de.tobiga.yougattme.ApplicationViewModel
import de.tobiga.yougattme.gatt.AbstractGattProfile
import de.tobiga.yougattme.gatt.TobigaGattCharacteristic
import de.tobiga.yougattme.gatt.common.UserDescriptor
import java.util.UUID

private object FitnessMachineFeatureBits {
    const val AVERAGE_SPEED = 0
    const val CADENCE = 1
    const val TOTAL_DISTANCE = 2
    const val INCLINATION = 3
    const val ELEVATION_GAIN = 4
    const val PACE = 5
    const val STEP_COUNT = 6
    const val RESISTANCE_LEVEL = 7
    const val STRIDE_COUNT = 8
    const val EXPENDED_ENERGY = 9
    const val HEART_RATE = 10
    const val METABOLIC_EQUIVALENT = 11
    const val ELAPSED_TIME = 12
    const val REMAINING_TIME = 13
    const val POWER_MEASUREMENT = 14
    const val FORCE_ON_BELT_AND_POWER_OUTPUT = 15
}

private object FitnessMachineTargetSettingFeatureBits {
    const val SPEED_TARGET_SETTING = 0
    const val INCLINATION_TARGET_SETTING = 1
    const val RESISTANCE_TARGET_SETTING = 2
    const val POWER_TARGET_SETTING = 3
    const val HEART_RATE_TARGET_SETTING = 4
    const val TARGETED_EXPENDED_ENERGY = 5
    const val TARGETED_NUMBER_OF_STEPS = 6
    const val TARGETED_NUMBER_OF_STRIDES = 7
    const val TARGETED_DISTANCE = 8
    const val TARGETED_TRAINING_TIME = 9
    const val TARGETED_TIME_IN_TWO_HEART_RATE_ZONES = 10
    const val TARGETED_TIME_IN_THREE_HEART_RATE_ZONES = 11
    const val TARGETED_TIME_IN_FIVE_HEART_RATE_ZONES = 12
    const val INDOOR_BIKE_SIMULATION_PARAMETERS = 13
    const val WHEEL_CIRCUMFERENCE = 14
    const val SPIN_DOWN_CONTROL = 15
    const val TARGETED_CADENCE = 16
}

class FitnessMachineFeatureCharacteristic(
    gattProfile: AbstractGattProfile,
    private val machineType: String,
    private val mainViewModel: ApplicationViewModel
) : TobigaGattCharacteristic(gattProfile) {

    private val GATT_FITNESS_MACHINE_FEATURE_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("00002ACC-0000-1000-8000-00805f9b34fb")

    override fun getCharacteristicUUID(): UUID {
        return GATT_FITNESS_MACHINE_FEATURE_CHARACTERISTIC_UUID
    }

    init {
        activeDescriptors.add(UserDescriptor(gattProfile))

        gattCharacteristic = BluetoothGattCharacteristic(
            GATT_FITNESS_MACHINE_FEATURE_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        activeDescriptors.forEach {
            gattCharacteristic.addDescriptor(it.gattDescriptor)
        }
    }

    override fun onReadRequest(device: BluetoothDevice, requestId: Int, offset: Int) {
        val data = getFeatureCharacteristicData()
        if (offset > data.size) {
            gattProfile.bluetoothGattServer!!.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            return
        }

        gattProfile.bluetoothGattServer!!.sendResponse(
            device,
            requestId,
            BluetoothGatt.GATT_SUCCESS,
            offset,
            data.copyOfRange(offset, data.size)
        )
    }

    override fun onWriteRequest(
        device: BluetoothDevice,
        requestId: Int,
        preparedWrite: Boolean,
        responseNeeded: Boolean,
        offset: Int,
        value: ByteArray
    ) {
        if (responseNeeded) {
            gattProfile.bluetoothGattServer!!.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_WRITE_NOT_PERMITTED,
                offset,
                null
            )
        }
    }

    private fun getFeatureCharacteristicData(): ByteArray {
        val fitnessMachineFeatures = when (machineType) {
            "indoor_bike" -> indoorBikeFeatures()
            "rower" -> rowerFeatures()
            "treadmill" -> treadmillFeatures()
            "cross_trainer" -> crossTrainerFeatures()
            "stair_climber" -> stairClimberFeatures()
            "step_climber" -> stepClimberFeatures()
            else -> features(
                FitnessMachineFeatureBits.CADENCE,
                FitnessMachineFeatureBits.TOTAL_DISTANCE,
                FitnessMachineFeatureBits.HEART_RATE,
                FitnessMachineFeatureBits.ELAPSED_TIME,
                FitnessMachineFeatureBits.POWER_MEASUREMENT
            )
        }

        val targetSettingFeatures = targetSettingFeatures()
        return littleEndianUInt32(fitnessMachineFeatures) + littleEndianUInt32(targetSettingFeatures)
    }

    private fun targetSettingFeatures(): Int {
        if (!mainViewModel.ftmsControlPointEnabled) {
            return 0
        }
        val targetFeatures = mainViewModel.ftmsTargetFeatureDeclarationsForGatt()
        val featureBits = mutableListOf<Int>()

        if (targetFeatures.targetSpeed && supportsTargetSpeed()) featureBits.add(FitnessMachineTargetSettingFeatureBits.SPEED_TARGET_SETTING)
        if (targetFeatures.targetInclination && supportsTargetInclination()) featureBits.add(FitnessMachineTargetSettingFeatureBits.INCLINATION_TARGET_SETTING)
        if (targetFeatures.targetResistanceLevel && supportsTargetResistance()) featureBits.add(FitnessMachineTargetSettingFeatureBits.RESISTANCE_TARGET_SETTING)
        if (targetFeatures.targetPower && supportsTargetPower()) featureBits.add(FitnessMachineTargetSettingFeatureBits.POWER_TARGET_SETTING)
        if (targetFeatures.targetHeartRate) featureBits.add(FitnessMachineTargetSettingFeatureBits.HEART_RATE_TARGET_SETTING)
        if (targetFeatures.targetedExpendedEnergy) featureBits.add(FitnessMachineTargetSettingFeatureBits.TARGETED_EXPENDED_ENERGY)
        if (targetFeatures.targetedSteps && supportsTargetSteps()) featureBits.add(FitnessMachineTargetSettingFeatureBits.TARGETED_NUMBER_OF_STEPS)
        if (targetFeatures.targetedStrides && supportsTargetStrides()) featureBits.add(FitnessMachineTargetSettingFeatureBits.TARGETED_NUMBER_OF_STRIDES)
        if (targetFeatures.targetedDistance && supportsTargetDistance()) featureBits.add(FitnessMachineTargetSettingFeatureBits.TARGETED_DISTANCE)
        if (targetFeatures.targetedTrainingTime) featureBits.add(FitnessMachineTargetSettingFeatureBits.TARGETED_TRAINING_TIME)
        if (targetFeatures.targetedTimeInTwoHeartRateZones) featureBits.add(FitnessMachineTargetSettingFeatureBits.TARGETED_TIME_IN_TWO_HEART_RATE_ZONES)
        if (targetFeatures.targetedTimeInThreeHeartRateZones) featureBits.add(FitnessMachineTargetSettingFeatureBits.TARGETED_TIME_IN_THREE_HEART_RATE_ZONES)
        if (targetFeatures.targetedTimeInFiveHeartRateZones) featureBits.add(FitnessMachineTargetSettingFeatureBits.TARGETED_TIME_IN_FIVE_HEART_RATE_ZONES)
        if (targetFeatures.indoorBikeSimulationParameters && supportsIndoorBikeSimulation()) featureBits.add(FitnessMachineTargetSettingFeatureBits.INDOOR_BIKE_SIMULATION_PARAMETERS)
        if (targetFeatures.wheelCircumference && supportsWheelCircumference()) featureBits.add(FitnessMachineTargetSettingFeatureBits.WHEEL_CIRCUMFERENCE)
        if (targetFeatures.spinDownControl && supportsSpinDown()) featureBits.add(FitnessMachineTargetSettingFeatureBits.SPIN_DOWN_CONTROL)
        if (targetFeatures.targetedCadence && supportsTargetCadence()) featureBits.add(FitnessMachineTargetSettingFeatureBits.TARGETED_CADENCE)

        return features(*featureBits.toIntArray())
    }

    private fun supportsTargetSpeed(): Boolean = machineType in setOf("treadmill", "cross_trainer")
    private fun supportsTargetInclination(): Boolean = machineType in setOf("treadmill", "cross_trainer")
    private fun supportsTargetResistance(): Boolean = machineType in setOf("indoor_bike", "rower", "cross_trainer")
    private fun supportsTargetPower(): Boolean = machineType in setOf("indoor_bike", "treadmill", "rower", "cross_trainer")
    private fun supportsTargetSteps(): Boolean = machineType in setOf("cross_trainer", "step_climber")
    private fun supportsTargetStrides(): Boolean = machineType in setOf("cross_trainer", "stair_climber")
    private fun supportsTargetDistance(): Boolean = machineType in setOf("indoor_bike", "treadmill", "rower", "cross_trainer")
    private fun supportsTargetCadence(): Boolean = machineType in setOf("indoor_bike", "rower", "cross_trainer")
    private fun supportsIndoorBikeSimulation(): Boolean = machineType == "indoor_bike"
    private fun supportsWheelCircumference(): Boolean = machineType == "indoor_bike"
    private fun supportsSpinDown(): Boolean = machineType == "indoor_bike"

    private fun treadmillFeatures(): Int {
        val data = mainViewModel.ftmsTreadmillData
        val featureBits = mutableListOf<Int>()

        if (data.includeAverageSpeed) featureBits.add(FitnessMachineFeatureBits.AVERAGE_SPEED)
        if (data.includeTotalDistance) featureBits.add(FitnessMachineFeatureBits.TOTAL_DISTANCE)
        if (data.includeInclination) featureBits.add(FitnessMachineFeatureBits.INCLINATION)
        if (data.includeElevationGain) featureBits.add(FitnessMachineFeatureBits.ELEVATION_GAIN)
        if (data.includeInstantaneousPace || data.includeAveragePace) featureBits.add(FitnessMachineFeatureBits.PACE)
        if (data.includeExpendedEnergy) featureBits.add(FitnessMachineFeatureBits.EXPENDED_ENERGY)
        if (data.includeHeartRate) featureBits.add(FitnessMachineFeatureBits.HEART_RATE)
        if (data.includeMetabolicEquivalent) featureBits.add(FitnessMachineFeatureBits.METABOLIC_EQUIVALENT)
        if (data.includeElapsedTime) featureBits.add(FitnessMachineFeatureBits.ELAPSED_TIME)
        if (data.includeRemainingTime) featureBits.add(FitnessMachineFeatureBits.REMAINING_TIME)
        if (data.includeForceOnBeltAndPowerOutput) featureBits.add(FitnessMachineFeatureBits.FORCE_ON_BELT_AND_POWER_OUTPUT)

        return features(*featureBits.toIntArray())
    }

    private fun rowerFeatures(): Int {
        val data = mainViewModel.ftmsRowerData
        val featureBits = mutableListOf<Int>()

        if (data.includeTotalDistance) featureBits.add(FitnessMachineFeatureBits.TOTAL_DISTANCE)
        if (data.includeInstantaneousPace || data.includeAveragePace) featureBits.add(FitnessMachineFeatureBits.PACE)
        if (data.includeResistanceLevel) featureBits.add(FitnessMachineFeatureBits.RESISTANCE_LEVEL)
        if (data.includeExpendedEnergy) featureBits.add(FitnessMachineFeatureBits.EXPENDED_ENERGY)
        if (data.includeHeartRate) featureBits.add(FitnessMachineFeatureBits.HEART_RATE)
        if (data.includeMetabolicEquivalent) featureBits.add(FitnessMachineFeatureBits.METABOLIC_EQUIVALENT)
        if (data.includeElapsedTime) featureBits.add(FitnessMachineFeatureBits.ELAPSED_TIME)
        if (data.includeRemainingTime) featureBits.add(FitnessMachineFeatureBits.REMAINING_TIME)
        if (data.includeInstantaneousPower || data.includeAveragePower) featureBits.add(FitnessMachineFeatureBits.POWER_MEASUREMENT)

        return features(*featureBits.toIntArray())
    }

    private fun crossTrainerFeatures(): Int {
        val data = mainViewModel.ftmsCrossTrainerData
        val featureBits = mutableListOf<Int>()

        if (data.includeAverageSpeed) featureBits.add(FitnessMachineFeatureBits.AVERAGE_SPEED)
        if (data.includeTotalDistance) featureBits.add(FitnessMachineFeatureBits.TOTAL_DISTANCE)
        if (data.includeStepCount) featureBits.add(FitnessMachineFeatureBits.STEP_COUNT)
        if (data.includeStrideCount) featureBits.add(FitnessMachineFeatureBits.STRIDE_COUNT)
        if (data.includeElevationGain) featureBits.add(FitnessMachineFeatureBits.ELEVATION_GAIN)
        if (data.includeInclination) featureBits.add(FitnessMachineFeatureBits.INCLINATION)
        if (data.includeResistanceLevel) featureBits.add(FitnessMachineFeatureBits.RESISTANCE_LEVEL)
        if (data.includeInstantaneousPower || data.includeAveragePower) featureBits.add(FitnessMachineFeatureBits.POWER_MEASUREMENT)
        if (data.includeExpendedEnergy) featureBits.add(FitnessMachineFeatureBits.EXPENDED_ENERGY)
        if (data.includeHeartRate) featureBits.add(FitnessMachineFeatureBits.HEART_RATE)
        if (data.includeMetabolicEquivalent) featureBits.add(FitnessMachineFeatureBits.METABOLIC_EQUIVALENT)
        if (data.includeElapsedTime) featureBits.add(FitnessMachineFeatureBits.ELAPSED_TIME)
        if (data.includeRemainingTime) featureBits.add(FitnessMachineFeatureBits.REMAINING_TIME)

        return features(*featureBits.toIntArray())
    }

    private fun stepClimberFeatures(): Int {
        val data = mainViewModel.ftmsStepClimberData
        val featureBits = mutableListOf<Int>()

        featureBits.add(FitnessMachineFeatureBits.STEP_COUNT)
        if (data.includePositiveElevationGain) featureBits.add(FitnessMachineFeatureBits.ELEVATION_GAIN)
        if (data.includeExpendedEnergy) featureBits.add(FitnessMachineFeatureBits.EXPENDED_ENERGY)
        if (data.includeHeartRate) featureBits.add(FitnessMachineFeatureBits.HEART_RATE)
        if (data.includeMetabolicEquivalent) featureBits.add(FitnessMachineFeatureBits.METABOLIC_EQUIVALENT)
        if (data.includeElapsedTime) featureBits.add(FitnessMachineFeatureBits.ELAPSED_TIME)
        if (data.includeRemainingTime) featureBits.add(FitnessMachineFeatureBits.REMAINING_TIME)

        return features(*featureBits.toIntArray())
    }

    private fun stairClimberFeatures(): Int {
        val data = mainViewModel.ftmsStairClimberData
        val featureBits = mutableListOf<Int>()

        if (data.includeStepsPerMinute || data.includeAverageStepRate) featureBits.add(FitnessMachineFeatureBits.STEP_COUNT)
        if (data.includeStrideCount) featureBits.add(FitnessMachineFeatureBits.STRIDE_COUNT)
        if (data.includePositiveElevationGain) featureBits.add(FitnessMachineFeatureBits.ELEVATION_GAIN)
        if (data.includeExpendedEnergy) featureBits.add(FitnessMachineFeatureBits.EXPENDED_ENERGY)
        if (data.includeHeartRate) featureBits.add(FitnessMachineFeatureBits.HEART_RATE)
        if (data.includeMetabolicEquivalent) featureBits.add(FitnessMachineFeatureBits.METABOLIC_EQUIVALENT)
        if (data.includeElapsedTime) featureBits.add(FitnessMachineFeatureBits.ELAPSED_TIME)
        if (data.includeRemainingTime) featureBits.add(FitnessMachineFeatureBits.REMAINING_TIME)

        return features(*featureBits.toIntArray())
    }

    private fun indoorBikeFeatures(): Int {
        val indoorBikeData = mainViewModel.ftmsIndoorBikeData
        val featureBits = mutableListOf<Int>()

        if (indoorBikeData.includeAverageSpeed) {
            featureBits.add(FitnessMachineFeatureBits.AVERAGE_SPEED)
        }
        if (indoorBikeData.includeInstantaneousCadence || indoorBikeData.includeAverageCadence) {
            featureBits.add(FitnessMachineFeatureBits.CADENCE)
        }
        if (indoorBikeData.includeTotalDistance) {
            featureBits.add(FitnessMachineFeatureBits.TOTAL_DISTANCE)
        }
        if (indoorBikeData.includeResistanceLevel) {
            featureBits.add(FitnessMachineFeatureBits.RESISTANCE_LEVEL)
        }
        if (indoorBikeData.includeExpendedEnergy) {
            featureBits.add(FitnessMachineFeatureBits.EXPENDED_ENERGY)
        }
        if (indoorBikeData.includeHeartRate) {
            featureBits.add(FitnessMachineFeatureBits.HEART_RATE)
        }
        if (indoorBikeData.includeMetabolicEquivalent) {
            featureBits.add(FitnessMachineFeatureBits.METABOLIC_EQUIVALENT)
        }
        if (indoorBikeData.includeElapsedTime) {
            featureBits.add(FitnessMachineFeatureBits.ELAPSED_TIME)
        }
        if (indoorBikeData.includeRemainingTime) {
            featureBits.add(FitnessMachineFeatureBits.REMAINING_TIME)
        }
        if (indoorBikeData.includeInstantaneousPower || indoorBikeData.includeAveragePower) {
            featureBits.add(FitnessMachineFeatureBits.POWER_MEASUREMENT)
        }

        return features(*featureBits.toIntArray())
    }

    private fun features(vararg bits: Int): Int {
        return bits.fold(0) { value, bit -> value or (1 shl bit) }
    }

    private fun littleEndianUInt32(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }
}
