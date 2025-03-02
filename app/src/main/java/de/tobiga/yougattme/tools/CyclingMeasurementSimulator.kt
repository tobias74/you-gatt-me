package de.tobiga.yougattme.tools

import android.util.Log
import kotlin.math.floor

class CyclingMeasurementSimulator {

    private val WHEEL_CIRCUMFERENCE = 2.07f // in meters
    private val MAX_WHEEL_REVOLUTIONS = 4294967295L // 0xFFFFFFFF (32-bit max value)
    private val MAX_CRANK_REVOLUTIONS = 65535L // 0xFFFF (16-bit max value)

    // Wheel revolution data
    private var cumulativeWheelRevolutions: Double = 0.0
    private var wheelPosition: Double = 0.0 // 0.0 to 1.0 for one revolution
    private var lastWheelEventTimestamp: Long = 0L

    // Crank revolution data
    private var cumulativeCrankRevolutions: Double = 0.0
    private var crankPosition: Double = 0.0 // 0.0 to 1.0 for one revolution
    private var lastCrankEventTimestamp: Long = 0L

    private data class SimulationResult(
        val newPosition: Double,
        val cumulativeRevolutions: Double,
        val newTimestamp: Long,
        val newRevolutions: Int
    )

    private fun simulateRotation(
        revolutionsPerSecond: Double,
        currentPosition: Double,
        cumulativeRevolutions: Double,
        lastEventTimestamp: Long,
        maxRevolutions: Long,
        currentTimeMillis: Long,
        durationMillis: Long = -1,
        componentName: String
    ): SimulationResult {
        // Initialize timestamp if this is the first call
        if (lastEventTimestamp == 0L) {
            return SimulationResult(
                newPosition = currentPosition,
                cumulativeRevolutions = cumulativeRevolutions,
                newTimestamp = currentTimeMillis,
                newRevolutions = 0
            )
        }

        // Handle zero velocity/cadence case
        if (revolutionsPerSecond <= 0.0) {
            // Don't update timestamps or positions when not moving
            return SimulationResult(
                newPosition = currentPosition,
                cumulativeRevolutions = cumulativeRevolutions,
                newTimestamp = lastEventTimestamp,
                newRevolutions = 0
            )
        }

        // Determine simulation duration
        val actualDurationMillis = if (durationMillis > 0) {
            durationMillis
        } else {
            currentTimeMillis - lastEventTimestamp
        }

        // Calculate rotation progress during this period
        val rotationProgress = revolutionsPerSecond * (actualDurationMillis / 1000.0)

        // Add rotation progress to current position
        val newTotalPosition = currentPosition + rotationProgress

        // Calculate full revolutions completed
        val fullRevolutions = floor(newTotalPosition).toInt()

        // Calculate new position (just the fractional part)
        val newPosition = newTotalPosition - fullRevolutions

        // Update cumulative revolutions
        var newCumulativeRevolutions = cumulativeRevolutions + fullRevolutions

        // Handle overflow according to CSC specification
        if (newCumulativeRevolutions > maxRevolutions) {
            newCumulativeRevolutions -= (maxRevolutions + 1)
        }

        // Calculate the timestamp for when the revolutions complete
        val newTimestamp = if (fullRevolutions > 0) {
            // If revolutions occurred, calculate when they would have finished
            val timeForRevolutions = (fullRevolutions / revolutionsPerSecond) * 1000.0
            lastEventTimestamp + timeForRevolutions.toLong()
        } else {
            // If no revolutions occurred, keep the last timestamp
            lastEventTimestamp
        }

        return SimulationResult(
            newPosition = newPosition,
            cumulativeRevolutions = newCumulativeRevolutions,
            newTimestamp = newTimestamp,
            newRevolutions = fullRevolutions
        )
    }

    fun simulateCycling(velocityMs: Double, cadenceRpm: Double, durationMillis: Long = -1, currentTimeMillis: Long): Pair<Int, Int> {
        val wheelRevs = simulateWheel(velocityMs, durationMillis, currentTimeMillis)
        val crankRevs = simulateCrank(cadenceRpm, durationMillis, currentTimeMillis)
        return Pair(wheelRevs, crankRevs)
    }

