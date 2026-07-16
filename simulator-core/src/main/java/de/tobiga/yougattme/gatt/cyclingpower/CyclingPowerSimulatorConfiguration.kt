package de.tobiga.yougattme.gatt.cyclingpower

import de.tobiga.yougattme.gatt.common.SensorLocationValues
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

object CyclingPowerFeatureBits {
    const val PEDAL_POWER_BALANCE = 1 shl 0
    const val ACCUMULATED_TORQUE = 1 shl 1
    const val WHEEL_REVOLUTION_DATA = 1 shl 2
    const val CRANK_REVOLUTION_DATA = 1 shl 3
    const val EXTREME_MAGNITUDES = 1 shl 4
    const val EXTREME_ANGLES = 1 shl 5
    const val DEAD_SPOT_ANGLES = 1 shl 6
    const val ACCUMULATED_ENERGY = 1 shl 7
    const val OFFSET_COMPENSATION_INDICATOR = 1 shl 8
    const val OFFSET_COMPENSATION = 1 shl 9
    const val MEASUREMENT_CONTENT_MASKING = 1 shl 10
    const val MULTIPLE_LOCATIONS = 1 shl 11
    const val CRANK_LENGTH_ADJUSTMENT = 1 shl 12
    const val CHAIN_LENGTH_ADJUSTMENT = 1 shl 13
    const val CHAIN_WEIGHT_ADJUSTMENT = 1 shl 14
    const val SPAN_LENGTH_ADJUSTMENT = 1 shl 15
    const val TORQUE_BASED_CONTEXT = 1 shl 16
    const val INSTANTANEOUS_DIRECTION = 1 shl 17
    const val FACTORY_CALIBRATION_DATE = 1 shl 18
    const val ENHANCED_OFFSET_COMPENSATION = 1 shl 19
    const val DISTRIBUTED_SYSTEM_SHIFT = 20
    const val DEFINED_MASK = 0x003F_FFFF
}

object CyclingPowerControlPointOpCodes {
    const val SET_CUMULATIVE_VALUE = 0x01
    const val UPDATE_SENSOR_LOCATION = 0x02
    const val REQUEST_SUPPORTED_SENSOR_LOCATIONS = 0x03
    const val SET_CRANK_LENGTH = 0x04
    const val REQUEST_CRANK_LENGTH = 0x05
    const val SET_CHAIN_LENGTH = 0x06
    const val REQUEST_CHAIN_LENGTH = 0x07
    const val SET_CHAIN_WEIGHT = 0x08
    const val REQUEST_CHAIN_WEIGHT = 0x09
    const val SET_SPAN_LENGTH = 0x0A
    const val REQUEST_SPAN_LENGTH = 0x0B
    const val START_OFFSET_COMPENSATION = 0x0C
    const val MASK_MEASUREMENT_CONTENT = 0x0D
    const val REQUEST_SAMPLING_RATE = 0x0E
    const val REQUEST_FACTORY_CALIBRATION_DATE = 0x0F
    const val START_ENHANCED_OFFSET_COMPENSATION = 0x10
    const val RESPONSE_CODE = 0x20

    val ALL = (SET_CUMULATIVE_VALUE..START_ENHANCED_OFFSET_COMPENSATION).toSet()
}

class CyclingPowerSimulatorConfiguration {
    var mirrorActualIntoFeature: Boolean = true
    var declaredFeatureBits: Int = CyclingPowerFeatureBits.DEFINED_MASK and
        (3 shl CyclingPowerFeatureBits.DISTRIBUTED_SYSTEM_SHIFT).inv()
    var featureIndicationsEnabled: Boolean = false

    var includePedalPowerBalance: Boolean = true
    var pedalPowerBalanceReferenceIsLeft: Boolean = true
    var includeAccumulatedTorque: Boolean = true
    var accumulatedTorqueSourceIsCrank: Boolean = true
    var includeWheelRevolutionData: Boolean = true
    var includeCrankRevolutionData: Boolean = true
    var includeExtremeMagnitudes: Boolean = true
    var includeExtremeAngles: Boolean = true
    var includeTopDeadSpotAngle: Boolean = true
    var includeBottomDeadSpotAngle: Boolean = true
    var includeAccumulatedEnergy: Boolean = true
    var offsetCompensationIndicatorSupported: Boolean = true
    var offsetCompensationRequired: Boolean = false

