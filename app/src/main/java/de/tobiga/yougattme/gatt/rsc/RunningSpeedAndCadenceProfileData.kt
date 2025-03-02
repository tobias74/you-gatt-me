package de.tobiga.yougattme.gatt.rsc

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class RunningSpeedAndCadenceProfileData {

    // Simple state holder class for our single source of truth
    private class RunningState {
        var totalDistance: Double = 0.0
    }

    // Single source of truth
    private val state = RunningState()

    // User-set values that aren't calculated
    private val _instantaneousSpeed = MutableLiveData<Double>(0.0)
    val instantaneousSpeedLiveData: LiveData<Double> = _instantaneousSpeed

    private val _instantaneousCadence = MutableLiveData<Int>(0)
    val instantaneousCadenceLiveData: LiveData<Int> = _instantaneousCadence

    private val _instantaneousStrideLength = MutableLiveData<Double>(0.0)
    val instantaneousStrideLengthLiveData: LiveData<Double> = _instantaneousStrideLength

    // Custom LiveData that reads directly from the state
    val totalDistanceLiveData = object : LiveData<Double>() {
        override fun getValue(): Double = state.totalDistance
    }

    // Getters and setters
    var instantaneousSpeed: Double
        get() = _instantaneousSpeed.value ?: 0.0
        set(value) { _instantaneousSpeed.value = value }

    fun postInstantaneousSpeed(value: Double) {
        _instantaneousSpeed.postValue(value)
    }

    var instantaneousCadence: Int
        get() = _instantaneousCadence.value ?: 0
        set(value) { _instantaneousCadence.value = value }

    fun postInstantaneousCadence(value: Int) {
        _instantaneousCadence.postValue(value)
    }

    var instantaneousStrideLength: Double
        get() = _instantaneousStrideLength.value ?: 0.0
        set(value) { _instantaneousStrideLength.value = value }

    fun postInstantaneousStrideLength(value: Double) {
        _instantaneousStrideLength.postValue(value)
    }

    // Total distance is now a direct getter to our state
    val totalDistance: Double
        get() = state.totalDistance

    // Direct setter if needed
    fun setTotalDistance(value: Double) {
        state.totalDistance = value
        totalDistanceLiveData.notifyObservers()
    }

    // For backward compatibility
    fun postTotalDistance(value: Double) {
        setTotalDistance(value)
    }

    fun advanceTime(milliseconds: Long) {
        val seconds = milliseconds / 1000.0
        val additionalDistance = instantaneousSpeed * seconds

        // Update state directly
        state.totalDistance += additionalDistance

        // Notify UI observers that the data has changed
        totalDistanceLiveData.notifyObservers()
    }

    // Helper extension function to notify observers
    private fun <T> LiveData<T>.notifyObservers() {
        (this as? MutableLiveData<T>)?.value = this.value
    }

    // Reset function
    fun reset() {
        state.totalDistance = 0.0
        totalDistanceLiveData.notifyObservers()
    }
}