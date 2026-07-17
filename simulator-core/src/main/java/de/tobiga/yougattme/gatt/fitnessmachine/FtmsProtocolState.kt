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
            indoorBikeSimulation?.let { add(it.summary()) }
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

data class FtmsMeasurementSnapshot(
    val machineType: String,
    val characteristicUuid: String,
    val logicalRecordHex: String,
    val notificationPacketHex: List<String>,
    val previewPayloadBytes: Int,
    val timestampMillis: Long
)

data class FtmsProtocolEvent(
    val sequence: Long,
    val timestampMillis: Long,
    val direction: String,
    val channel: String,
    val title: String,
    val payloadHex: String
)

fun IndoorCyclingSimulationParameters.summary(): String {
    return "bike sim wind ${formatOne(windSpeedMps)} m/s, " +
        "grade ${formatOne(gradePercent.toFloat())}%, " +
        "Crr ${formatFour(crr)}, " +
        "Cw ${formatTwo(cw)}"
}

private fun formatOne(value: Float): String = String.format(java.util.Locale.US, "%.1f", value)

private fun formatTwo(value: Float): String = String.format(java.util.Locale.US, "%.2f", value)

private fun formatFour(value: Float): String = String.format(java.util.Locale.US, "%.4f", value)

data class FtmsSupportedRanges(
    val minSpeedKmh: Float = 0.0f,
    val maxSpeedKmh: Float = 60.0f,
    val speedIncrementKmh: Float = 0.1f,
    val minInclinationPercent: Float = -20.0f,
    val maxInclinationPercent: Float = 20.0f,
    val inclinationIncrementPercent: Float = 0.1f,
    val minResistanceLevel: Float = 0.0f,
    val maxResistanceLevel: Float = 25.5f,
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

    fun supportsSpeedRaw(raw: Int): Boolean = supportsIncrement(
        raw,
        speedRaw(minSpeedKmh),
        speedRaw(maxSpeedKmh),
        speedRaw(speedIncrementKmh)
    )

    fun supportsInclinationRaw(raw: Int): Boolean = supportsIncrement(
        raw,
        inclinationRaw(minInclinationPercent),
        inclinationRaw(maxInclinationPercent),
        inclinationRaw(inclinationIncrementPercent)
    )

    fun supportsResistanceRaw(raw: Int): Boolean = supportsIncrement(
        raw,
        resistanceRaw(minResistanceLevel),
        resistanceRaw(maxResistanceLevel),
        resistanceRaw(resistanceIncrement)
    )

    fun supportsPower(raw: Int): Boolean = supportsIncrement(
        raw,
        minPowerWatt,
        maxPowerWatt,
        powerIncrementWatt
    )

    fun supportsHeartRate(raw: Int): Boolean = supportsIncrement(
        raw,
        minHeartRateBpm,
        maxHeartRateBpm,
        heartRateIncrementBpm
    )

    private fun supportsIncrement(raw: Int, minimum: Int, maximum: Int, increment: Int): Boolean {
        return raw in minimum..maximum && increment > 0 && (raw - minimum) % increment == 0
    }
}

const val FTMS_SPIN_DOWN_STATUS_REQUESTED = 0x01
const val FTMS_SPIN_DOWN_STATUS_SUCCESS = 0x02
const val FTMS_SPIN_DOWN_STATUS_ERROR = 0x03
const val FTMS_SPIN_DOWN_STATUS_STOP_PEDALING = 0x04