    var sensorMeasurementContextTorqueBased: Boolean = false
    var instantaneousMeasurementDirectionSupported: Boolean = true
    var instantaneousMeasurementDirection: Int = 1
    var distributedSystemSupport: Int = 1

    var sensorLocationCharacteristicEnabled: Boolean = true
    var controlPointCharacteristicEnabled: Boolean = true
    var vectorCharacteristicEnabled: Boolean = true
    var measurementBroadcastPropertyEnabled: Boolean = false
    var vectorIncludeCrankData: Boolean = true
    var vectorIncludeFirstCrankAngle: Boolean = true
    var vectorIncludeMagnitudeArray: Boolean = true

    var sensorLocation: Int = SensorLocationValues.LEFT_CRANK
    val supportedSensorLocations: MutableSet<Int> = linkedSetOf(
        SensorLocationValues.LEFT_CRANK,
        SensorLocationValues.RIGHT_CRANK,
        SensorLocationValues.LEFT_PEDAL,
        SensorLocationValues.RIGHT_PEDAL,
        SensorLocationValues.REAR_HUB,
        SensorLocationValues.SPIDER,
        SensorLocationValues.CHAIN_RING
    )
    val supportedProcedures: MutableSet<Int> = CyclingPowerControlPointOpCodes.ALL.toMutableSet()

    var pedalPowerBalancePercent: Double = 50.0
    var accumulatedTorqueNm: Double = 0.0
    var maximumForceMagnitudeN: Int = 780
    var minimumForceMagnitudeN: Int = -90
    var maximumTorqueMagnitudeNm: Double = 65.0
    var minimumTorqueMagnitudeNm: Double = -8.0
    var maximumAngleDegrees: Int = 110
    var minimumAngleDegrees: Int = 250
    var topDeadSpotAngleDegrees: Int = 5
    var bottomDeadSpotAngleDegrees: Int = 185
    var accumulatedEnergyKj: Int = 0

    var crankLengthHalfMillimeters: Int = 350
    var chainLengthMillimeters: Int = 1100
    var chainWeightGrams: Int = 280
    var spanLengthMillimeters: Int = 170
    var offsetCompensationRaw: Int = 0
    var samplingRateHz: Int = 25
    var factoryCalibrationDate: LocalDateTime = LocalDateTime.of(2026, 1, 15, 10, 30, 0)
    var enhancedOffsetCompanyId: Int = 0xFFFF
    var enhancedOffsetManufacturerData: ByteArray = byteArrayOf(0x59, 0x47, 0x4D)
    var enhancedOffsetFailureCode: Int? = null

    val vectorMagnitudes: MutableList<Int> = mutableListOf(
        120, 250, 430, 620, 760, 710, 520, 280, 80, -40, 20, 100
    )

    private val measurementMasksByDevice = ConcurrentHashMap<String, Int>()

    fun measurementMaskFor(deviceAddress: String): Int = measurementMasksByDevice[deviceAddress] ?: 0

    fun setMeasurementMask(deviceAddress: String, mask: Int) {
        measurementMasksByDevice[deviceAddress] = mask and 0x01FF
    }

    fun clearDevice(deviceAddress: String) {
        measurementMasksByDevice.remove(deviceAddress)
    }

    fun clearDevices() {
        measurementMasksByDevice.clear()
    }

