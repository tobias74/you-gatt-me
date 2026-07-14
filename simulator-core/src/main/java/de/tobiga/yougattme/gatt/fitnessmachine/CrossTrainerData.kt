package de.tobiga.yougattme.gatt.fitnessmachine

import kotlin.math.atan
import kotlin.math.max
import kotlin.math.roundToInt

class CrossTrainerData {

    var includeAverageSpeed: Boolean = false
    var includeTotalDistance: Boolean = true
    var includeStepCount: Boolean = true
    var includeStrideCount: Boolean = false
    var includeElevationGain: Boolean = false
    var includeInclination: Boolean = false
    var includeResistanceLevel: Boolean = false
    var includeInstantaneousPower: Boolean = true
    var includeAveragePower: Boolean = false
    var includeExpendedEnergy: Boolean = false
    var includeHeartRate: Boolean = true
    var includeMetabolicEquivalent: Boolean = false
    var includeElapsedTime: Boolean = true
    var includeRemainingTime: Boolean = false
    var movementDirectionBackward: Boolean = false

    var instantaneousSpeed: Float = 10.0f
    var instantaneousStepsPerMinute: Int = 80
    var resistanceLevel: Int = 20
    var instantaneousPower: Int = 240
    var inclinationPercent: Float = 0.0f
    var heartRate: Int = 120
    var metabolicEquivalent: Float = 8.0f
    var targetDurationSeconds: Int = 30 * 60

    val totalDistance: Int
        get() = totalDistanceMeters.roundToInt().ftmsUInt24()

    val elapsedTime: Float
        get() = elapsedTimeSeconds.toFloat()

    val averageStepsPerMinute: Int
        get() = getAverageStepRateSpm()

    val totalStepCount: Int
        get() = totalStepCountAccumulator.roundToFtmsUInt16()

    val strideCount: Int
        get() = (totalStepCountAccumulator / 2.0).roundToFtmsUInt16()

    private var elapsedTimeSeconds = 0.0
    private var totalDistanceMeters = 0.0
    private var totalStepCountAccumulator = 0.0
    private var positiveElevationMeters = 0.0
    private var negativeElevationMeters = 0.0
    private var powerTimeIntegral = 0.0
    private var totalEnergyKcal = 0.0

    fun advanceTime(milliseconds: Long, workoutRunning: Boolean) {
        if (!workoutRunning || milliseconds <= 0) {
            return
        }

        val deltaSeconds = milliseconds / 1000.0
        val speedMetersPerSecond = instantaneousSpeed.coerceAtLeast(0f).toDouble() / 3.6
        val distanceDelta = speedMetersPerSecond * deltaSeconds
        val stepDelta = instantaneousStepsPerMinute.coerceAtLeast(0) * deltaSeconds / 60.0
        val elevationDelta = distanceDelta * inclinationPercent.toDouble() / 100.0

        elapsedTimeSeconds += deltaSeconds
        totalDistanceMeters += distanceDelta
        totalStepCountAccumulator += stepDelta
        if (elevationDelta >= 0.0) {
            positiveElevationMeters += elevationDelta
        } else {
            negativeElevationMeters += -elevationDelta
        }
        powerTimeIntegral += instantaneousPower * deltaSeconds
        totalEnergyKcal += max(0.0, instantaneousPower.toDouble()) * deltaSeconds / FTMS_JOULES_PER_KILOCALORIE
    }

    fun resetSession() {
        elapsedTimeSeconds = 0.0
        totalDistanceMeters = 0.0
        totalStepCountAccumulator = 0.0
        positiveElevationMeters = 0.0
        negativeElevationMeters = 0.0
        powerTimeIntegral = 0.0
        totalEnergyKcal = 0.0
    }

    fun reset() {
        resetSession()
    }

