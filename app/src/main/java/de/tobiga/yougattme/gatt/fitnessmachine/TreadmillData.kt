package de.tobiga.yougattme.gatt.fitnessmachine

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import android.util.Log

class TreadmillData {

    private class TreadmillState {
        // Toggle Flags
        var includeAverageSpeed = false
        var includeTotalDistance = true
        var includeInstantaneousPace = false
        var includeAveragePace = false
        var includeHeartRate = false
        var includeSteps = true  // whether to include step-related data (steps calculated)

        // Core simulation values
        var instantaneousSpeed: Float = 8.0f      // km/h (mandatory)
        var averageSpeed: Float = 7.5f              // km/h (calculated)
        var totalDistance: Int = 0                  // meters (calculated)
        var instantaneousPace: Float = 0f           // min/km (calculated)
        var averagePace: Float = 0f                 // min/km (calculated)
        var heartRate: Int = 0                      // BPM (optional)
        var steps: Float = 0f                        // Total step count (calculated)
        var elapsedTime: Float = 0f                 // seconds (calculated, stored as Float for precision)

        // New: instantaneous step rate (steps per minute) from user input.
        var instantaneousStepRate: Int = 0
    }

    private val state = TreadmillState()

    // Toggle flags and their LiveData (if needed)
    val includeAverageSpeedLiveData: LiveData<Boolean> = object : LiveData<Boolean>() {
        override fun getValue(): Boolean = state.includeAverageSpeed
    }
    var includeAverageSpeed: Boolean
        get() = state.includeAverageSpeed
        set(value) { state.includeAverageSpeed = value }

    val includeTotalDistanceLiveData: LiveData<Boolean> = object : LiveData<Boolean>() {
        override fun getValue(): Boolean = state.includeTotalDistance
    }
    var includeTotalDistance: Boolean
        get() = state.includeTotalDistance
        set(value) { state.includeTotalDistance = value }

    val includeInstantaneousPaceLiveData: LiveData<Boolean> = object : LiveData<Boolean>() {
        override fun getValue(): Boolean = state.includeInstantaneousPace
    }
    var includeInstantaneousPace: Boolean
        get() = state.includeInstantaneousPace
        set(value) { state.includeInstantaneousPace = value }

    val includeAveragePaceLiveData: LiveData<Boolean> = object : LiveData<Boolean>() {
        override fun getValue(): Boolean = state.includeAveragePace
    }
    var includeAveragePace: Boolean
        get() = state.includeAveragePace
        set(value) { state.includeAveragePace = value }

    val includeHeartRateLiveData: LiveData<Boolean> = object : LiveData<Boolean>() {
        override fun getValue(): Boolean = state.includeHeartRate
    }
    var includeHeartRate: Boolean
        get() = state.includeHeartRate
        set(value) { state.includeHeartRate = value }

    val includeStepsLiveData: LiveData<Boolean> = object : LiveData<Boolean>() {
        override fun getValue(): Boolean = state.includeSteps
    }
    var includeSteps: Boolean
        get() = state.includeSteps
        set(value) { state.includeSteps = value }

    // Core data LiveData (if needed)
    val instantaneousSpeedLiveData: LiveData<Float> = object : LiveData<Float>() {
        override fun getValue(): Float = state.instantaneousSpeed
    }
    var instantaneousSpeed: Float
        get() = state.instantaneousSpeed
        set(value) {
            state.instantaneousSpeed = value
        }

    val averageSpeedLiveData: LiveData<Float> = object : LiveData<Float>() {
        override fun getValue(): Float = state.averageSpeed
    }
    var averageSpeed: Float
        get() = state.averageSpeed
        set(value) { state.averageSpeed = value }

    val totalDistanceLiveData: LiveData<Int> = object : LiveData<Int>() {
        override fun getValue(): Int = state.totalDistance
    }
    var totalDistance: Int
        get() = state.totalDistance
        set(value) { state.totalDistance = value }

    val instantaneousPaceLiveData: LiveData<Float> = object : LiveData<Float>() {
        override fun getValue(): Float = state.instantaneousPace
    }
    var instantaneousPace: Float
        get() = state.instantaneousPace
        set(value) { state.instantaneousPace = value }

    val averagePaceLiveData: LiveData<Float> = object : LiveData<Float>() {
        override fun getValue(): Float = state.averagePace
    }
    var averagePace: Float
        get() = state.averagePace
        set(value) { state.averagePace = value }

    val heartRateLiveData: LiveData<Int> = object : LiveData<Int>() {
        override fun getValue(): Int = state.heartRate
    }
    var heartRate: Int
        get() = state.heartRate
        set(value) { state.heartRate = value }

    val stepsLiveData: LiveData<Float> = object : LiveData<Float>() {
        override fun getValue(): Float = state.steps
    }
    var steps: Float
        get() = state.steps
        set(value) { state.steps = value }

    val elapsedTimeLiveData: LiveData<Float> = object : LiveData<Float>() {
        override fun getValue(): Float = state.elapsedTime
    }
    var elapsedTime: Float
        get() = state.elapsedTime
        set(value) { state.elapsedTime = value }

    // New: Instantaneous Step Rate (steps per minute)
    var instantaneousStepRate: Int
        get() = state.instantaneousStepRate
        set(value) { state.instantaneousStepRate = value }

    /**
     * Advances the simulation by the given number of milliseconds.
     * Updates elapsed time (with full precision), total distance, pace, and calculates steps based on the instantaneousStepRate.
     */
    fun advanceTime(milliseconds: Long) {
        val deltaSeconds = milliseconds / 1000f

        // Increase elapsed time without converting to Int; store full precision.
        elapsedTime += deltaSeconds

        // Update total distance.
        // instantaneousSpeed is in km/h; convert to m/s (divide by 3.6) then multiply by deltaSeconds.
        val distanceIncrement = (instantaneousSpeed / 3.6f * deltaSeconds).toInt()
        totalDistance += distanceIncrement

        // Calculate instantaneous pace (min/km) if speed > 0.
        instantaneousPace = if (instantaneousSpeed > 0) 60f / instantaneousSpeed else 0f

        // Calculate average speed from total distance and elapsed time.
        val elapsedHours = elapsedTime / 3600f
        averageSpeed = if (elapsedHours > 0) totalDistance / 1000f / elapsedHours else instantaneousSpeed

        // Calculate average pace (min/km) from average speed.
        averagePace = if (averageSpeed > 0) 60f / averageSpeed else 0f

        // Calculate steps if enabled: use the instantaneousStepRate (steps per minute)
        if (includeSteps) {
            val deltaMinutes = milliseconds / 60000f
            steps += (instantaneousStepRate * deltaMinutes)
        }

    }
}
