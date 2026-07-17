package de.tobiga.yougattme.gatt.fitnessmachine

import kotlin.math.max
import kotlin.math.roundToInt

class RowerData {

    var includeAverageStrokeRate: Boolean = false
    var includeTotalDistance: Boolean = true
    var includeInstantaneousPace: Boolean = true
    var includeAveragePace: Boolean = false
    var includeInstantaneousPower: Boolean = true
    var includeAveragePower: Boolean = false
    var includeResistanceLevel: Boolean = false
    var includeExpendedEnergy: Boolean = false
    var includeHeartRate: Boolean = true
    var includeMetabolicEquivalent: Boolean = false
    var includeElapsedTime: Boolean = true
    var includeRemainingTime: Boolean = false

    var strokeRate: Int = 30
    var resistanceLevel: Int = 20
    var instantaneousPower: Int = 150
    var heartRate: Int = 120
    var metabolicEquivalent: Float = 8.0f
    var targetDurationSeconds: Int = 30 * 60

    var strokeCount: Float
        get() = strokeCountAccumulator.toFloat()
        set(value) {
            strokeCountAccumulator = max(0.0, value.toDouble())
        }

    var instantaneousPace: Int
        get() = getInstantaneousPaceSeconds()
        set(value) {
            // Kept for legacy fragment compatibility; pace is derived internally.
        }

    private var elapsedTimeSeconds = 0.0
    private var strokeCountAccumulator = 0.0
    private var totalDistanceMeters = 0.0
    private var strokeRateTimeIntegral = 0.0
    private var powerTimeIntegral = 0.0
    private var totalEnergyKcal = 0.0

    fun advanceTime(milliseconds: Long, workoutRunning: Boolean) {
        if (!workoutRunning || milliseconds <= 0) {
            return
        }

        val deltaSeconds = milliseconds / 1000.0
        val strokesDelta = strokeRate.coerceAtLeast(0) * deltaSeconds / 60.0
        val distanceDelta = strokesDelta * METERS_PER_STROKE

        elapsedTimeSeconds += deltaSeconds
        strokeCountAccumulator += strokesDelta
        totalDistanceMeters += distanceDelta
        strokeRateTimeIntegral += strokeRate * deltaSeconds
        powerTimeIntegral += instantaneousPower * deltaSeconds
        totalEnergyKcal += max(0.0, instantaneousPower.toDouble()) * deltaSeconds / FTMS_JOULES_PER_KILOCALORIE
    }

    fun resetSession() {
        elapsedTimeSeconds = 0.0
        strokeCountAccumulator = 0.0
        totalDistanceMeters = 0.0
        strokeRateTimeIntegral = 0.0
        powerTimeIntegral = 0.0
        totalEnergyKcal = 0.0
    }

    fun getStrokeRateHalfSpm(): Int {
        return (strokeRate.coerceAtLeast(0) * 2).ftmsUInt8()
    }

    fun getStrokeCountTotal(): Int {
        return strokeCountAccumulator.roundToFtmsUInt16()
    }

    fun getAverageStrokeRateHalfSpm(): Int {
        if (elapsedTimeSeconds <= 0.0) {
            return 0
        }
        return (strokeRateTimeIntegral / elapsedTimeSeconds * 2.0).roundToFtmsUInt8()
    }

    fun getTotalDistanceMeters(): Int {
        return totalDistanceMeters.roundToFtmsUInt24()
    }

    fun getInstantaneousPaceSeconds(): Int {
        val metersPerSecond = strokeRate.coerceAtLeast(0) * METERS_PER_STROKE / 60.0
        if (metersPerSecond <= 0.0) {
            return 0
        }
        return (500.0 / metersPerSecond).roundToFtmsUInt16()
    }

    fun getAveragePaceSeconds(): Int {
        if (elapsedTimeSeconds <= 0.0 || totalDistanceMeters <= 0.0) {
            return 0
        }
        return (elapsedTimeSeconds / totalDistanceMeters * 500.0).roundToFtmsUInt16()
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

    fun getResistanceLevelRaw(): Int {
        return (resistanceLevel * 10).ftmsSInt16()
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
        return "${getStrokeCountTotal()} strokes | " +
            "${getTotalDistanceMeters()} m | " +
            "${getInstantaneousPaceSeconds()} s/500m | " +
            "${getElapsedTimeSeconds()} s"
    }

    companion object {
        private const val METERS_PER_STROKE = 7.0
    }
}
