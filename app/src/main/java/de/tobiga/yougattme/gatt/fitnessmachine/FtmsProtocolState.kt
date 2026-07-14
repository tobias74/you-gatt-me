package de.tobiga.yougattme.gatt.fitnessmachine

data class FtmsTargetFeatures(
    var targetSpeed: Boolean = false,
    var targetInclination: Boolean = false,
    var targetResistanceLevel: Boolean = false,
    var targetPower: Boolean = false,
    var targetHeartRate: Boolean = false,
    var targetedExpendedEnergy: Boolean = false,
    var targetedSteps: Boolean = false,
    var targetedStrides: Boolean = false,
    var targetedDistance: Boolean = false,
    var targetedTrainingTime: Boolean = false,
    var targetedTimeInTwoHeartRateZones: Boolean = false,
    var targetedTimeInThreeHeartRateZones: Boolean = false,
    var targetedTimeInFiveHeartRateZones: Boolean = false,
    var indoorBikeSimulationParameters: Boolean = false,
    var wheelCircumference: Boolean = false,
    var spinDownControl: Boolean = false,
    var targetedCadence: Boolean = false
) {
    fun copyFrom(other: FtmsTargetFeatures) {
        targetSpeed = other.targetSpeed
        targetInclination = other.targetInclination
        targetResistanceLevel = other.targetResistanceLevel
        targetPower = other.targetPower
        targetHeartRate = other.targetHeartRate
        targetedExpendedEnergy = other.targetedExpendedEnergy
        targetedSteps = other.targetedSteps
        targetedStrides = other.targetedStrides
        targetedDistance = other.targetedDistance
        targetedTrainingTime = other.targetedTrainingTime
        targetedTimeInTwoHeartRateZones = other.targetedTimeInTwoHeartRateZones
        targetedTimeInThreeHeartRateZones = other.targetedTimeInThreeHeartRateZones
        targetedTimeInFiveHeartRateZones = other.targetedTimeInFiveHeartRateZones
        indoorBikeSimulationParameters = other.indoorBikeSimulationParameters
        wheelCircumference = other.wheelCircumference
        spinDownControl = other.spinDownControl
        targetedCadence = other.targetedCadence
    }
}

data class FtmsHeartRateZoneTargets(
    val zoneCount: Int,
    val seconds: List<Int>
) {
    fun summary(): String = "HR zones $zoneCount ${seconds.joinToString("/") { "${it}s" }}"
}

data class FtmsSpinDownTarget(
    val status: Int,
    val targetSpeedLowKmh: Float? = null,
    val targetSpeedHighKmh: Float? = null,
    val ignored: Boolean = false
) {
    fun summary(): String {
        val label = if (ignored) {
            "ignored"
        } else when (status) {
            FTMS_SPIN_DOWN_STATUS_REQUESTED -> "requested"
            FTMS_SPIN_DOWN_STATUS_SUCCESS -> "success"
            FTMS_SPIN_DOWN_STATUS_ERROR -> "error"
            FTMS_SPIN_DOWN_STATUS_STOP_PEDALING -> "stop pedaling"
            else -> "unknown"
        }
        return if (targetSpeedLowKmh != null && targetSpeedHighKmh != null) {
            "spin-down $label ${formatOne(targetSpeedLowKmh)}-${formatOne(targetSpeedHighKmh)} km/h"
        } else {
            "spin-down $label"
        }
    }

    private fun formatOne(value: Float): String = String.format(java.util.Locale.US, "%.1f", value)
}

data class FtmsTargetState(
    val speedKmh: Float? = null,
    val inclinationPercent: Float? = null,
    val resistanceLevel: Float? = null,
    val powerWatt: Int? = null,
    val heartRateBpm: Int? = null,
    val expendedEnergyKcal: Int? = null,
    val steps: Int? = null,
    val strides: Int? = null,
    val distanceMeters: Int? = null,
    val trainingTimeSeconds: Int? = null,
    val heartRateZoneTargets: FtmsHeartRateZoneTargets? = null,
    val indoorBikeSimulation: IndoorCyclingSimulationParameters? = null,
    val wheelCircumferenceMm: Float? = null,
    val spinDown: FtmsSpinDownTarget? = null,
    val cadenceRpm: Float? = null
) {
    fun summary(): String {
        val activeTargets = buildList {
            speedKmh?.let { add("speed ${formatOne(it)} km/h") }
            inclinationPercent?.let { add("incline ${formatOne(it)}%") }
            resistanceLevel?.let { add("resistance ${formatOne(it)}") }
            powerWatt?.let { add("power ${it} W") }
            heartRateBpm?.let { add("HR ${it} bpm") }
            expendedEnergyKcal?.let { add("energy ${it} kcal") }
            steps?.let { add("steps $it") }
            strides?.let { add("strides $it") }
            distanceMeters?.let { add("distance ${it} m") }
            trainingTimeSeconds?.let { add("time ${it}s") }
            heartRateZoneTargets?.let { add(it.summary()) }
            indoorBikeSimulation?.let { add("bike sim grade ${formatOne(it.gradePercent.toFloat())}%") }
            wheelCircumferenceMm?.let { add("wheel ${formatOne(it)} mm") }
            spinDown?.let { add(it.summary()) }
            cadenceRpm?.let { add("cadence ${formatOne(it)} rpm") }
        }
        return if (activeTargets.isEmpty()) {
            "Targets: none"
        } else {
            "Targets: ${activeTargets.joinToString(", ")}"
        }
    }

    private fun formatOne(value: Float): String = String.format(java.util.Locale.US, "%.1f", value)
}

data class FtmsSupportedRanges(
    val minSpeedKmh: Float = 0.0f,
    val maxSpeedKmh: Float = 60.0f,
    val speedIncrementKmh: Float = 0.1f,
    val minInclinationPercent: Float = -20.0f,
    val maxInclinationPercent: Float = 20.0f,
    val inclinationIncrementPercent: Float = 0.1f,
    val minResistanceLevel: Float = 0.0f,
    val maxResistanceLevel: Float = 100.0f,
    val resistanceIncrement: Float = 0.1f,
    val minHeartRateBpm: Int = 40,
    val maxHeartRateBpm: Int = 220,
    val heartRateIncrementBpm: Int = 1,
    val minPowerWatt: Int = 0,
    val maxPowerWatt: Int = 2000,
    val powerIncrementWatt: Int = 1,
    val spinDownTargetSpeedLowKmh: Float = 20.0f,
    val spinDownTargetSpeedHighKmh: Float = 25.0f
) {
    fun speedRaw(valueKmh: Float): Int = (valueKmh * 100f).roundToFtmsUInt16()
    fun inclinationRaw(valuePercent: Float): Int = (valuePercent * 10f).roundToFtmsSInt16()
    fun resistanceRaw(value: Float): Int = (value * 10f).roundToFtmsSInt16()
}

const val FTMS_SPIN_DOWN_STATUS_REQUESTED = 0x01
const val FTMS_SPIN_DOWN_STATUS_SUCCESS = 0x02
const val FTMS_SPIN_DOWN_STATUS_ERROR = 0x03
const val FTMS_SPIN_DOWN_STATUS_STOP_PEDALING = 0x04
