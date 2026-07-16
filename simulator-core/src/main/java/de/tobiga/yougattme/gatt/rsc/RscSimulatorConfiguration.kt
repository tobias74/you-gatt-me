package de.tobiga.yougattme.gatt.rsc

import de.tobiga.yougattme.gatt.common.SensorLocationValues

object RscFeatureBits {
    const val STRIDE_LENGTH = 1 shl 0
    const val TOTAL_DISTANCE = 1 shl 1
    const val WALKING_OR_RUNNING = 1 shl 2
    const val CALIBRATION = 1 shl 3
    const val MULTIPLE_LOCATIONS = 1 shl 4
    const val ALL = 0x1F
}

object ScControlPointOpCodes {
    const val SET_CUMULATIVE_VALUE = 0x01
    const val START_SENSOR_CALIBRATION = 0x02
    const val UPDATE_SENSOR_LOCATION = 0x03
    const val REQUEST_SUPPORTED_SENSOR_LOCATIONS = 0x04
    const val RESPONSE_CODE = 0x10
}

class RscSimulatorConfiguration {
    var mirrorActualIntoFeature: Boolean = true
    var declaredFeatureBits: Int = RscFeatureBits.ALL
    var featureIndicationsEnabled: Boolean = false

    var includeStrideLength: Boolean = true
    var includeTotalDistance: Boolean = true
    var includeWalkingOrRunningStatus: Boolean = true
    var runningStatus: Boolean = true

    var sensorLocationCharacteristicEnabled: Boolean = true
    var controlPointCharacteristicEnabled: Boolean = true
    var sensorLocation: Int = SensorLocationValues.IN_SHOE
    val supportedSensorLocations: MutableSet<Int> = linkedSetOf(
        SensorLocationValues.TOP_OF_SHOE,
        SensorLocationValues.IN_SHOE,
        SensorLocationValues.HIP
    )
    val supportedProcedures: MutableSet<Int> = linkedSetOf(
        ScControlPointOpCodes.SET_CUMULATIVE_VALUE,
        ScControlPointOpCodes.START_SENSOR_CALIBRATION,
        ScControlPointOpCodes.UPDATE_SENSOR_LOCATION,
        ScControlPointOpCodes.REQUEST_SUPPORTED_SENSOR_LOCATIONS
    )

    fun actualFeatureBits(): Int {
        var bits = 0
        if (includeStrideLength) bits = bits or RscFeatureBits.STRIDE_LENGTH
        if (includeTotalDistance) bits = bits or RscFeatureBits.TOTAL_DISTANCE
        if (includeWalkingOrRunningStatus) bits = bits or RscFeatureBits.WALKING_OR_RUNNING
        if (ScControlPointOpCodes.START_SENSOR_CALIBRATION in supportedProcedures) {
            bits = bits or RscFeatureBits.CALIBRATION
        }
        if (
            ScControlPointOpCodes.UPDATE_SENSOR_LOCATION in supportedProcedures ||
            ScControlPointOpCodes.REQUEST_SUPPORTED_SENSOR_LOCATIONS in supportedProcedures
        ) {
            bits = bits or RscFeatureBits.MULTIPLE_LOCATIONS
        }
        return bits
    }

    fun featureBitsForGatt(): Int =
        if (mirrorActualIntoFeature) actualFeatureBits() else declaredFeatureBits and RscFeatureBits.ALL
}
