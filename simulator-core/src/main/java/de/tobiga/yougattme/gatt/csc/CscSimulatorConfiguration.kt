package de.tobiga.yougattme.gatt.csc

import de.tobiga.yougattme.gatt.common.SensorLocationValues

object CscFeatureBits {
    const val WHEEL_REVOLUTION_DATA = 1 shl 0
    const val CRANK_REVOLUTION_DATA = 1 shl 1
    const val MULTIPLE_LOCATIONS = 1 shl 2
    const val ALL = 0x07
}

object CscControlPointOpCodes {
    const val SET_CUMULATIVE_VALUE = 0x01
    const val UPDATE_SENSOR_LOCATION = 0x03
    const val REQUEST_SUPPORTED_SENSOR_LOCATIONS = 0x04
    const val RESPONSE_CODE = 0x10
}

class CscSimulatorConfiguration {
    var mirrorActualIntoFeature: Boolean = true
    var declaredFeatureBits: Int = CscFeatureBits.ALL
    var featureIndicationsEnabled: Boolean = false

    var includeWheelRevolutionData: Boolean = true
    var includeCrankRevolutionData: Boolean = true

    var sensorLocationCharacteristicEnabled: Boolean = true
    var controlPointCharacteristicEnabled: Boolean = true
    var sensorLocation: Int = SensorLocationValues.CHAINSTAY
    val supportedSensorLocations: MutableSet<Int> = linkedSetOf(
        SensorLocationValues.FRONT_WHEEL,
        SensorLocationValues.LEFT_CRANK,
        SensorLocationValues.RIGHT_CRANK,
        SensorLocationValues.CHAINSTAY,
        SensorLocationValues.REAR_WHEEL,
        SensorLocationValues.REAR_HUB
    )
    val supportedProcedures: MutableSet<Int> = linkedSetOf(
        CscControlPointOpCodes.SET_CUMULATIVE_VALUE,
        CscControlPointOpCodes.UPDATE_SENSOR_LOCATION,
        CscControlPointOpCodes.REQUEST_SUPPORTED_SENSOR_LOCATIONS
    )

    fun actualFeatureBits(): Int {
        var bits = 0
        if (includeWheelRevolutionData) bits = bits or CscFeatureBits.WHEEL_REVOLUTION_DATA
        if (includeCrankRevolutionData) bits = bits or CscFeatureBits.CRANK_REVOLUTION_DATA
        if (
            CscControlPointOpCodes.UPDATE_SENSOR_LOCATION in supportedProcedures ||
            CscControlPointOpCodes.REQUEST_SUPPORTED_SENSOR_LOCATIONS in supportedProcedures
        ) {
            bits = bits or CscFeatureBits.MULTIPLE_LOCATIONS
        }
        return bits
    }

    fun featureBitsForGatt(): Int =
        if (mirrorActualIntoFeature) actualFeatureBits() else declaredFeatureBits and CscFeatureBits.ALL
}
