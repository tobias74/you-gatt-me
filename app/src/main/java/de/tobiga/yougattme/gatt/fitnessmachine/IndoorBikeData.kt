package de.tobiga.yougattme.gatt.fitnessmachine

import android.util.Log

class IndoorBikeData {

    private class IndoorBikeState {
        // Flags: Only Heart Rate is optional
        var includeHeartRate = false

        // Core simulation values (direct input)
        var instantaneousSpeed: Float = 25.0f   // km/h (stored as Float for precision)
        var instantaneousCadence: Int = 80      // rpm (stored as Int)
        var instantaneousPower: Int = 65        // watts (stored as Int)
        var heartRate: Int = 120                // BPM (optional)

        // Calculated values (stored as Float for precision)
        var elapsedTime: Float = 0f             // seconds (Float to preserve precision)
        var totalDistance: Float = 0f           // meters (Float to preserve precision)
    }

    private val state = IndoorBikeState()

    // Toggle Flag
    var includeHeartRate: Boolean
        get() = state.includeHeartRate
        set(value) { state.includeHeartRate = value }

    // Core Data (Direct Inputs)
    var instantaneousSpeed: Float
        get() = state.instantaneousSpeed
        set(value) { state.instantaneousSpeed = value }

    var instantaneousCadence: Int
        get() = state.instantaneousCadence
        set(value) { state.instantaneousCadence = value }

    var instantaneousPower: Int
        get() = state.instantaneousPower
        set(value) { state.instantaneousPower = value }

    var heartRate: Int
        get() = state.heartRate
        set(value) { state.heartRate = value }

    // Calculated Data (Stored as Float for Precision)
    var elapsedTime: Float
        get() = state.elapsedTime
        set(value) { state.elapsedTime = value }

    var totalDistance: Float
        get() = state.totalDistance
        set(value) { state.totalDistance = value }

    /**
     * Advances the simulation by the given number of milliseconds.
     * - Updates elapsed time (preserving precision).
     * - Updates total distance based on speed.
     */
    fun advanceTime(milliseconds: Long) {

        val deltaSeconds = milliseconds / 1000f

        // Keep elapsed time as a float for maximum precision
        elapsedTime += deltaSeconds

        // Compute total distance based on speed (km/h → m/s) and keep it as Float
        val speedMetersPerSecond = instantaneousSpeed / 3.6f
        totalDistance += speedMetersPerSecond * deltaSeconds

    }

    /**
     * Converts elapsed time and total distance to integer format before sending.
     */
    fun getElapsedTimeInt(): Int {
        return elapsedTime.toInt()
    }

    fun getTotalDistanceInt(): Int {
        return totalDistance.toInt()
    }
}
