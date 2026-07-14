package de.tobiga.yougattme.gatt.fitnessmachine

import kotlin.math.atan
import kotlin.math.max
import kotlin.math.roundToInt

class TreadmillData {

    var includeAverageSpeed: Boolean = false
    var includeTotalDistance: Boolean = true
    var includeInclination: Boolean = false
    var includeElevationGain: Boolean = false
    var includeInstantaneousPace: Boolean = false
    var includeAveragePace: Boolean = false
    var includeExpendedEnergy: Boolean = false
    var includeHeartRate: Boolean = true
    var includeMetabolicEquivalent: Boolean = false
    var includeElapsedTime: Boolean = true
    var includeRemainingTime: Boolean = false
    var includeForceOnBeltAndPowerOutput: Boolean = true

    // Backward-compatible name used by older UI code.
    var includePower: Boolean
        get() = includeForceOnBeltAndPowerOutput
        set(value) {
            includeForceOnBeltAndPowerOutput = value
        }

    var instantaneousSpeed: Float = 8.0f
    var inclinationPercent: Float = 0.0f
    var instantaneousPower: Int = 190
    var heartRate: Int = 120
    var metabolicEquivalent: Float = 8.0f
    var targetDurationSeconds: Int = 30 * 60

    var elapsedTime: Float
        get() = elapsedTimeSeconds.toFloat()
        set(value) {
            elapsedTimeSeconds = max(0.0, value.toDouble())
        }

    var totalDistance: Int
        get() = totalDistanceMeters.roundToInt().ftmsUInt24()
        set(value) {
            totalDistanceMeters = max(0.0, value.toDouble())
        }

    var averageSpeed: Float
        get() = getAverageSpeedHundredthsKmh() / 100f
        set(value) {
            // Kept for legacy fragment compatibility; average speed is derived internally.
        }

    var instantaneousPace: Float
        get() = getInstantaneousPaceKmpmTenths() / 10f
        set(value) {
            // Kept for legacy fragment compatibility; pace is derived internally.
        }

    var averagePace: Float
        get() = getAveragePaceKmpmTenths() / 10f
        set(value) {
            // Kept for legacy fragment compatibility; pace is derived internally.
        }

    private var elapsedTimeSeconds = 0.0
    private var totalDistanceMeters = 0.0
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
        val elevationDelta = distanceDelta * inclinationPercent.toDouble() / 100.0

        elapsedTimeSeconds += deltaSeconds
        totalDistanceMeters += distanceDelta
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
        positiveElevationMeters = 0.0
        negativeElevationMeters = 0.0
        powerTimeIntegral = 0.0
        totalEnergyKcal = 0.0
    }

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

    fun getInclinationTenthsPercent(): Int {
        return (inclinationPercent * 10f).roundToFtmsSInt16()
    }

    fun getRampAngleTenthsDegree(): Int {
        val angleDegrees = Math.toDegrees(atan(inclinationPercent.toDouble() / 100.0))
        return (angleDegrees * 10.0).roundToFtmsSInt16()
    }

    fun getPositiveElevationGainTenthsMeters(): Int {
        return (positiveElevationMeters * 10.0).roundToFtmsUInt16()
    }

    fun getNegativeElevationGainTenthsMeters(): Int {
        return (negativeElevationMeters * 10.0).roundToFtmsUInt16()
    }

    fun getInstantaneousPaceKmpmTenths(): Int {
        return (instantaneousSpeed.coerceAtLeast(0f).toDouble() / 60.0 * 10.0).roundToFtmsUInt8()
    }

    fun getAveragePaceKmpmTenths(): Int {
        if (elapsedTimeSeconds <= 0.0) {
            return 0
        }
        val averageSpeedKmh = totalDistanceMeters / elapsedTimeSeconds * 3.6
        return (averageSpeedKmh / 60.0 * 10.0).roundToFtmsUInt8()
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

    fun getForceOnBeltNewton(): Int {
        val speedMetersPerSecond = instantaneousSpeed.coerceAtLeast(0f).toDouble() / 3.6
        if (speedMetersPerSecond <= 0.05 || instantaneousPower <= 0) {
            return DATA_NOT_AVAILABLE_SINT16
        }
        return (instantaneousPower / speedMetersPerSecond).roundToFtmsSInt16()
    }

    fun getPowerOutputWatt(): Int {
        return instantaneousPower.ftmsSInt16()
    }

    fun getComputedSummary(): String {
        return "Avg ${getAverageSpeedHundredthsKmh() / 100.0} km/h | " +
            "${getTotalDistanceMeters()} m | " +
            "${getElapsedTimeSeconds()} s | " +
            "${getTotalEnergyKcal()} kcal"
    }

    companion object {
        const val DATA_NOT_AVAILABLE_SINT16 = 0x7FFF
    }
}
