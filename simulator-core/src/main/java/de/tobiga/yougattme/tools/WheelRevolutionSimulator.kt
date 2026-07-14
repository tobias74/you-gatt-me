package de.tobiga.yougattme.tools

import android.util.Log
import kotlin.math.floor

/**
 * Simulates wheel revolutions based on velocity and duration.
 * Tracks cumulative wheel revolutions and timestamps for cycling metrics.
 */
class WheelRevolutionSimulator {

    private val WHEEL_CIRCUMFERENCE = 2.07f // in meters
    private val MAX_WHEEL_REVOLUTIONS = 4294967295L // 0xFFFFFFFF max value for characteristic

    private var cumulativeWheelRevolutions: Double = 0.0
    private var leftOverMillis: Double = 0.0
    private var lastWheelEventTimestamp: Long = 0L

    /**
     * Simulates wheel revolutions for a specified velocity and duration.
     *
     * @param velocity Velocity in meters per second
     * @param durationMillis Duration to simulate (in milliseconds), defaults to elapsed time since last call
     * @return Number of new wheel revolutions generated in this simulation
     */
    fun simulate(velocity: Double, durationMillis: Long): Int {
        val currentTimeMillis = System.currentTimeMillis()

        // Initialize timestamp if this is the first call
        if (lastWheelEventTimestamp == 0L) {
            lastWheelEventTimestamp = currentTimeMillis
            return 0
        }

        // Handle zero velocity case
        if (velocity <= 0.0) {
            // Don't update lastWheelEventTimestamp as no wheel event actually occurred
            // Don't reset leftOverMillis to maintain continuity when motion resumes
            return 0
        }

        // Determine simulation duration
        val actualDurationMillis = if (durationMillis > 0) {
            durationMillis
        } else {
            currentTimeMillis - lastWheelEventTimestamp
        }

        // Calculate total available time including leftover from previous simulation
        val totalAvailableMillis = leftOverMillis + actualDurationMillis

        // Calculate wheel revolutions
        val metersPerSecond = velocity // Already in m/s
        val wheelRevolutionsPerSecond = metersPerSecond / WHEEL_CIRCUMFERENCE
        val totalWheelRevolutions = wheelRevolutionsPerSecond * (totalAvailableMillis / 1000.0)

        // Split into full and partial revolutions
        val fullWheelRevolutions = floor(totalWheelRevolutions).toInt()
        val partialRevolution = totalWheelRevolutions - fullWheelRevolutions

        // Calculate time used for full revolutions
        val timeForFullRevolutions = (fullWheelRevolutions / wheelRevolutionsPerSecond) * 1000.0

        // Calculate leftover time for partial revolution
        leftOverMillis = totalAvailableMillis - timeForFullRevolutions

        // Update cumulative wheel revolutions
        cumulativeWheelRevolutions += fullWheelRevolutions

        // Handle overflow
        if (cumulativeWheelRevolutions > MAX_WHEEL_REVOLUTIONS) {
            cumulativeWheelRevolutions -= (MAX_WHEEL_REVOLUTIONS + 1)
        }

        // Update timestamp based on when the full revolutions would have completed
        lastWheelEventTimestamp = if (durationMillis > 0) {
            // For explicit duration, advance by full duration
            lastWheelEventTimestamp + actualDurationMillis
        } else {
            // For real-time simulation, use the time for full revolutions
            lastWheelEventTimestamp + timeForFullRevolutions.toLong()
        }

        return fullWheelRevolutions
    }

    /**
     * Alternative method that takes velocity in km/h for convenience
     */
    fun simulateKmh(velocityKmh: Double, durationMillis: Long = -1): Int {
        // Convert km/h to m/s
        val velocityMs = velocityKmh / 3.6
        return simulate(velocityMs, durationMillis)
    }

    /**
     * Returns the current cumulative wheel revolutions
     */
    fun getCumulativeWheelRevolutions(): Int {
        return cumulativeWheelRevolutions.toInt()
    }

    /**
     * Returns the last wheel event timestamp
     */
    fun getLastWheelEventTimestamp(): Long {
        return lastWheelEventTimestamp
    }

    /**
     * Resets the simulator to initial state
     */
    fun reset() {
        cumulativeWheelRevolutions = 0.0
        leftOverMillis = 0.0
        lastWheelEventTimestamp = 0L
    }
}