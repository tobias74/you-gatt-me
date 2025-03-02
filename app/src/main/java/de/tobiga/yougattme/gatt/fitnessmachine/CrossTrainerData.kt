package de.tobiga.yougattme.gatt.fitnessmachine

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import android.util.Log

class CrossTrainerData {

    private class CrossTrainerState {
        // Feature flag controls
        var includeInstantaneousPower = true
        var includeHeartRate = true
        var includeTotalDistance = true
        var includeElapsedTime = true
        // Steps are always included in this minimal implementation

        // Core data values
        var instantaneousSpeed: Float = 10.0f
        var instantaneousPower: Int = 240
        var heartRate: Int = 120
        var totalDistance: Int = 0
        var elapsedTime: Float = 0f  // in seconds

        // Step-related data
        var instantaneousStepsPerMinute: Int = 0  // User provided current cadence
        var averageStepsPerMinute: Int = 0          // Calculated over the session
        var totalStepCount: Int = 0                 // Cumulative step count
    }

    private val state = CrossTrainerState()

    // --- Flag LiveData ---

    val includeInstantaneousPowerLiveData = object : LiveData<Boolean>() {
        override fun getValue(): Boolean = state.includeInstantaneousPower
    }
    val includeHeartRateLiveData = object : LiveData<Boolean>() {
        override fun getValue(): Boolean = state.includeHeartRate
    }
    val includeTotalDistanceLiveData = object : LiveData<Boolean>() {
        override fun getValue(): Boolean = state.includeTotalDistance
    }
    val includeElapsedTimeLiveData = object : LiveData<Boolean>() {
        override fun getValue(): Boolean = state.includeElapsedTime
    }

    // --- Data LiveData ---

    val instantaneousSpeedLiveData = object : LiveData<Float>() {
        override fun getValue(): Float = state.instantaneousSpeed
    }
    val instantaneousPowerLiveData = object : LiveData<Int>() {
        override fun getValue(): Int = state.instantaneousPower
    }
    val heartRateLiveData = object : LiveData<Int>() {
        override fun getValue(): Int = state.heartRate
    }
    val totalDistanceLiveData = object : LiveData<Int>() {
        override fun getValue(): Int = state.totalDistance
    }
    val elapsedTimeLiveData = object : LiveData<Float>() {
        override fun getValue(): Float = state.elapsedTime
    }

    // New Step Data LiveData
    val instantaneousStepsPerMinuteLiveData = object : LiveData<Int>() {
        override fun getValue(): Int = state.instantaneousStepsPerMinute
    }
    val averageStepsPerMinuteLiveData = object : LiveData<Int>() {
        override fun getValue(): Int = state.averageStepsPerMinute
    }
    val totalStepCountLiveData = object : LiveData<Int>() {
        override fun getValue(): Int = state.totalStepCount
    }

    // --- Flag Getters and Setters ---

    var includeInstantaneousPower: Boolean
        get() = state.includeInstantaneousPower
        set(value) {
            state.includeInstantaneousPower = value
            includeInstantaneousPowerLiveData.notifyObservers()
        }

    var includeHeartRate: Boolean
        get() = state.includeHeartRate
        set(value) {
            state.includeHeartRate = value
            includeHeartRateLiveData.notifyObservers()
        }

    var includeTotalDistance: Boolean
        get() = state.includeTotalDistance
        set(value) {
            state.includeTotalDistance = value
            includeTotalDistanceLiveData.notifyObservers()
        }

    var includeElapsedTime: Boolean
        get() = state.includeElapsedTime
        set(value) {
            state.includeElapsedTime = value
            includeElapsedTimeLiveData.notifyObservers()
        }

    // --- Data Getters and Setters ---

    var instantaneousSpeed: Float
        get() = state.instantaneousSpeed
        set(value) {
            state.instantaneousSpeed = value
            instantaneousSpeedLiveData.notifyObservers()
        }

    var instantaneousPower: Int
        get() = state.instantaneousPower
        set(value) {
            state.instantaneousPower = value
            instantaneousPowerLiveData.notifyObservers()
        }

    var heartRate: Int
        get() = state.heartRate
        set(value) {
            state.heartRate = value
            heartRateLiveData.notifyObservers()
        }

    val totalDistance: Int
        get() = state.totalDistance

    val elapsedTime: Float
        get() = state.elapsedTime

    // New Step Data Getters and Setters

    var instantaneousStepsPerMinute: Int
        get() = state.instantaneousStepsPerMinute
        set(value) {
            state.instantaneousStepsPerMinute = value
            instantaneousStepsPerMinuteLiveData.notifyObservers()
        }

    val averageStepsPerMinute: Int
        get() = state.averageStepsPerMinute

    val totalStepCount: Int
        get() = state.totalStepCount

    // --- Post methods for compatibility ---

    fun postIncludeInstantaneousPower(value: Boolean) { includeInstantaneousPower = value }
    fun postIncludeHeartRate(value: Boolean) { includeHeartRate = value }
    fun postIncludeTotalDistance(value: Boolean) { includeTotalDistance = value }
    fun postIncludeElapsedTime(value: Boolean) { includeElapsedTime = value }

    fun postInstantaneousSpeed(value: Float) { instantaneousSpeed = value }
    fun postInstantaneousPower(value: Int) { instantaneousPower = value }
    fun postHeartRate(value: Int) { heartRate = value }
    fun postInstantaneousStepsPerMinute(value: Int) { instantaneousStepsPerMinute = value }

    /**
     * Advances the simulation by the given number of milliseconds.
     * Updates elapsed time, distance, and step-related data.
     *
     * @param milliseconds The time to advance, in milliseconds.
     */
    fun advanceTime(milliseconds: Long) {
        val deltaSeconds = milliseconds / 1000f
        state.elapsedTime += deltaSeconds

        // Other calculations (using the precise elapsed time)
        val distanceIncrement = (state.instantaneousSpeed * deltaSeconds / 3.6).toInt()
        state.totalDistance += distanceIncrement

        // Steps calculation remains the same (using milliseconds converted to minutes)
        val deltaMinutes = milliseconds / 60000f
        val stepsAdded = (state.instantaneousStepsPerMinute * deltaMinutes).toInt()
        state.totalStepCount += stepsAdded

        // Recalculate average steps per minute based on the precise elapsed time.
        state.averageStepsPerMinute = if (state.elapsedTime > 0) {
            (state.totalStepCount * 60 / state.elapsedTime).toInt()
        } else {
            state.instantaneousStepsPerMinute
        }

        // Notify observers of changed values
        elapsedTimeLiveData.notifyObservers()
        totalDistanceLiveData.notifyObservers()
        totalStepCountLiveData.notifyObservers()
        averageStepsPerMinuteLiveData.notifyObservers()

    }

    // Helper extension function to notify observers
    private fun <T> LiveData<T>.notifyObservers() {
        (this as? MutableLiveData<T>)?.value = this.value
    }

    // Reset all calculated values
    fun reset() {
        state.elapsedTime = 0f
        state.totalDistance = 0
        state.totalStepCount = 0
        state.averageStepsPerMinute = 0

        elapsedTimeLiveData.notifyObservers()
        totalDistanceLiveData.notifyObservers()
        totalStepCountLiveData.notifyObservers()
        averageStepsPerMinuteLiveData.notifyObservers()
    }
}