    fun simulateWheel(velocity: Double, durationMillis: Long = -1, currentTimeMillis: Long = System.currentTimeMillis()): Int {
        // Convert velocity to wheel revolutions per second
        val wheelRevolutionsPerSecond = velocity / WHEEL_CIRCUMFERENCE

        val result = simulateRotation(
            revolutionsPerSecond = wheelRevolutionsPerSecond,
            currentPosition = wheelPosition,
            cumulativeRevolutions = cumulativeWheelRevolutions,
            lastEventTimestamp = lastWheelEventTimestamp,
            maxRevolutions = MAX_WHEEL_REVOLUTIONS,
            currentTimeMillis = currentTimeMillis,
            durationMillis = durationMillis,
            componentName = "wheel"
        )

        // Update state
        wheelPosition = result.newPosition
        cumulativeWheelRevolutions = result.cumulativeRevolutions
        lastWheelEventTimestamp = result.newTimestamp

        return result.newRevolutions
    }

    fun simulateCrank(cadenceRpm: Double, durationMillis: Long = -1, currentTimeMillis: Long = System.currentTimeMillis()): Int {
        // Convert RPM to revolutions per second
        val crankRevolutionsPerSecond = cadenceRpm / 60.0

        val result = simulateRotation(
            revolutionsPerSecond = crankRevolutionsPerSecond,
            currentPosition = crankPosition,
            cumulativeRevolutions = cumulativeCrankRevolutions,
            lastEventTimestamp = lastCrankEventTimestamp,
            maxRevolutions = MAX_CRANK_REVOLUTIONS,
            currentTimeMillis = currentTimeMillis,
            durationMillis = durationMillis,
            componentName = "crank"
        )

        // Update state
        crankPosition = result.newPosition
        cumulativeCrankRevolutions = result.cumulativeRevolutions
        lastCrankEventTimestamp = result.newTimestamp

        return result.newRevolutions
    }

    fun simulateWheelKmh(velocityKmh: Double, durationMillis: Long = -1, currentTimeMillis: Long = System.currentTimeMillis()): Int {
        // Convert km/h to m/s
        val velocityMs = velocityKmh / 3.6
        return simulateWheel(velocityMs, durationMillis, currentTimeMillis)
    }

    fun simulateCyclingKmh(velocityKmh: Double, cadenceRpm: Double, durationMillis: Long = -1, currentTimeMillis: Long = System.currentTimeMillis()): Pair<Int, Int> {
        val velocityMs = velocityKmh / 3.6
        return simulateCycling(velocityMs, cadenceRpm, durationMillis, currentTimeMillis)
    }

    // Getters for wheel revolution data (for CSC service)
    fun getCumulativeWheelRevolutions(): Int {
        return cumulativeWheelRevolutions.toInt()
    }

    fun getLastWheelEventTimestamp(): Long {
        return lastWheelEventTimestamp
    }

    // Getters for crank revolution data (for CSC service)
    fun getCumulativeCrankRevolutions(): Int {
        return cumulativeCrankRevolutions.toInt()
    }

    fun getLastCrankEventTimestamp(): Long {
        return lastCrankEventTimestamp
    }

    fun getLastCrankEventTimestampBle(): Int {
        // Convert milliseconds to 1/1024 seconds (wrapping at 65535)
        return ((lastCrankEventTimestamp * 1024) / 1000).toInt() % 65536
    }

    fun getLastWheelEventTimestampBle(): Int {
        // Convert milliseconds to 1/1024 seconds (wrapping at 65535)
        return ((lastWheelEventTimestamp * 1024) / 1000).toInt() % 65536
    }

    fun reset() {
        cumulativeWheelRevolutions = 0.0
        wheelPosition = 0.0
        lastWheelEventTimestamp = 0L

        cumulativeCrankRevolutions = 0.0
        crankPosition = 0.0
        lastCrankEventTimestamp = 0L
    }

    fun setWheelCircumference(fl: Float) {

    }
}