    fun actualFeatureBits(): Int {
        var bits = 0
        if (includePedalPowerBalance) bits = bits or CyclingPowerFeatureBits.PEDAL_POWER_BALANCE
        if (includeAccumulatedTorque) bits = bits or CyclingPowerFeatureBits.ACCUMULATED_TORQUE
        if (includeWheelRevolutionData) bits = bits or CyclingPowerFeatureBits.WHEEL_REVOLUTION_DATA
        if (includeCrankRevolutionData || vectorIncludeCrankData) {
            bits = bits or CyclingPowerFeatureBits.CRANK_REVOLUTION_DATA
        }
        if (includeExtremeMagnitudes) bits = bits or CyclingPowerFeatureBits.EXTREME_MAGNITUDES
        if (includeExtremeAngles || vectorIncludeFirstCrankAngle) {
            bits = bits or CyclingPowerFeatureBits.EXTREME_ANGLES
        }
        if (includeTopDeadSpotAngle || includeBottomDeadSpotAngle) {
            bits = bits or CyclingPowerFeatureBits.DEAD_SPOT_ANGLES
        }
        if (includeAccumulatedEnergy) bits = bits or CyclingPowerFeatureBits.ACCUMULATED_ENERGY
        if (offsetCompensationIndicatorSupported) {
            bits = bits or CyclingPowerFeatureBits.OFFSET_COMPENSATION_INDICATOR
        }
        if (CyclingPowerControlPointOpCodes.START_OFFSET_COMPENSATION in supportedProcedures) {
            bits = bits or CyclingPowerFeatureBits.OFFSET_COMPENSATION
        }
        if (CyclingPowerControlPointOpCodes.MASK_MEASUREMENT_CONTENT in supportedProcedures) {
            bits = bits or CyclingPowerFeatureBits.MEASUREMENT_CONTENT_MASKING
        }
        if (
            CyclingPowerControlPointOpCodes.UPDATE_SENSOR_LOCATION in supportedProcedures ||
            CyclingPowerControlPointOpCodes.REQUEST_SUPPORTED_SENSOR_LOCATIONS in supportedProcedures
        ) bits = bits or CyclingPowerFeatureBits.MULTIPLE_LOCATIONS
        if (CyclingPowerControlPointOpCodes.SET_CRANK_LENGTH in supportedProcedures) {
            bits = bits or CyclingPowerFeatureBits.CRANK_LENGTH_ADJUSTMENT
        }
        if (CyclingPowerControlPointOpCodes.SET_CHAIN_LENGTH in supportedProcedures) {
            bits = bits or CyclingPowerFeatureBits.CHAIN_LENGTH_ADJUSTMENT
        }
        if (CyclingPowerControlPointOpCodes.SET_CHAIN_WEIGHT in supportedProcedures) {
            bits = bits or CyclingPowerFeatureBits.CHAIN_WEIGHT_ADJUSTMENT
        }
        if (CyclingPowerControlPointOpCodes.SET_SPAN_LENGTH in supportedProcedures) {
            bits = bits or CyclingPowerFeatureBits.SPAN_LENGTH_ADJUSTMENT
        }
        if (sensorMeasurementContextTorqueBased) bits = bits or CyclingPowerFeatureBits.TORQUE_BASED_CONTEXT
        if (instantaneousMeasurementDirectionSupported) {
            bits = bits or CyclingPowerFeatureBits.INSTANTANEOUS_DIRECTION
        }
        if (CyclingPowerControlPointOpCodes.REQUEST_FACTORY_CALIBRATION_DATE in supportedProcedures) {
            bits = bits or CyclingPowerFeatureBits.FACTORY_CALIBRATION_DATE
        }
        if (CyclingPowerControlPointOpCodes.START_ENHANCED_OFFSET_COMPENSATION in supportedProcedures) {
            bits = bits or CyclingPowerFeatureBits.ENHANCED_OFFSET_COMPENSATION
        }
        bits = bits or ((distributedSystemSupport and 0x03) shl CyclingPowerFeatureBits.DISTRIBUTED_SYSTEM_SHIFT)
        return bits
    }

    fun featureBitsForGatt(): Int = if (mirrorActualIntoFeature) {
        actualFeatureBits()
    } else {
        declaredFeatureBits and CyclingPowerFeatureBits.DEFINED_MASK
    }
}
