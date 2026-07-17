package de.tobiga.yougattme.gatt.fitnessmachine

import kotlin.math.max
import kotlin.math.roundToInt

class IndoorBikeData {

    var includeAverageSpeed: Boolean = false
    var includeInstantaneousCadence: Boolean = true
    var includeAverageCadence: Boolean = false
    var includeTotalDistance: Boolean = true
    var includeResistanceLevel: Boolean = false
    var includeInstantaneousPower: Boolean = true
    var includeAveragePower: Boolean = false
    var includeExpendedEnergy: Boolean = false
    var includeHeartRate: Boolean = true
    var includeMetabolicEquivalent: Boolean = false
    var includeElapsedTime: Boolean = true
    var includeRemainingTime: Boolean = false

    var instantaneousSpeed: Float = 25.0f
    var instantaneousCadence: Int = 80
    var resistanceLevel: Int = 20
    var instantaneousPower: Int = 180
    var heartRate: Int = 120
    var metabolicEquivalent: Float = 8.0f
    var targetDurationSeconds: Int = 30 * 60

    var elapsedTime: Float
        get() = elapsedTimeSeconds.toFloat()
        set(value) {
            elapsedTimeSeconds = max(0.0, value.toDouble())
        }

    var totalDistance: Float
        get() = totalDistanceMeters.toFloat()
        set(value) {
            totalDistanceMeters = max(0.0, value.toDouble())
        }

    private var elapsedTimeSeconds = 0.0
    private var totalDistanceMeters = 0.0
    private var cadenceTimeIntegral = 0.0
    private var powerTimeIntegral = 0.0
    private var totalEnergyKcal = 0.0

    fun advanceTime(milliseconds: Long, workoutRunning: Boolean) {
        if (!workoutRunning || milliseconds <= 0) {
            return
        }

        val deltaSeconds = milliseconds / 1000.0
        val speedMetersPerSecond = instantaneousSpeed.toDouble() / 3.6

        elapsedTimeSeconds += deltaSeconds
        totalDistanceMeters += speedMetersPerSecond * deltaSeconds
        cadenceTimeIntegral += instantaneousCadence * deltaSeconds
        powerTimeIntegral += instantaneousPower * deltaSeconds
        totalEnergyKcal += max(0.0, instantaneousPower.toDouble()) * deltaSeconds / JOULES_PER_KILOCALORIE
    }

    fun resetSession() {
        elapsedTimeSeconds = 0.0
        totalDistanceMeters = 0.0
        cadenceTimeIntegral = 0.0
        powerTimeIntegral = 0.0
        totalEnergyKcal = 0.0
    }

    fun getInstantaneousSpeedHundredthsKmh(): Int {
        return (instantaneousSpeed * 100f).roundToInt().coerceIn(0, UINT16_MAX)
    }

    fun getAverageSpeedHundredthsKmh(): Int {
        if (elapsedTimeSeconds <= 0.0) {
            return 0
        }
        val averageSpeedKmh = totalDistanceMeters / elapsedTimeSeconds * 3.6
        return (averageSpeedKmh * 100.0).roundToInt().coerceIn(0, UINT16_MAX)
    }

    fun getInstantaneousCadenceHalfRpm(): Int {
        return (instantaneousCadence * 2).coerceIn(0, UINT16_MAX)
    }

    fun getAverageCadenceHalfRpm(): Int {
        if (elapsedTimeSeconds <= 0.0) {
            return 0
        }
        return (cadenceTimeIntegral / elapsedTimeSeconds * 2.0).roundToInt().coerceIn(0, UINT16_MAX)
    }

    fun getTotalDistanceInt(): Int {
        return totalDistanceMeters.roundToInt().coerceIn(0, UINT24_MAX)
    }

    fun getResistanceLevelRaw(): Int {
        return (resistanceLevel * 10f).roundToInt().coerceIn(SINT16_MIN, SINT16_MAX)
    }

    fun getInstantaneousPowerRaw(): Int {
        return instantaneousPower.coerceIn(SINT16_MIN, SINT16_MAX)
    }

    fun getAveragePowerRaw(): Int {
        if (elapsedTimeSeconds <= 0.0) {
            return 0
        }
        return (powerTimeIntegral / elapsedTimeSeconds).roundToInt().coerceIn(SINT16_MIN, SINT16_MAX)
    }

    fun getTotalEnergyKcal(): Int {
        return totalEnergyKcal.roundToInt().coerceIn(0, UINT16_MAX - 1)
    }

    fun getEnergyPerHourKcal(): Int {
        if (elapsedTimeSeconds <= 0.0) {
            return 0
        }
        return (totalEnergyKcal * 3600.0 / elapsedTimeSeconds).roundToInt().coerceIn(0, UINT16_MAX - 1)
    }

    fun getEnergyPerMinuteKcal(): Int {
        if (elapsedTimeSeconds <= 0.0) {
            return 0
        }
        return (totalEnergyKcal * 60.0 / elapsedTimeSeconds).roundToInt().coerceIn(0, UINT8_MAX - 1)
    }

    fun getHeartRateBpm(): Int {
        return heartRate.coerceIn(0, UINT8_MAX)
    }

    fun getMetabolicEquivalentTenths(): Int {
        return (metabolicEquivalent * 10f).roundToInt().coerceIn(0, UINT8_MAX)
    }

    fun getElapsedTimeInt(): Int {
        return elapsedTimeSeconds.roundToInt().coerceIn(0, UINT16_MAX)
    }

    fun getRemainingTimeInt(): Int {
        return (targetDurationSeconds - elapsedTimeSeconds).roundToInt().coerceIn(0, UINT16_MAX)
    }

    fun getComputedSummary(): String {
        return "Avg ${getAverageSpeedHundredthsKmh() / 100.0} km/h | " +
            "${getTotalDistanceInt()} m | " +
            "${getElapsedTimeInt()} s | " +
            "${getTotalEnergyKcal()} kcal"
    }

    companion object {
        private const val UINT8_MAX = 0xFF
        private const val UINT16_MAX = 0xFFFF
        private const val UINT24_MAX = 0xFFFFFF
        private const val SINT16_MIN = -0x8000
        private const val SINT16_MAX = 0x7FFF
        private const val JOULES_PER_KILOCALORIE = 4184.0
    }
}
