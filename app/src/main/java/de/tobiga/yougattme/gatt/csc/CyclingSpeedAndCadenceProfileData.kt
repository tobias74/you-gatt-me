package de.tobiga.yougattme.gatt.csc

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import de.tobiga.yougattme.tools.CyclingMeasurementSimulator

class CyclingSpeedAndCadenceProfileData {

    // The simulator is the single source of truth
    private val simulator = CyclingMeasurementSimulator()

    // Only keep LiveData for user-set values that aren't derived from simulator
    private val _instantaneousSpeed = MutableLiveData<Float>(0.0f)
    val instantaneousSpeedLiveData: LiveData<Float> = _instantaneousSpeed

    private val _instantaneousCadence = MutableLiveData<Int>(0)
    val instantaneousCadenceLiveData: LiveData<Int> = _instantaneousCadence

    private val _wheelCircumference = MutableLiveData<Float>(210.0f) // Default in cm
    val wheelCircumferenceLiveData: LiveData<Float> = _wheelCircumference

    private val _totalDistance = MutableLiveData<Int>(0)
    val totalDistanceLiveData: LiveData<Int> = _totalDistance

    // Create LiveData that dynamically wraps simulator values for the UI
    val cumulativeWheelRevolutionsLiveData = object : LiveData<Int>() {
        override fun getValue(): Int = simulator.getCumulativeWheelRevolutions()
    }

    val cumulativeCrankRevolutionsLiveData = object : LiveData<Int>() {
        override fun getValue(): Int = simulator.getCumulativeCrankRevolutions()
    }

    val lastWheelEventTimeLiveData = object : LiveData<Long>() {
        override fun getValue(): Long = simulator.getLastWheelEventTimestampBle().toLong()
    }

    val lastCrankEventTimeLiveData = object : LiveData<Long>() {
        override fun getValue(): Long = simulator.getLastCrankEventTimestampBle().toLong()
    }

    init {
        // Apply wheel circumference to simulator
        simulator.setWheelCircumference(_wheelCircumference.value ?: 210.0f / 100.0f) // Convert cm to meters
    }

    // Speed is stored in the class, but updates simulator when changed
    var instantaneousSpeed: Float
        get() = _instantaneousSpeed.value ?: 0.0f
        set(value) {
            _instantaneousSpeed.value = value
        }

    fun postInstantaneousSpeed(value: Float) {
        _instantaneousSpeed.postValue(value)
    }

    var instantaneousCadence: Int
        get() = _instantaneousCadence.value ?: 0
        set(value) {
            _instantaneousCadence.value = value
        }

    fun postInstantaneousCadence(value: Int) {
        _instantaneousCadence.postValue(value)
    }

    var wheelCircumference: Float
        get() = _wheelCircumference.value ?: 210.0f
        set(value) {
            _wheelCircumference.value = value
            // Update the simulator when wheel circumference changes
            simulator.setWheelCircumference(value / 100.0f) // Convert cm to meters
        }

    fun postWheelCircumference(value: Float) {
        _wheelCircumference.postValue(value)
        // Update the simulator when wheel circumference changes
        simulator.setWheelCircumference(value / 100.0f) // Convert cm to meters
    }

    var totalDistance: Int
        get() = _totalDistance.value ?: 0
        set(value) { _totalDistance.value = value }

    fun postTotalDistance(value: Int) { _totalDistance.postValue(value) }

    // Direct getters for BLE characteristics to use
    val cumulativeWheelRevolutions: Int
        get() = simulator.getCumulativeWheelRevolutions()

    val cumulativeCrankRevolutions: Int
        get() = simulator.getCumulativeCrankRevolutions()

    val lastWheelEventTime: Long
        get() = simulator.getLastWheelEventTimestampBle().toLong()

    val lastCrankEventTime: Long
        get() = simulator.getLastCrankEventTimestampBle().toLong()

    fun advanceTime(timePassedMillis: Long, lastMovedTimestamp: Long) {
        val speedMps = instantaneousSpeed / 3.6

        simulator.simulateCycling(speedMps, instantaneousCadence.toDouble(), timePassedMillis, lastMovedTimestamp)

        // Let observers know data has changed - no value setting needed!
        cumulativeWheelRevolutionsLiveData.notifyObservers()
        cumulativeCrankRevolutionsLiveData.notifyObservers()
        lastWheelEventTimeLiveData.notifyObservers()
        lastCrankEventTimeLiveData.notifyObservers()

    }

    // Helper function to notify observers
    private fun <T> LiveData<T>.notifyObservers() {
        (this as? MutableLiveData<T>)?.value = this.value
    }

    // Reset the simulator
    fun reset() {
        simulator.reset()
        // Notify observers that values have reset
        cumulativeWheelRevolutionsLiveData.notifyObservers()
        cumulativeCrankRevolutionsLiveData.notifyObservers()
        lastWheelEventTimeLiveData.notifyObservers()
        lastCrankEventTimeLiveData.notifyObservers()
    }
}