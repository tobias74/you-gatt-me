package de.tobiga.yougattme.gatt.fitnessmachine

import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import de.tobiga.yougattme.core.BR
import kotlin.math.max

class StepClimberData : BaseObservable() {

    private var elapsedTimeSeconds = 0.0
    private var stepCountAccumulator = 0.0
    private var totalEnergyKcal = 0.0

    @get:Bindable
    val floors: Int
        get() = (stepCountAccumulator / STEPS_PER_FLOOR).toInt().ftmsUInt16()

    @get:Bindable
    val stepCount: Int
        get() = stepCountAccumulator.roundToFtmsUInt16()

    @get:Bindable
    var stepsPerMinute: Int = 80
        set(value) {
            field = value.coerceAtLeast(0)
            notifyPropertyChanged(BR.stepsPerMinute)
        }

    @get:Bindable
    val averageStepRate: Int
        get() = getAverageStepRateSpm()

    @get:Bindable
    val positiveElevationGain: Int
        get() = (floors * FLOOR_HEIGHT_METERS).roundToFtmsUInt16()

    @get:Bindable
    var heartRate: Int = 85
        set(value) {
            field = value
            notifyPropertyChanged(BR.heartRate)
        }

    @get:Bindable
    var metabolicEquivalent: Float = 8.0f
        set(value) {
            field = value.coerceAtLeast(0f)
            notifyPropertyChanged(BR.metabolicEquivalent)
        }

    @get:Bindable
    var targetDurationSeconds: Int = 30 * 60
        set(value) {
            field = value.coerceAtLeast(0)
            notifyPropertyChanged(BR.targetDurationSeconds)
            notifyPropertyChanged(BR.remainingTime)
        }

    @get:Bindable
    val elapsedTime: Int
        get() = elapsedTimeSeconds.roundToFtmsUInt16()

    @get:Bindable
    val remainingTime: Int
        get() = getRemainingTimeSeconds()

    @get:Bindable
    var energyPerMinute: Int = 6
        set(value) {
            field = value.coerceAtLeast(0)
            notifyPropertyChanged(BR.energyPerMinute)
            notifyPropertyChanged(BR.energyPerHour)
        }

    @get:Bindable
    val energyPerHour: Int
        get() = (energyPerMinute * 60).ftmsUInt16()

    @get:Bindable
    val totalEnergy: Int
        get() = totalEnergyKcal.roundToFtmsUInt16()

    @get:Bindable
    var includeStepsPerMinute: Boolean = true
        set(value) {
            field = value
            notifyPropertyChanged(BR.includeStepsPerMinute)
        }

    @get:Bindable
    var includeAverageStepRate: Boolean = false
        set(value) {
            field = value
            notifyPropertyChanged(BR.includeAverageStepRate)
        }

    @get:Bindable
    var includePositiveElevationGain: Boolean = true
        set(value) {
            field = value
            notifyPropertyChanged(BR.includePositiveElevationGain)
        }

    @get:Bindable
    var includeExpendedEnergy: Boolean = true
        set(value) {
            field = value
            notifyPropertyChanged(BR.includeExpendedEnergy)
        }

    @get:Bindable
    var includeHeartRate: Boolean = true
        set(value) {
            field = value
            notifyPropertyChanged(BR.includeHeartRate)
        }

    @get:Bindable
    var includeMetabolicEquivalent: Boolean = false
        set(value) {
            field = value
            notifyPropertyChanged(BR.includeMetabolicEquivalent)
        }

    @get:Bindable
    var includeElapsedTime: Boolean = true
        set(value) {
            field = value
            notifyPropertyChanged(BR.includeElapsedTime)
        }

    @get:Bindable
    var includeRemainingTime: Boolean = false
        set(value) {
            field = value
            notifyPropertyChanged(BR.includeRemainingTime)
        }

    fun advanceTime(timePassedMillis: Long, workoutRunning: Boolean) {
        if (!workoutRunning || timePassedMillis <= 0) {
            return
        }

        val secondsDelta = timePassedMillis / 1000.0
        elapsedTimeSeconds += secondsDelta
        stepCountAccumulator += stepsPerMinute * secondsDelta / 60.0
        totalEnergyKcal += max(0.0, energyPerMinute.toDouble()) * secondsDelta / 60.0
        notifyDerivedValuesChanged()
    }

    fun resetSession() {
        elapsedTimeSeconds = 0.0
        stepCountAccumulator = 0.0
        totalEnergyKcal = 0.0
        notifyDerivedValuesChanged()
    }

    fun getStepsPerMinuteSpm(): Int = stepsPerMinute.ftmsUInt16()

    fun getAverageStepRateSpm(): Int {
        if (elapsedTimeSeconds <= 0.0) {
            return 0
        }
        return (stepCountAccumulator * 60.0 / elapsedTimeSeconds).roundToFtmsUInt16()
    }

    fun getPositiveElevationGainMeters(): Int = positiveElevationGain
    fun getTotalEnergyKcal(): Int = totalEnergy
    fun getEnergyPerHourKcal(): Int = energyPerHour
    fun getEnergyPerMinuteKcal(): Int = energyPerMinute.ftmsUInt8()
    fun getHeartRateBpm(): Int = heartRate.ftmsUInt8()
    fun getMetabolicEquivalentTenths(): Int = (metabolicEquivalent * 10f).roundToFtmsUInt8()
    fun getElapsedTimeSeconds(): Int = elapsedTime
    fun getRemainingTimeSeconds(): Int = (targetDurationSeconds - elapsedTimeSeconds).roundToFtmsUInt16()

    fun getComputedSummary(): String {
        return "$floors floors | " +
            "$stepCount steps | " +
            "${getElapsedTimeSeconds()} s | " +
            "${getTotalEnergyKcal()} kcal"
    }

    private fun notifyDerivedValuesChanged() {
        notifyPropertyChanged(BR.elapsedTime)
        notifyPropertyChanged(BR.remainingTime)
        notifyPropertyChanged(BR.floors)
        notifyPropertyChanged(BR.stepCount)
        notifyPropertyChanged(BR.averageStepRate)
        notifyPropertyChanged(BR.positiveElevationGain)
        notifyPropertyChanged(BR.totalEnergy)
        notifyPropertyChanged(BR.energyPerHour)
    }

    companion object {
        private const val STEPS_PER_FLOOR = 20.0
        private const val FLOOR_HEIGHT_METERS = 3.0
    }
}
