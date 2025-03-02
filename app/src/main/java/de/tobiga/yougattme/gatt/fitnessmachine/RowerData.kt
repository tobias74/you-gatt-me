package de.tobiga.yougattme.gatt.fitnessmachine

import android.util.Log

class RowerData {

    private class RowerState {
        // Flags: Only Heart Rate, Pace, and Power are optional
        var includeInstantaneousPace = true
        var includeInstantaneousPower = true
        var includeHeartRate = true

        // Core simulation values
        var strokeRate: Int = 30              // Strokes per minute (SPM)
        var strokeCount: Float = 0f              // Total strokes (cumulative)
        var instantaneousPace: Int = 180      // sec/500m (resolution: 1 sec)
        var instantaneousPower: Int = 150     // Watts (instant power)
        var heartRate: Int = 120              // BPM (optional)
    }

    private val state = RowerState()

    // Toggle Flags
    var includeInstantaneousPace: Boolean
        get() = state.includeInstantaneousPace
        set(value) { state.includeInstantaneousPace = value }

    var includeInstantaneousPower: Boolean
        get() = state.includeInstantaneousPower
        set(value) { state.includeInstantaneousPower = value }

    var includeHeartRate: Boolean
        get() = state.includeHeartRate
        set(value) { state.includeHeartRate = value }

    // Core Data
    var strokeRate: Int
        get() = state.strokeRate
        set(value) { state.strokeRate = value }

    var strokeCount: Float
        get() = state.strokeCount
        set(value) { state.strokeCount = value }

    var instantaneousPace: Int
        get() = state.instantaneousPace
        set(value) { state.instantaneousPace = value }

    var instantaneousPower: Int
        get() = state.instantaneousPower
        set(value) { state.instantaneousPower = value }

    var heartRate: Int
        get() = state.heartRate
        set(value) { state.heartRate = value }

    /**
     * Advances the simulation by the given number of milliseconds.
     * - Updates stroke count based on stroke rate.
     */
    fun advanceTime(milliseconds: Long) {

        val deltaSeconds = milliseconds / 1000f

        // Calculate new strokes in this time period
        val deltaStrokes = strokeRate * (deltaSeconds / 60f)
        state.strokeCount += deltaStrokes

    }
}
