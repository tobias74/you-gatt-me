package de.tobiga.yougattme.gatt.fitnessmachine

import org.junit.Assert.assertEquals
import org.junit.Test

class RemainingFitnessMachineDataTest {

    @Test
    fun treadmill_accumulatesOnlyWhileWorkoutRuns() {
        val data = TreadmillData()
        data.instantaneousSpeed = 36f
        data.instantaneousPower = 200

        data.advanceTime(10_000, workoutRunning = false)

        assertEquals(0, data.getElapsedTimeSeconds())
        assertEquals(0, data.getTotalDistanceMeters())

        data.advanceTime(10_000, workoutRunning = true)

        assertEquals(10, data.getElapsedTimeSeconds())
        assertEquals(100, data.getTotalDistanceMeters())
        assertEquals(3600, data.getAverageSpeedHundredthsKmh())
        assertEquals(6, data.getInstantaneousPaceKmpmTenths())
        assertEquals(20, data.getForceOnBeltNewton())
    }

    @Test
    fun rower_calculatesStrokeCountDistanceAndPace() {
        val data = RowerData()
        data.strokeRate = 60
        data.instantaneousPower = 200

        data.advanceTime(10_000, workoutRunning = true)

        assertEquals(10, data.getElapsedTimeSeconds())
        assertEquals(10, data.getStrokeCountTotal())
        assertEquals(70, data.getTotalDistanceMeters())
        assertEquals(71, data.getInstantaneousPaceSeconds())
        assertEquals(200, data.getAveragePowerWatt())
    }

    @Test
    fun crossTrainer_calculatesDistanceAndStepRate() {
        val data = CrossTrainerData()
        data.instantaneousSpeed = 36f
        data.instantaneousStepsPerMinute = 120

        data.advanceTime(10_000, workoutRunning = true)

        assertEquals(10, data.getElapsedTimeSeconds())
        assertEquals(100, data.getTotalDistanceMeters())
        assertEquals(20, data.totalStepCount)
        assertEquals(120, data.getAverageStepRateSpm())
        assertEquals(3600, data.getAverageSpeedHundredthsKmh())
    }

    @Test
    fun stepClimber_calculatesMandatoryFieldsAndReset() {
        val data = StepClimberData()
        data.stepsPerMinute = 60

        data.advanceTime(60_000, workoutRunning = true)

        assertEquals(60, data.getElapsedTimeSeconds())
        assertEquals(60, data.stepCount)
        assertEquals(3, data.floors)

        data.resetSession()

        assertEquals(0, data.getElapsedTimeSeconds())
        assertEquals(0, data.stepCount)
        assertEquals(0, data.floors)
    }

    @Test
    fun stairClimber_doesNotAdvanceWhileStopped() {
        val data = StairClimberData()
        data.stepsPerMinute = 60

        data.advanceTime(32_000, workoutRunning = false)

        assertEquals(0, data.getElapsedTimeSeconds())
        assertEquals(0, data.floors)

        data.advanceTime(32_000, workoutRunning = true)

        assertEquals(32, data.getElapsedTimeSeconds())
        assertEquals(2, data.floors)
        assertEquals(16, data.strideCount)
    }
}
