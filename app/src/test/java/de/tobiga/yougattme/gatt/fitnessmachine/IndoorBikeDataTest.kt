package de.tobiga.yougattme.gatt.fitnessmachine

import org.junit.Assert.assertEquals
import org.junit.Test

class IndoorBikeDataTest {

    @Test
    fun advanceTime_doesNotAccumulateWhenWorkoutIsStopped() {
        val data = IndoorBikeData()
        data.instantaneousSpeed = 36f
        data.instantaneousPower = 200

        data.advanceTime(10_000, workoutRunning = false)

        assertEquals(0, data.getElapsedTimeInt())
        assertEquals(0, data.getTotalDistanceInt())
        assertEquals(0, data.getTotalEnergyKcal())
    }

    @Test
    fun advanceTime_accumulatesDistanceAveragesAndEnergyWhileRunning() {
        val data = IndoorBikeData()
        data.instantaneousSpeed = 36f
        data.instantaneousCadence = 90
        data.instantaneousPower = 200

        data.advanceTime(10_000, workoutRunning = true)

        assertEquals(10, data.getElapsedTimeInt())
        assertEquals(100, data.getTotalDistanceInt())
        assertEquals(3600, data.getAverageSpeedHundredthsKmh())
        assertEquals(180, data.getAverageCadenceHalfRpm())
        assertEquals(200, data.getAveragePowerRaw())
        assertEquals(0, data.getTotalEnergyKcal())
        assertEquals(172, data.getEnergyPerHourKcal())
    }

    @Test
    fun resetSession_clearsDerivedValuesButKeepsLiveInputs() {
        val data = IndoorBikeData()
        data.instantaneousSpeed = 30f
        data.instantaneousPower = 250
        data.advanceTime(5_000, workoutRunning = true)

        data.resetSession()

        assertEquals(30f, data.instantaneousSpeed, 0.001f)
        assertEquals(250, data.instantaneousPower)
        assertEquals(0, data.getElapsedTimeInt())
        assertEquals(0, data.getTotalDistanceInt())
        assertEquals(0, data.getAveragePowerRaw())
    }
}