    fun postIncludeInstantaneousPower(value: Boolean) { includeInstantaneousPower = value }
    fun postIncludeHeartRate(value: Boolean) { includeHeartRate = value }
    fun postIncludeTotalDistance(value: Boolean) { includeTotalDistance = value }
    fun postIncludeElapsedTime(value: Boolean) { includeElapsedTime = value }
    fun postInstantaneousSpeed(value: Float) { instantaneousSpeed = value }
    fun postInstantaneousPower(value: Int) { instantaneousPower = value }
    fun postHeartRate(value: Int) { heartRate = value }
    fun postInstantaneousStepsPerMinute(value: Int) { instantaneousStepsPerMinute = value }

    fun getInstantaneousSpeedHundredthsKmh(): Int {
        return (instantaneousSpeed.coerceAtLeast(0f) * 100f).roundToFtmsUInt16()
    }

    fun getAverageSpeedHundredthsKmh(): Int {
        if (elapsedTimeSeconds <= 0.0) {
            return 0
        }
        return (totalDistanceMeters / elapsedTimeSeconds * 3.6 * 100.0).roundToFtmsUInt16()
    }

    fun getTotalDistanceMeters(): Int {
        return totalDistanceMeters.roundToFtmsUInt24()
    }

    fun getInstantaneousStepRateSpm(): Int {
        return instantaneousStepsPerMinute.ftmsUInt16()
    }

    fun getAverageStepRateSpm(): Int {
        if (elapsedTimeSeconds <= 0.0) {
            return 0
        }
        return (totalStepCountAccumulator * 60.0 / elapsedTimeSeconds).roundToFtmsUInt16()
    }

    fun getStrideCountTenths(): Int {
        return (totalStepCountAccumulator / 2.0 * 10.0).roundToFtmsUInt16()
    }

    fun getPositiveElevationGainMeters(): Int {
        return positiveElevationMeters.roundToFtmsUInt16()
    }

    fun getNegativeElevationGainMeters(): Int {
        return negativeElevationMeters.roundToFtmsUInt16()
    }

    fun getInclinationTenthsPercent(): Int {
        return (inclinationPercent * 10f).roundToFtmsSInt16()
    }

    fun getRampAngleTenthsDegree(): Int {
        val angleDegrees = Math.toDegrees(atan(inclinationPercent.toDouble() / 100.0))
        return (angleDegrees * 10.0).roundToFtmsSInt16()
    }

    fun getResistanceLevelTenths(): Int {
        return (resistanceLevel * 10).ftmsSInt16()
    }

    fun getInstantaneousPowerWatt(): Int {
        return instantaneousPower.ftmsSInt16()
    }

    fun getAveragePowerWatt(): Int {
        if (elapsedTimeSeconds <= 0.0) {
            return 0
        }
        return (powerTimeIntegral / elapsedTimeSeconds).roundToFtmsSInt16()
    }

    fun getTotalEnergyKcal(): Int {
        return totalEnergyKcal.roundToFtmsUInt16()
    }

    fun getEnergyPerHourKcal(): Int {
        if (elapsedTimeSeconds <= 0.0) {
            return 0
        }
        return (totalEnergyKcal * 3600.0 / elapsedTimeSeconds).roundToFtmsUInt16()
    }

    fun getEnergyPerMinuteKcal(): Int {
        if (elapsedTimeSeconds <= 0.0) {
            return 0
        }
        return (totalEnergyKcal * 60.0 / elapsedTimeSeconds).roundToFtmsUInt8()
    }

    fun getHeartRateBpm(): Int {
        return heartRate.ftmsUInt8()
    }

    fun getMetabolicEquivalentTenths(): Int {
        return (metabolicEquivalent * 10f).roundToFtmsUInt8()
    }

    fun getElapsedTimeSeconds(): Int {
        return elapsedTimeSeconds.roundToFtmsUInt16()
    }

    fun getRemainingTimeSeconds(): Int {
        return (targetDurationSeconds - elapsedTimeSeconds).roundToFtmsUInt16()
    }

    fun getComputedSummary(): String {
        return "Avg ${getAverageSpeedHundredthsKmh() / 100.0} km/h | " +
            "${getTotalDistanceMeters()} m | " +
            "${totalStepCount} steps | " +
            "${getElapsedTimeSeconds()} s"
    }
}
